package com.example.artifactshare.web;

import com.example.artifactshare.config.AgentProperties;
import com.example.artifactshare.domain.Project;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.repository.ProjectRepository;
import com.example.artifactshare.service.AgentService;
import com.example.artifactshare.service.BuildService;
import com.example.artifactshare.service.ProjectFileService;
import com.example.artifactshare.service.ProjectPublishService;
import com.example.artifactshare.util.ContentTypeResolver;
import com.example.artifactshare.web.dto.ArtifactView;
import com.example.artifactshare.web.dto.CreateProjectRequest;
import com.example.artifactshare.web.dto.FileEntry;
import com.example.artifactshare.web.dto.ModelsInfo;
import com.example.artifactshare.web.dto.ProjectView;
import com.example.artifactshare.web.dto.PublishProjectRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 网页生成工作台 API:
 * - POST   /api/v1/projects                      新建项目
 * - GET    /api/v1/projects                      项目列表
 * - GET    /api/v1/projects/{id}                 项目详情(文件/对话/发布状态)
 * - POST   /api/v1/projects/{id}/generate        LLM 生成/迭代(multipart: prompt, model?, images?)
 * - POST   /api/v1/projects/{id}/build           npm 构建(静态工程直接返回)
 * - GET    /api/v1/projects/{id}/files           文件清单
 * - GET    /api/v1/projects/{id}/files/{*path}   读文件(文本)
 * - PUT    /api/v1/projects/{id}/files/{*path}   写文件(body 为原始文本)
 * - DELETE /api/v1/projects/{id}/files/{*path}   删文件
 * - GET    /api/v1/projects/{id}/preview/{*path} 预览产物(NPM 工程 = dist,静态 = 工作区)
 * - POST   /api/v1/projects/{id}/publish         发布到 slug 域名系统(OSS + CDN)
 * - GET    /api/v1/models                        模型选择器数据
 */
@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private final AgentService agentService;
    private final ProjectFileService fileService;
    private final BuildService buildService;
    private final ProjectPublishService publishService;
    private final ContentTypeResolver contentTypeResolver;
    private final AgentProperties props;
    private final ProjectRepository projectRepository;

    public ProjectController(AgentService agentService, ProjectFileService fileService,
                             BuildService buildService, ProjectPublishService publishService,
                             ContentTypeResolver contentTypeResolver, AgentProperties props,
                             ProjectRepository projectRepository) {
        this.agentService = agentService;
        this.fileService = fileService;
        this.buildService = buildService;
        this.publishService = publishService;
        this.contentTypeResolver = contentTypeResolver;
        this.props = props;
        this.projectRepository = projectRepository;
    }

    @GetMapping("/models")
    public ModelsInfo models() {
        return new ModelsInfo(props.isEnabled(), props.getModels(), props.getDefaultModel());
    }

    @PostMapping("/projects")
    public ProjectView create(@RequestBody CreateProjectRequest request) {
        return view(agentService.create(request.name()));
    }

    @GetMapping("/projects")
    public List<ProjectView> list() {
        return agentService.list().stream().map(this::view).toList();
    }

    @GetMapping("/projects/{id}")
    public ProjectView get(@PathVariable String id) {
        return view(agentService.require(id));
    }

    @PostMapping(value = "/projects/{id}/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AgentService.GenerateResult generate(@PathVariable String id,
                                                @RequestParam("prompt") String prompt,
                                                @RequestParam(value = "model", required = false) String model,
                                                @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        return agentService.generate(id, prompt, model, images);
    }

    @PostMapping("/projects/{id}/build")
    public BuildService.BuildResult build(@PathVariable String id) {
        Project project = agentService.require(id);
        BuildService.BuildResult result = buildService.build(project);
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return result;
    }

    @GetMapping("/projects/{id}/files")
    public List<FileEntry> files(@PathVariable String id) {
        agentService.require(id);
        return fileService.listFileEntries(id);
    }

    @GetMapping("/projects/{id}/files/{*path}")
    public ResponseEntity<String> readFile(@PathVariable String id, @PathVariable String path) {
        agentService.require(id);
        String content = fileService.readFile(id, strip(path));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/plain; charset=utf-8"))
                .body(content);
    }

    @PutMapping("/projects/{id}/files/{*path}")
    public ResponseEntity<Void> writeFile(@PathVariable String id, @PathVariable String path,
                                          @RequestBody String body) {
        agentService.require(id);
        fileService.writeFile(id, strip(path), body);
        touch(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/projects/{id}/files/{*path}")
    public ResponseEntity<Void> deleteFile(@PathVariable String id, @PathVariable String path) {
        agentService.require(id);
        fileService.deleteFile(id, strip(path));
        touch(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{id}/preview/{*path}")
    public ResponseEntity<byte[]> preview(@PathVariable String id, @PathVariable String path) {
        Project project = agentService.require(id);
        Path root = buildService.outputRoot(project);
        String rel = strip(path);
        byte[] content;
        try {
            content = fileService.readFromRoot(root, rel.isEmpty() ? "index.html" : rel);
        } catch (ApiException e) {
            // SPA 路由回退到 index.html
            if (e.getStatus() == HttpStatus.NOT_FOUND && !rel.contains(".")) {
                content = fileService.readFromRoot(root, "index.html");
            } else {
                throw e;
            }
        }
        String contentType = contentTypeResolver.resolve(rel.isEmpty() ? "index.html" : rel);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }

    @PostMapping("/projects/{id}/publish")
    public ArtifactView publish(@PathVariable String id,
                                @RequestBody(required = false) PublishProjectRequest request) {
        return publishService.publish(id,
                request == null ? null : request.slug(),
                request == null ? null : request.title());
    }

    private ProjectView view(Project project) {
        return new ProjectView(project.getId(), project.getName(), project.getModel(),
                project.getType(), project.getStatus(), fileService.listFileEntries(project.getId()),
                agentService.readChat(project), project.getPublishedSlug(), project.getPublishedUrl(),
                project.getCreatedAt(), project.getUpdatedAt());
    }

    private void touch(String id) {
        Project project = agentService.require(id);
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
    }

    private String strip(String path) {
        String rel = path.startsWith("/") ? path.substring(1) : path;
        return rel.endsWith("/") ? rel.substring(0, rel.length() - 1) : rel;
    }
}
