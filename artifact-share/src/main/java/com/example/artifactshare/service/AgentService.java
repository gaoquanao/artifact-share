package com.example.artifactshare.service;

import com.example.artifactshare.config.AgentProperties;
import com.example.artifactshare.domain.Project;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.repository.ProjectRepository;
import com.example.artifactshare.service.model.UploadFile;
import com.example.artifactshare.util.LlmJsonExtractor;
import com.example.artifactshare.util.UploadPathValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
/**
 * 网页生成 Agent:把用户需求(文本+图片)+ 当前工程上下文发给大模型,
 * 解析其输出的 {summary, files} JSON,全量写入工作区;对话历史随项目持久化,支持多轮迭代。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String SYSTEM_PROMPT = """
            你是资深前端工程师,在"网页生成工作台"中根据用户需求生成并迭代一个可直接运行的网页工程。

            输出要求(必须严格遵守):
            1. 只输出一个 JSON 对象,不要输出 markdown 代码块或任何其他文字:
               {"summary": "给用户的中文实现说明(简短)", "files": {"相对路径": "完整文件内容"}}
            2. 默认生成纯静态工程:入口必须是 index.html,CSS/JS 用相对路径引用,不要创建 package.json。
               仅当用户明确要求框架或 npm 依赖时才生成 Vite 工程:package.json 的 build 脚本必须是 "vite build",
               依赖保持最少必要,构建产物输出到 dist/。
            3. 图片资源:引用用户上传的 assets/ 下文件(相对路径),或使用内联 SVG / 纯 CSS 图形,禁止引用外链图片。
            4. 每次输出需要变更文件的完整内容(全量覆盖),不要输出 diff 或省略号;未提及的文件保持不变。
            5. 所有文件 UTF-8;页面需在桌面与移动端浏览器可用,自包含样式。
            """;
    private static final int HISTORY_KEEP = 8;
    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();
    private final ProjectRepository repository;
    private final ProjectFileService fileService;
    private final LLMClient llmClient;
    private final AgentProperties props;
    private final ObjectMapper objectMapper;

    public AgentService(ProjectRepository repository, ProjectFileService fileService,
                        LLMClient llmClient, AgentProperties props, ObjectMapper objectMapper) {
        this.repository = repository;
        this.fileService = fileService;
        this.llmClient = llmClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public Project create(String name) {
        Project p = new Project();
        p.setId(newId());
        p.setName(name == null || name.isBlank() ? "未命名项目" : name.trim());
        p.setType(Project.TYPE_STATIC);
        p.setStatus(Project.STATUS_CREATED);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        repository.save(p);
        fileService.workspace(p.getId());
        return p;
    }

    public Project require(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在: " + id));
    }

    public List<Project> list() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    /**
     * 生成/迭代:图片先落入工作区 assets/,再连同工程快照一起作为上下文发给模型。
     */
    public GenerateResult generate(String projectId, String prompt, String model, List<MultipartFile> images) {
        if (prompt == null || prompt.isBlank()) {
            throw ApiException.badRequest("生成需求(prompt)不能为空");
        }
        Project project = require(projectId);
        String useModel = model != null && !model.isBlank() ? model
                : project.getModel() != null ? project.getModel() : props.getDefaultModel();
        project.setModel(useModel);

        List<String> assetPaths = saveInputImages(project.getId(), images);

        String snapshot = fileService.renderSnapshot(projectId);
        String userText = "以下是当前工程文件(为空表示全新项目):\n<current_project_files>\n"
                + snapshot + "\n</current_project_files>\n\n用户需求:\n" + prompt.trim();
        if (!assetPaths.isEmpty()) {
            userText += "\n\n用户本次上传的图片已保存到工程中,可在代码里用相对路径引用:\n"
                    + String.join("\n", assetPaths);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (Map<String, String> history : readChat(project)) {
            messages.add(Map.of("role", history.get("role"), "content", history.get("content")));
        }
        if (assetPaths.isEmpty()) {
            messages.add(Map.of("role", "user", "content", userText));
        } else {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", userText));
            for (String assetPath : assetPaths) {
                parts.add(imagePart(project.getId(), assetPath));
            }
            messages.add(Map.of("role", "user", "content", parts));
        }

        String raw = llmClient.chat(useModel, messages);
        ParsedGeneration parsed = parseGeneration(raw);
        for (Map.Entry<String, String> entry : parsed.files().entrySet()) {
            fileService.writeFile(project.getId(), entry.getKey(), entry.getValue());
        }

        project.setType(projectType(project.getId()));
        project.setStatus(Project.STATUS_GENERATED);
        appendChat(project, prompt.trim(), parsed.summary());
        repository.save(project);
        log.info("项目 {} 生成完成: files={} model={}", projectId, parsed.files().size(), useModel);
        return new GenerateResult(parsed.summary(), List.copyOf(parsed.files().keySet()), parsed.warnings(),
                readChat(project));
    }


    private List<String> saveInputImages(String projectId, List<MultipartFile> images) {
        List<String> paths = new ArrayList<>();
        if (images == null) {
            return paths;
        }
        int index = 1;
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }
            String name = image.getOriginalFilename() == null ? "image.png" : image.getOriginalFilename();
            String ext = name.toLowerCase(Locale.ROOT);
            String suffix = ext.endsWith(".png") ? "png"
                    : ext.endsWith(".jpg") || ext.endsWith(".jpeg") ? "jpg"
                    : ext.endsWith(".gif") ? "gif"
                    : ext.endsWith(".webp") ? "webp" : "png";
            String path = "assets/llm-input-" + (index++) + "." + suffix;
            try {
                fileService.writeFileBytes(projectId, path, image.getBytes());
            } catch (IOException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存上传图片失败");
            }
            paths.add(path);
        }
        return paths;
    }

    private Map<String, Object> imagePart(String projectId, String assetPath) {
        byte[] bytes = java.util.Base64.getEncoder()
                .encode(readBytes(projectId, assetPath));
        String mime = assetPath.endsWith(".jpg") ? "image/jpeg" : "image/" + assetPath.substring(assetPath.lastIndexOf('.') + 1);
        return Map.of("type", "image_url",
                "image_url", Map.of("url", "data:" + mime + ";base64," + new String(bytes, java.nio.charset.StandardCharsets.US_ASCII)));
    }

    private byte[] readBytes(String projectId, String path) {
        try {
            return java.nio.file.Files.readAllBytes(fileService.workspace(projectId).resolve(path).normalize());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取图片失败: " + path);
        }
    }

    private ParsedGeneration parseGeneration(String raw) {
        JsonNode node;
        try {
            node = objectMapper.readTree(LlmJsonExtractor.extractJsonObject(raw));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "模型输出 JSON 解析失败: " + e.getMessage());
        }
        String summary = node.path("summary").asText("已生成工程文件");
        Map<String, String> files = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        JsonNode filesNode = node.path("files");
        if (!filesNode.isObject() || filesNode.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "模型输出缺少 files 字段");
        }
        filesNode.fields().forEachRemaining(entry -> {
            String path = entry.getKey();
            String content = entry.getValue().asText("");
            if (content.isBlank()) {
                return;
            }
            try {
                files.put(UploadPathValidator.normalize(path), content);
            } catch (ApiException e) {
                warnings.add("跳过非法路径: " + path);
            }
        });
        if (files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "模型未输出任何有效文件");
        }
        return new ParsedGeneration(summary, files, warnings);
    }

    private String projectType(String projectId) {
        return java.nio.file.Files.exists(fileService.workspace(projectId).resolve("package.json"))
                ? Project.TYPE_NPM : Project.TYPE_STATIC;
    }

    public List<Map<String, String>> readChat(Project project) {
        if (project.getChatJson() == null || project.getChatJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(project.getChatJson(), new TypeReference<List<Map<String, String>>>() {
            });
        } catch (IOException e) {
            return List.of();
        }
    }

    private void appendChat(Project project, String userMessage, String assistantMessage) {
        List<Map<String, String>> chat = new ArrayList<>(readChat(project));
        chat.add(Map.of("role", "user", "content", userMessage));
        chat.add(Map.of("role", "assistant", "content", assistantMessage));
        while (chat.size() > HISTORY_KEEP) {
            chat.remove(0);
        }
        try {
            project.setChatJson(objectMapper.writeValueAsString(chat));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化对话历史失败");
        }
    }

    public String newId() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private record ParsedGeneration(String summary, Map<String, String> files, List<String> warnings) {
    }

    public record GenerateResult(String summary, List<String> files, List<String> warnings,
                                 List<Map<String, String>> chat) {
    }
}
