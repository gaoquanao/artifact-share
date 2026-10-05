package com.example.artifactshare.service;

import com.example.artifactshare.domain.Artifact;
import com.example.artifactshare.domain.Project;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.repository.ProjectRepository;
import com.example.artifactshare.service.model.UploadFile;
import com.example.artifactshare.web.dto.ArtifactView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 工程发布:从产物根目录(NPM 工程为 dist,静态工程为工作区)收集文件,
 * 复用 ArtifactPublishService 的发布链路(slug 域名系统、OSS、CDN 预热)。
 * 默认 slug 以项目名取名,发布时可指定自定义 slug。
 */
@Service
public class ProjectPublishService {

    private static final Logger log = LoggerFactory.getLogger(ProjectPublishService.class);

    private final AgentService agentService;
    private final BuildService buildService;
    private final ProjectFileService fileService;
    private final ArtifactPublishService publishService;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public ProjectPublishService(AgentService agentService, BuildService buildService,
                                 ProjectFileService fileService, ArtifactPublishService publishService,
                                 ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.buildService = buildService;
        this.fileService = fileService;
        this.publishService = publishService;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    public ArtifactView publish(String projectId, String slug, String title) {
        Project project = agentService.require(projectId);
        Path root = buildService.outputRoot(project);
        if (!Files.exists(root.resolve("index.html"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "产物缺少 index.html,无法发布");
        }
        List<UploadFile> files = fileService.collect(projectId, root);
        Artifact artifact = publishService.publish(files, slug,
                title == null || title.isBlank() ? project.getName() : title, project.getName());
        project.setPublishedSlug(artifact.getSlug());
        project.setPublishedUrl(artifact.getPublicUrl());
        project.setUpdatedAt(java.time.Instant.now());
        projectRepository.save(project);
        log.info("项目 {} 已发布: {}", projectId, artifact.getPublicUrl());
        return ArtifactView.from(artifact, objectMapper);
    }
}
