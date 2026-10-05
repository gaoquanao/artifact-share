package com.example.artifactshare.service.model;

/** 已发布文件元数据,序列化为 JSON 存入 artifact.files_json */
public record FileMeta(String path, String contentType, long size) {
}
