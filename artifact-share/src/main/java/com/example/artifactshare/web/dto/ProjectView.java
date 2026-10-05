package com.example.artifactshare.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProjectView(
        String id,
        String name,
        String model,
        String type,
        String status,
        List<FileEntry> files,
        List<Map<String, String>> chat,
        String publishedSlug,
        String publishedUrl,
        Instant createdAt,
        Instant updatedAt) {
}
