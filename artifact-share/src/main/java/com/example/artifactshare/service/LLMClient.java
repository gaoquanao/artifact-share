package com.example.artifactshare.service;

import com.example.artifactshare.config.AgentProperties;
import com.example.artifactshare.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 客户端(/chat/completions),支持视觉消息
 * (content 为 [{type:text},{type:image_url}] 结构)。API Key 只在服务端使用。
 */
@Service
public class LLMClient {

    private static final Logger log = LoggerFactory.getLogger(LLMClient.class);
    private static final int ERROR_BODY_LIMIT = 500;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final AgentProperties props;
    private final ObjectMapper objectMapper;

    public LLMClient(AgentProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * @param messages OpenAI 消息列表;content 为字符串或 multimodal parts 列表
     * @return assistant 回复文本
     */
    public String chat(String model, List<Map<String, Object>> messages) {
        if (!props.isEnabled() || props.getApiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "LLM 未配置:请设置 share.agent.enabled=true 与环境变量 LLM_API_KEY");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", props.getTemperature(),
                "max_tokens", props.getMaxTokens(),
                "stream", false);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "构造 LLM 请求失败: " + e.getMessage());
        }
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 调用被中断");
        } catch (Exception e) {
            log.warn("LLM 调用异常: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 调用失败: " + e.getMessage());
        }
        if (response.statusCode() / 100 != 2) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 服务返回 HTTP " + response.statusCode()
                    + ": " + abbreviate(response.body()));
        }
        try {
            return objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "解析 LLM 响应失败: " + abbreviate(response.body()));
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= ERROR_BODY_LIMIT ? s : s.substring(0, ERROR_BODY_LIMIT) + "...";
    }
}
