package com.example.artifactshare.web.dto;

import com.example.artifactshare.domain.Artifact;
import com.example.artifactshare.service.model.FileMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

public record ArtifactView(
        String slug,
        String title,
        String publicUrl,
        String entryObjectKey,
        long sizeBytes,
        int fileCount,
        String status,
        Instant publishedAt,
        List<FileMeta> files) {

    public static ArtifactView from(Artifact artifact, ObjectMapper objectMapper) {
        List<FileMeta> files;
        try {
            files = artifact.getFilesJson() == null
                    ? List.of()
                    : objectMapper.readValue(artifact.getFilesJson(), new TypeReference<List<FileMeta>>() {
                    });
        } catch (Exception e) {
            files = List.of();
        }
        return new ArtifactView(artifact.getSlug(), artifact.getTitle(), artifact.getPublicUrl(),
                artifact.getEntryObjectKey(), artifact.getSizeBytes(), artifact.getFileCount(),
                artifact.getStatus(), artifact.getPublishedAt(), files);
    }
}
