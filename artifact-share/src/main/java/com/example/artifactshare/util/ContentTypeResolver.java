package com.example.artifactshare.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * 按扩展名解析 Content-Type。
 * 浏览器能否在线渲染取决于该值:OSS/CDN 按对象存储的 Content-Type 原样返回,
 * 若是 application/octet-stream 浏览器会转下载而不是渲染页面。
 */
@Component
public class ContentTypeResolver {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("map", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("avif", "image/avif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("eot", "application/vnd.ms-fontobject"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("xml", "application/xml; charset=utf-8"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("md", "text/markdown; charset=utf-8"),
            Map.entry("wasm", "application/wasm"),
            Map.entry("zip", "application/zip"));

    public String resolve(String path) {
        String ext = extension(path);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }

    public boolean isHtmlLike(String path) {
        return resolve(path).startsWith("text/html");
    }

    private String extension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String ext = path.substring(dot + 1);
        int slash = Math.max(path.lastIndexOf('/'), 0);
        return dot < slash ? "" : ext.toLowerCase(Locale.ROOT);
    }
}
