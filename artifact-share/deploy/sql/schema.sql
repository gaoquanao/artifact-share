-- 生产 MySQL 建表参考(H2 环境由 JPA ddl-auto=update 自动建表,无需执行)
CREATE TABLE IF NOT EXISTS artifact (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    slug            VARCHAR(64)  NOT NULL,
    title           VARCHAR(255) NULL,
    entry_object_key VARCHAR(512) NOT NULL,
    public_url      VARCHAR(512) NOT NULL,
    size_bytes      BIGINT       NOT NULL DEFAULT 0,
    file_count      INT          NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL,
    published_at    TIMESTAMP(6) NOT NULL,
    files_json      LONGTEXT     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_artifact_slug (slug),
    KEY idx_artifact_status_published (status, published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
