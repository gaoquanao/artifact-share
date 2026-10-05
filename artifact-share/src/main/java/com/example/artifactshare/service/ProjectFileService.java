package com.example.artifactshare.service;

import com.example.artifactshare.config.AgentProperties;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.util.UploadPathValidator;
import com.example.artifactshare.web.dto.FileEntry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * 项目工作区文件读写:list/read/write/delete + 快照(给 LLM 的当前工程上下文)。
 * 所有路径经 UploadPathValidator 清洗并限制在工作区目录内。
 */
@Service
public class ProjectFileService {

    /** 给 LLM 的工程快照大小上限,超出部分只列文件名 */
    private static final int SNAPSHOT_BUDGET_BYTES = 120_000;

    private final AgentProperties props;

    public ProjectFileService(AgentProperties props) {
        this.props = props;
    }

    public Path workspace(String projectId) {
        Path ws = Paths.get(props.getWorkspace().getRoot(), projectId).normalize();
        try {
            Files.createDirectories(ws);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "创建工作区失败: " + e.getMessage());
        }
        return ws;
    }

    public List<String> listFiles(String projectId) {
        Path ws = workspace(projectId);
        try (Stream<Path> stream = Files.walk(ws)) {
            return stream.filter(Files::isRegularFile)
                    .map(ws::relativize)
                    .map(p -> p.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取文件列表失败: " + e.getMessage());
        }
    }

    public List<FileEntry> listFileEntries(String projectId) {
        Path ws = workspace(projectId);
        try (Stream<Path> stream = Files.walk(ws)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> new FileEntry(
                            ws.relativize(p).toString().replace('\\', '/'),
                            sizeOf(p)))
                    .sorted(java.util.Comparator.comparing(FileEntry::path))
                    .toList();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取文件列表失败: " + e.getMessage());
        }
    }

    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    /** 从指定产物根目录(工作区或 dist)读文件,路径限制在 root 内 */
    public byte[] readFromRoot(Path root, String rawPath) {
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(UploadPathValidator.normalize(rawPath)).normalize();
        if (!target.startsWith(base)) {
            throw ApiException.badRequest("非法文件路径: " + rawPath);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw ApiException.notFound("文件不存在: " + rawPath);
        }
    }

    public String readFile(String projectId, String rawPath) {
        try {
            return Files.readString(safeResolve(projectId, rawPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.notFound("文件不存在或不可读: " + rawPath);
        }
    }

    public void writeFile(String projectId, String rawPath, String content) {
        Path target = safeResolve(projectId, rawPath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "写入文件失败: " + e.getMessage());
        }
    }

    public void writeFileBytes(String projectId, String rawPath, byte[] content) {
        Path target = safeResolve(projectId, rawPath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "写入文件失败: " + e.getMessage());
        }
    }

    public void deleteFile(String projectId, String rawPath) {
        Path target = safeResolve(projectId, rawPath);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "删除文件失败: " + e.getMessage());
        }
    }

    /** 打包整个目录(发布时使用),跳过 node_modules/.git */
    public List<com.example.artifactshare.service.model.UploadFile> collect(String projectId, Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("node_modules") && !p.toString().contains(".git"))
                    .map(p -> {
                        try {
                            String relative = root.relativize(p).toString().replace('\\', '/');
                            return new com.example.artifactshare.service.model.UploadFile(relative, Files.readAllBytes(p));
                        } catch (IOException e) {
                            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取文件失败: " + p);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "收集产物文件失败: " + e.getMessage());
        }
    }

    /** 供 LLM 的当前工程上下文:全部文件内容,超预算的只列路径 */
    public String renderSnapshot(String projectId) {
        StringBuilder sb = new StringBuilder();
        int budget = SNAPSHOT_BUDGET_BYTES;
        for (String path : listFiles(projectId)) {
            String content = readFile(projectId, path);
            if (content.length() > budget) {
                sb.append("[file] ").append(path).append(" (内容过长,已省略)\n");
                continue;
            }
            sb.append("[file] ").append(path).append('\n')
                    .append(content).append('\n');
            budget -= content.length();
        }
        return sb.toString();
    }

    private Path safeResolve(String projectId, String rawPath) {
        String relative = UploadPathValidator.normalize(rawPath);
        Path ws = workspace(projectId).toAbsolutePath().normalize();
        Path target = ws.resolve(relative).normalize();
        if (!target.startsWith(ws)) {
            throw ApiException.badRequest("非法文件路径: " + rawPath);
        }
        return target;
    }
}
