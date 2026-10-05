package com.example.artifactshare.service;

import com.example.artifactshare.config.ShareProperties;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.repository.ArtifactRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * slug 即子域名前缀:{slug}.s.example.com。
 *
 * 默认 slug 由产物主文件名 + 时间戳派生:可读前缀(文件名清洗,20 字符内)
 * + "文件名|时间戳|随机熵" 的 SHA-256 短哈希,形如 weekly-report-k3x9m2q7;
 * 文件名无可读 ASCII 字符(如纯中文)时退化为纯哈希。时间戳保证同名文件多次发布得到不同链接。
 *
 * 自定义 slug 仅允许小写字母、数字、中划线(2~40 位)+ 保留字黑名单;
 * 已存在的自定义 slug 允许覆盖重新发布;发布后可用改名接口调整。
 */
@Service
public class SlugGenerator {

    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final int HASH_LENGTH = 8;
    private static final int MAX_PREFIX_LENGTH = 20;
    private static final Pattern CUSTOM_SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{0,38}[a-z0-9]$");
    /** 通用入口文件名:取前缀时回退到其父目录名,避免所有 zip 都是 index-xxx */
    private static final Set<String> GENERIC_NAMES = Set.of("index", "readme");

    private final SecureRandom random = new SecureRandom();
    private final ArtifactRepository repository;
    private final ShareProperties props;

    public SlugGenerator(ArtifactRepository repository, ShareProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** 发布入口:未指定 slug 时按产物主文件派生,指定时走自定义校验 */
    public String resolveSlug(String requested, String sourcePath) {
        return requested == null || requested.isBlank()
                ? generateDefault(sourcePath)
                : normalizeCustom(requested);
    }

    /** 自定义 slug:校验格式与保留字(同 slug 重复发布视为覆盖更新) */
    public String normalizeCustom(String requested) {
        if (requested == null || requested.isBlank()) {
            throw ApiException.badRequest("slug 不能为空");
        }
        String slug = requested.trim().toLowerCase(Locale.ROOT);
        if (!CUSTOM_SLUG.matcher(slug).matches() || props.getPublish().getReservedSlugs().contains(slug)) {
            throw ApiException.badRequest(
                    "slug 仅允许 2~40 位小写字母/数字/中划线,且不能使用保留字: " + requested);
        }
        return slug;
    }

    /** 默认 slug:可读前缀(可空)+ 短哈希,查库去重,冲突时换熵重试 */
    public String generateDefault(String sourcePath) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String slug = candidate(sourcePath, attempt);
            if (!repository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new IllegalStateException("默认 slug 生成冲突次数过多,请重试");
    }

    private String candidate(String sourcePath, int attempt) {
        String seed = (sourcePath == null ? "" : sourcePath) + "|" + System.currentTimeMillis()
                + "|" + random.nextLong() + "|" + attempt;
        String hash = shortHash(seed);
        String prefix = readablePrefix(sourcePath);
        return prefix.isEmpty() ? hash : prefix + "-" + hash;
    }

    private String readablePrefix(String sourcePath) {
        String base = baseNameWithoutExt(sourcePath);
        if (GENERIC_NAMES.contains(base)) {
            String parent = parentName(sourcePath);
            if (!parent.isEmpty() && !GENERIC_NAMES.contains(parent)) {
                base = parent;
            }
        }
        String prefix = base.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (prefix.length() > MAX_PREFIX_LENGTH) {
            prefix = prefix.substring(0, MAX_PREFIX_LENGTH).replaceAll("-+$", "");
        }
        return prefix;
    }

    private String baseNameWithoutExt(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String name = path.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        name = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private String parentName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path.trim().replace('\\', '/');
        int end = p.endsWith("/") ? p.length() - 1 : p.length();
        int slash = p.lastIndexOf('/', Math.max(end - 1, 0));
        return slash > 0 ? baseNameWithoutExt(p.substring(0, slash)) : "";
    }

    /** SHA-256 前 8 字节 → 去易混淆字符的 base36,固定 8 位 */
    private String shortHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (bytes[i] & 0xFFL);
            }
            value &= Long.MAX_VALUE;
            StringBuilder sb = new StringBuilder(HASH_LENGTH);
            for (int i = 0; i < HASH_LENGTH; i++) {
                sb.append(ALPHABET.charAt((int) (value % ALPHABET.length())));
                value /= ALPHABET.length();
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
