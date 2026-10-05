package com.example.artifactshare.service.model;

/** 一次待发布的产物文件:相对路径 + 内容 */
public record UploadFile(String path, byte[] content) {
}
