package com.example.artifactshare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "artifact")
public class Artifact {

    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DELETED = "DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    private String title;

    /** OSS 首页对象 key,如 s/{slug}/index.html */
    @Column(nullable = false, length = 512)
    private String entryObjectKey;

    /** 分享地址,如 https://{slug}.s.example.com/ */
    @Column(nullable = false, length = 512)
    private String publicUrl;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int fileCount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Instant publishedAt;

    /** 文件清单 JSON:[{path, contentType, size}] */
    @Lob
    private String filesJson;

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEntryObjectKey() {
        return entryObjectKey;
    }

    public void setEntryObjectKey(String entryObjectKey) {
        this.entryObjectKey = entryObjectKey;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getFilesJson() {
        return filesJson;
    }

    public void setFilesJson(String filesJson) {
        this.filesJson = filesJson;
    }
}
