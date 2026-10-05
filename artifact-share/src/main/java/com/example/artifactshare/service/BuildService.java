package com.example.artifactshare.service;

import com.example.artifactshare.config.AgentProperties;
import com.example.artifactshare.domain.Project;
import com.example.artifactshare.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 项目构建:package.json 存在 → npm install + npm run build(vite 产物 dist/);
 * 否则视为纯静态工程,源文件即产物。
 *
 * 安全提示:npm 构建会执行模型生成的代码(依赖安装脚本),生产环境务必把本服务
 * 部署在隔离容器/无敏感凭据的环境中。
 */
@Service
public class BuildService {

    private static final Logger log = LoggerFactory.getLogger(BuildService.class);
    private static final int OUTPUT_LIMIT = 8000;

    private final ProjectFileService fileService;
    private final AgentProperties props;

    public BuildService(ProjectFileService fileService, AgentProperties props) {
        this.fileService = fileService;
        this.props = props;
    }

    /** 预览/发布使用的产物根目录:NPM 工程用 dist/(需先构建成功),静态工程用工作区根 */
    public Path outputRoot(Project project) {
        Path ws = fileService.workspace(project.getId());
        if (Project.TYPE_NPM.equals(project.getType())) {
            Path dist = ws.resolve("dist");
            if (!Files.isDirectory(dist)) {
                throw ApiException.badRequest("NPM 工程尚未构建或构建失败,请先执行构建");
            }
            return dist;
        }
        return ws;
    }

    public BuildResult build(Project project) {
        Path ws = fileService.workspace(project.getId());
        if (!Files.exists(ws.resolve("package.json"))) {
            project.setType(Project.TYPE_STATIC);
            project.setStatus(Project.STATUS_BUILD_OK);
            return new BuildResult(true, Project.TYPE_STATIC, "纯静态工程,无需构建,可直接预览/发布。", null);
        }
        project.setType(Project.TYPE_NPM);

        StringBuilder output = new StringBuilder();
        ProcessResult install = run(ws, List.of(props.getBuild().getNpmBin(), "install", "--no-audit", "--no-fund"));
        output.append("$ npm install --no-audit --no-fund\n").append(install.output());
        if (install.exitCode() != 0) {
            project.setStatus(Project.STATUS_BUILD_FAILED);
            return new BuildResult(false, Project.TYPE_NPM, truncate(output), install.hint());
        }

        output.append("\n$ npm run build\n");
        ProcessResult buildCmd = run(ws, List.of(props.getBuild().getNpmBin(), "run", "build"));
        output.append(buildCmd.output());
        if (buildCmd.exitCode() != 0) {
            project.setStatus(Project.STATUS_BUILD_FAILED);
            return new BuildResult(false, Project.TYPE_NPM, truncate(output), buildCmd.hint());
        }
        if (!Files.isDirectory(ws.resolve("dist"))) {
            project.setStatus(Project.STATUS_BUILD_FAILED);
            return new BuildResult(false, Project.TYPE_NPM, truncate(output),
                    "构建成功但未发现 dist/ 目录:请确认 package.json 的 build 脚本输出到 dist(如 vite build)");
        }
        project.setStatus(Project.STATUS_BUILD_OK);
        log.info("项目 {} npm 构建成功", project.getId());
        return new BuildResult(true, Project.TYPE_NPM, truncate(output), "dist");
    }

    private ProcessResult run(Path dir, List<String> command) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(dir.toFile())
                    .redirectErrorStream(true);
            process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(props.getBuild().getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(-1, output + "\n[执行超时]", null);
            }
            return new ProcessResult(process.exitValue(), output, null);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("Cannot run program") || message.contains("No such file")) {
                return new ProcessResult(-1, message,
                        "未找到 npm 命令:服务器需安装 Node.js,或将 share.agent.build.npm-bin 配置为绝对路径");
            }
            return new ProcessResult(-1, message, "构建命令执行失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new ProcessResult(-1, "构建被中断", null);
        }
    }

    private String truncate(StringBuilder output) {
        String s = output.toString();
        return s.length() <= OUTPUT_LIMIT ? s : s.substring(s.length() - OUTPUT_LIMIT);
    }

    private record ProcessResult(int exitCode, String output, String hint) {
    }

    public record BuildResult(boolean ok, String type, String output, String hint) {
    }
}
