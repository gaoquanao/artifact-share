package com.example.artifactshare.web.dto;

/** PATCH /api/v1/artifacts/{slug} 请求体:修改分享域名 */
public record RenameRequest(String slug) {
}
