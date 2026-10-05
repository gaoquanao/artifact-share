package com.example.artifactshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * share.agent.* 网页生成工作台配置:OpenAI 兼容的大模型接入、模型清单(前端 picker)、
 * 工作区目录、npm 构建参数。
 */
@ConfigurationProperties(prefix = "share.agent")
public class AgentProperties {

    /** 总开关;关闭时生成接口返回 503,文件编辑/构建/发布仍可用 */
    private boolean enabled = false;
    /** OpenAI 兼容服务地址,如 https://open.bigmodel.cn/api/paas/v4 */
    private String baseUrl = "";
    private String apiKey = "";
    /** 前端模型选择器的可选项 */
    private List<String> models = List.of();
    private String defaultModel = "";
    private int timeoutSeconds = 300;
    private int maxTokens = 16384;
    private double temperature = 0.7;

    private Workspace workspace = new Workspace();
    private Build build = new Build();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<String> getModels() {
        return models;
    }

    public void setModels(List<String> models) {
        this.models = models;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public Build getBuild() {
        return build;
    }

    public void setBuild(Build build) {
        this.build = build;
    }

    public static class Workspace {
        private String root = "./data/projects";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static class Build {
        /** npm 可执行文件;systemd 部署时 PATH 可能不全,建议绝对路径 */
        private String npmBin = "npm";
        private int timeoutSeconds = 900;

        public String getNpmBin() {
            return npmBin;
        }

        public void setNpmBin(String npmBin) {
            this.npmBin = npmBin;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
