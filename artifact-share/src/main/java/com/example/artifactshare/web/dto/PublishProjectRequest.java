package com.example.artifactshare.web.dto;

/** POST /api/v1/projects/{id}/publish 请求体;slug/title 均可省略 */
public record PublishProjectRequest(String slug, String title) {
}
