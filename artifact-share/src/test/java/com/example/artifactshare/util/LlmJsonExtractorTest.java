package com.example.artifactshare.util;

import com.example.artifactshare.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmJsonExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void plainJsonObject() throws Exception {
        String json = LlmJsonExtractor.extractJsonObject("{\"summary\":\"ok\",\"files\":{}}");
        assertEquals("ok", objectMapper.readTree(json).path("summary").asText());
    }

    @Test
    void fencedJsonBlock() throws Exception {
        String raw = "以下是生成结果:\n```json\n{\"summary\":\"s\",\"files\":{\"index.html\":\"<p>hi</p>\"}}\n```\n";
        String json = LlmJsonExtractor.extractJsonObject(raw);
        assertEquals("s", objectMapper.readTree(json).path("summary").asText());
        assertEquals("<p>hi</p>", objectMapper.readTree(json).path("files").path("index.html").asText());
    }

    @Test
    void jsonWithPreambleAndSuffix() throws Exception {
        String raw = "好的,我来生成。 {\"files\":{\"a.js\":\"console.log(1)\"}} 以上。";
        String json = LlmJsonExtractor.extractJsonObject(raw);
        assertEquals("console.log(1)", objectMapper.readTree(json).path("files").path("a.js").asText());
    }

    @Test
    void contentWithBracesInsideStrings() throws Exception {
        String raw = "{\"files\":{\"a.js\":\"const f = () => { return {a: 1}; }\"}}";
        String json = LlmJsonExtractor.extractJsonObject(raw);
        assertEquals("const f = () => { return {a: 1}; }",
                objectMapper.readTree(json).path("files").path("a.js").asText());
    }

    @Test
    void emptyInputRejected() {
        assertThrows(ApiException.class, () -> LlmJsonExtractor.extractJsonObject("  "));
        assertThrows(ApiException.class, () -> LlmJsonExtractor.extractJsonObject(null));
    }

    @Test
    void noJsonRejected() {
        assertThrows(ApiException.class, () -> LlmJsonExtractor.extractJsonObject("抱歉,我无法完成"));
    }
}
