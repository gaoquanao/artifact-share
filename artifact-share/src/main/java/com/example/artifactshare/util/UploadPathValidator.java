package com.example.artifactshare.util;

import com.example.artifactshare.exception.ApiException;

import java.nio.file.Path;
import java.nio.file.Paths;

/** 产物相对路径清洗:防目录穿越(zip slip),统一斜杠 */
public final class UploadPathValidator {

    private UploadPathValidator() {
    }

    public static String normalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw ApiException.badRequest("文件路径为空");
        }
        String p = rawPath.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isEmpty()) {
            throw ApiException.badRequest("文件路径为空: " + rawPath);
        }
        Path normalized = Paths.get(p).normalize();
        String s = normalized.toString().replace('\\', '/');
        if (s.isEmpty() || s.equals(".") || s.equals("..") || s.startsWith("../") || normalized.isAbsolute()) {
            throw ApiException.badRequest("非法文件路径: " + rawPath);
        }
        return s;
    }
}
