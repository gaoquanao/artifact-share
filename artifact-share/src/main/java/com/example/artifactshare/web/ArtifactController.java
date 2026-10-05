package com.example.artifactshare.web;

import com.example.artifactshare.domain.Artifact;
import com.example.artifactshare.service.ArtifactPublishService;
import com.example.artifactshare.service.UploadExtractor;
import com.example.artifactshare.web.dto.ArtifactView;
import com.example.artifactshare.web.dto.RenameRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 发布 API:
 * - POST  /api/v1/artifacts   multipart: files(可多个,或一个 zip),可选 slug/title
 * - GET   /api/v1/artifacts   已发布列表
 * - GET   /api/v1/artifacts/{slug}
 * - PATCH /api/v1/artifacts/{slug}   修改分享域名(JSON body: {"slug": "new-slug"})
 * - DELETE /api/v1/artifacts/{slug}  下线并删除 OSS 对象
 */
@RestController
@RequestMapping("/api/v1/artifacts")
public class ArtifactController {

    private final ArtifactPublishService publishService;
    private final UploadExtractor extractor;
    private final ObjectMapper objectMapper;

    public ArtifactController(ArtifactPublishService publishService,
                              UploadExtractor extractor,
                              ObjectMapper objectMapper) {
        this.publishService = publishService;
        this.extractor = extractor;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactView publish(@RequestParam("files") List<MultipartFile> files,
                                @RequestParam(value = "slug", required = false) String slug,
                                @RequestParam(value = "title", required = false) String title) {
        Artifact artifact = publishService.publish(extractor.extract(files), slug, title);
        return ArtifactView.from(artifact, objectMapper);
    }

    @GetMapping
    public List<ArtifactView> list() {
        return publishService.listPublished().stream()
                .map(a -> ArtifactView.from(a, objectMapper))
                .toList();
    }

    @GetMapping("/{slug}")
    public ArtifactView get(@PathVariable String slug) {
        return ArtifactView.from(publishService.getBySlug(slug), objectMapper);
    }

    @PatchMapping("/{slug}")
    public ArtifactView rename(@PathVariable String slug, @RequestBody RenameRequest request) {
        return ArtifactView.from(publishService.rename(slug, request.slug()), objectMapper);
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String slug) {
        publishService.delete(slug);
    }
}
