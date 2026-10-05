package com.example.artifactshare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** 网页生成工作台项目:文件在工作区磁盘(chat/工作区根目录见 AgentProperties),DB 存元数据与对话历史 */
@Entity
@Table(name = "project")
public class Project {

    public static final String TYPE_STATIC = "STATIC";
    public static final String TYPE_NPM = "NPM";

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_BUILD_OK = "BUILD_OK";
    public static final String STATUS_BUILD_FAILED = "BUILD_FAILED";

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    /** 最近一次生成使用的模型 */
    private String model;

    /** STATIC:无需构建;NPM:package.json 存在,需 npm build */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 16)
    private String status;

    /** 对话历史 JSON:[{role, content}] */
    @Lob
    private String chatJson;

    private String publishedSlug;

    private String publishedUrl;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChatJson() {
        return chatJson;
    }

    public void setChatJson(String chatJson) {
        this.chatJson = chatJson;
    }

    public String getPublishedSlug() {
        return publishedSlug;
    }

    public void setPublishedSlug(String publishedSlug) {
        this.publishedSlug = publishedSlug;
    }

    public String getPublishedUrl() {
        return publishedUrl;
    }

    public void setPublishedUrl(String publishedUrl) {
        this.publishedUrl = publishedUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
