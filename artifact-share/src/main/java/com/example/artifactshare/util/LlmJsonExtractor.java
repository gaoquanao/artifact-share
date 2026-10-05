package com.example.artifactshare.util;

import com.example.artifactshare.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 从大模型回复中提取 JSON 对象:容忍 ```json 围栏、前后缀说明文字等常见噪声。
 */
public final class LlmJsonExtractor {

    private LlmJsonExtractor() {
    }

    public static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("模型返回为空");
        }
        String text = raw.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", start);
            if (start >= 0 && end > start) {
                String inner = text.substring(start + 1, end).trim();
                if (inner.startsWith("{")) {
                    return inner;
                }
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "模型输出中未找到 JSON 对象");
    }
}
