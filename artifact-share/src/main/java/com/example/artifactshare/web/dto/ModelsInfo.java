package com.example.artifactshare.web.dto;

import java.util.List;

/** GET /api/v1/models:前端模型选择器数据源 */
public record ModelsInfo(boolean enabled, List<String> models, String defaultModel) {
}
