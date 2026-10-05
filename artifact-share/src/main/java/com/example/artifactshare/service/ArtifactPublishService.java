package com.example.artifactshare.service;

import com.example.artifactshare.config.ShareProperties;
import com.example.artifactshare.domain.Artifact;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.repository.ArtifactRepository;
import com.example.artifactshare.service.model.FileMeta;
import com.example.artifactshare.service.model.UploadFile;
import com.example.artifactshare.util.ContentTypeResolver;
import com.example.artifactshare.util.UploadPathValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 发布核心流程:
 * 1. slug 解析(随机或自定义,已存在则覆盖重新发布)
 * 2. 路径清洗防穿越;zip 常见"单顶层目录"自动剥离
 * 3. .md/.markdown 渲染成 .html(index.md / README.md → index.html)
 * 4. 入口检查:必须有 index.html;单文件 html/md 自动作为首页
 * 5. 按对象逐个上传 OSS(设置 Content-Type 与 Cache-Control),落库,异步预热 CDN
 */
@Service
public class ArtifactPublishService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactPublishService.class);

    private final ArtifactRepository repository;
    private final OssStorageService storage;
    private final SlugGenerator slugGenerator;
    private final MarkdownRenderer markdownRenderer;
    private final ContentTypeResolver contentTypeResolver;
    private final CdnRefreshService cdnRefreshService;
    private final ShareProperties props;
    private final ObjectMapper objectMapper;

    public ArtifactPublishService(ArtifactRepository repository,
                                  OssStorageService storage,
                                  SlugGenerator slugGenerator,
                                  MarkdownRenderer markdownRenderer,
                                  ContentTypeResolver contentTypeResolver,
                                  CdnRefreshService cdnRefreshService,
                                  ShareProperties props,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.storage = storage;
        this.slugGenerator = slugGenerator;
        this.markdownRenderer = markdownRenderer;
        this.contentTypeResolver = contentTypeResolver;
        this.cdnRefreshService = cdnRefreshService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public Artifact publish(List<UploadFile> uploads, String requestedSlug, String title) {
        return publish(uploads, requestedSlug, title, null);
    }

    /**
     * @param defaultSlugSource 未指定 slug 时用于生成默认 slug 的名字来源
     *                          (工作台发布项目时传项目名;直传产物时为 null,取主文件名)
     */
    public Artifact publish(List<UploadFile> uploads, String requestedSlug, String title, String defaultSlugSource) {
        ShareProperties.Publish limits = props.getPublish();
        if (uploads.size() > limits.getMaxFiles()) {
            throw ApiException.badRequest("文件数量超过上限 " + limits.getMaxFiles());
        }

        String source = defaultSlugSource == null || defaultSlugSource.isBlank()
                ? primarySourceName(uploads)
                : defaultSlugSource;
        String slug = slugGenerator.resolveSlug(requestedSlug, source);
        List<UploadFile> files = ensureEntry(stripCommonRoot(renderMarkdown(uploads)));
        long totalBytes = files.stream().mapToLong(f -> f.content().length).sum();
        if (totalBytes > limits.getMaxTotalBytes()) {
            throw ApiException.badRequest("产物总大小超过上限 " + limits.getMaxTotalBytes() + " 字节");
        }

        Instant now = Instant.now();
        Artifact artifact = repository.findBySlug(slug).orElseGet(() -> {
            Artifact a = new Artifact();
            a.setSlug(slug);
            a.setPublishedAt(now);
            return a;
        });
        artifact.setTitle(title == null || title.isBlank() ? null : title.trim());
        artifact.setStatus(Artifact.STATUS_PUBLISHED);
        artifact.setPublishedAt(now);
        artifact.setEntryObjectKey(storage.slugPrefix(slug) + "/index.html");
        artifact.setPublicUrl(publicUrl(slug));
        artifact.setSizeBytes(totalBytes);
        artifact.setFileCount(files.size());

        List<FileMeta> metas = new ArrayList<>(files.size());
        if (props.getOss().getAccessKeyId().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OSS 未配置:请设置环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET");
        }
        for (UploadFile file : files) {
            String objectKey = storage.slugPrefix(slug) + "/" + file.path();
            String contentType = contentTypeResolver.resolve(file.path());
            String cacheControl = contentType.startsWith("text/html")
                    ? props.getOss().getHtmlCacheControl()
                    : props.getOss().getAssetCacheControl();
            storage.put(objectKey, file.content(), contentType, cacheControl);
            metas.add(new FileMeta(file.path(), contentType, file.content().length));
        }
        try {
            artifact.setFilesJson(objectMapper.writeValueAsString(metas));
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化文件清单失败");
        }
        repository.save(artifact);

        cdnRefreshService.warmup(artifact.getPublicUrl());
        log.info("产物已发布 slug={} files={} bytes={}", slug, files.size(), totalBytes);
        return artifact;
    }

    public Artifact getBySlug(String slug) {
        return repository.findBySlugAndStatus(slug, Artifact.STATUS_PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("产物不存在或已下线: " + slug));
    }

    /**
     * 修改分享域名:OSS 对象整体迁移到新前缀,更新记录,
     * 刷新旧地址 CDN 缓存、预热新地址。目标 slug 已被任何记录占用时返回 409。
     */
    public Artifact rename(String currentSlug, String requestedSlug) {
        Artifact artifact = getBySlug(currentSlug);
        String newSlug = slugGenerator.normalizeCustom(requestedSlug);
        if (newSlug.equals(currentSlug)) {
            return artifact;
        }
        if (repository.findBySlug(newSlug).isPresent()) {
            throw ApiException.conflict("目标 slug 已被占用: " + newSlug);
        }
        String oldPublicUrl = artifact.getPublicUrl();
        storage.moveToPrefix(currentSlug, newSlug);
        artifact.setSlug(newSlug);
        artifact.setEntryObjectKey(storage.slugPrefix(newSlug) + "/index.html");
        artifact.setPublicUrl(publicUrl(newSlug));
        repository.save(artifact);
        cdnRefreshService.purge(oldPublicUrl);
        cdnRefreshService.warmup(artifact.getPublicUrl());
        log.info("产物域名已变更 {} -> {}", currentSlug, newSlug);
        return artifact;
    }

    public List<Artifact> listPublished() {
        return repository.findByStatusOrderByPublishedAtDesc(Artifact.STATUS_PUBLISHED);
    }

    public void delete(String slug) {
        Artifact artifact = getBySlug(slug);
        storage.deleteBySlug(slug);
        artifact.setStatus(Artifact.STATUS_DELETED);
        repository.save(artifact);
        cdnRefreshService.purge(artifact.getPublicUrl());
        log.info("产物已下线 slug={}", slug);
    }

    /** 默认 slug 的取名来源:优先入口文件(index/README),其次第一个 html/md,再退首个文件 */
    private String primarySourceName(List<UploadFile> uploads) {
        List<String> paths = uploads.stream().map(UploadFile::path).toList();
        return paths.stream()
                .filter(p -> p.equalsIgnoreCase("index.html") || p.equalsIgnoreCase("index.md")
                        || p.equalsIgnoreCase("readme.md") || p.equalsIgnoreCase("index.markdown")
                        || p.equalsIgnoreCase("readme.markdown"))
                .findFirst()
                .or(() -> paths.stream().filter(p -> p.toLowerCase(Locale.ROOT).endsWith(".html")).findFirst())
                .or(() -> paths.stream().filter(p -> p.toLowerCase(Locale.ROOT).endsWith(".md")).findFirst())
                .orElse(paths.get(0));
    }

    private String publicUrl(String slug) {
        return props.getScheme() + "://" + slug + "." + props.getDomain() + "/";
    }

    private List<UploadFile> renderMarkdown(List<UploadFile> uploads) {
        List<UploadFile> out = new ArrayList<>(uploads.size());
        for (UploadFile upload : uploads) {
            String path = UploadPathValidator.normalize(upload.path());
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
                String htmlPath = lower.equals("index.md") || lower.equals("readme.md")
                        || lower.equals("index.markdown") || lower.equals("readme.markdown")
                        ? "index.html"
                        : stripExt(path) + ".html";
                String html = markdownRenderer.render(new String(upload.content(), StandardCharsets.UTF_8));
                out.add(new UploadFile(htmlPath, html.getBytes(StandardCharsets.UTF_8)));
            } else {
                out.add(new UploadFile(path, upload.content()));
            }
        }
        return out;
    }

    /** zip 内所有文件都在同一个顶层目录(如 dist/)时,自动去掉该前缀 */
    private List<UploadFile> stripCommonRoot(List<UploadFile> files) {
        Set<String> roots = files.stream()
                .map(UploadFile::path)
                .map(p -> p.contains("/") ? p.substring(0, p.indexOf('/')) : null)
                .collect(java.util.stream.Collectors.toSet());
        if (roots.size() == 1 && !roots.contains(null)) {
            String root = roots.iterator().next();
            return files.stream()
                    .map(f -> new UploadFile(f.path().substring(root.length() + 1), f.content()))
                    .toList();
        }
        return files;
    }

    private List<UploadFile> ensureEntry(List<UploadFile> files) {
        boolean hasIndex = files.stream().anyMatch(f -> f.path().equals("index.html"));
        if (hasIndex) {
            return files;
        }
        if (files.size() == 1 && contentTypeResolver.isHtmlLike(files.get(0).path())) {
            UploadFile only = files.get(0);
            return List.of(new UploadFile("index.html", only.content()));
        }
        throw ApiException.badRequest("产物必须包含 index.html(或 index.md / README.md);单个 html/md 文件会自动作为首页");
    }

    private String stripExt(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(0, dot) : path;
    }
}
