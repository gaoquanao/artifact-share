package com.example.artifactshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * share.* 配置项。字段用 JavaBean 风格手写访问器,避免 Lombok 在新 JDK 上的兼容性问题。
 */
@ConfigurationProperties(prefix = "share")
public class ShareProperties {

    private String scheme = "https";

    /** 分享泛域名后缀,如 s.example.com,最终访问 {slug}.s.example.com */
    private String domain = "s.example.com";

    private Oss oss = new Oss();
    private Cdn cdn = new Cdn();
    private Publish publish = new Publish();

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Oss getOss() {
        return oss;
    }

    public void setOss(Oss oss) {
        this.oss = oss;
    }

    public Cdn getCdn() {
        return cdn;
    }

    public void setCdn(Cdn cdn) {
        this.cdn = cdn;
    }

    public Publish getPublish() {
        return publish;
    }

    public void setPublish(Publish publish) {
        this.publish = publish;
    }

    public static class Oss {
        private String endpoint = "https://oss-cn-hangzhou.aliyuncs.com";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String bucket = "artifact-share";
        /** 对象统一落在 <key-prefix>/<slug>/ 下,与 CDN/边缘改写规则中的 /s/{slug}/ 一致 */
        private String keyPrefix = "s";
        private String htmlCacheControl = "no-cache";
        private String assetCacheControl = "public, max-age=3600";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public String getHtmlCacheControl() {
            return htmlCacheControl;
        }

        public void setHtmlCacheControl(String htmlCacheControl) {
            this.htmlCacheControl = htmlCacheControl;
        }

        public String getAssetCacheControl() {
            return assetCacheControl;
        }

        public void setAssetCacheControl(String assetCacheControl) {
            this.assetCacheControl = assetCacheControl;
        }
    }

    public static class Cdn {
        private boolean enabled = false;
        private String region = "cn-hangzhou";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private boolean warmupOnPublish = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public boolean isWarmupOnPublish() {
            return warmupOnPublish;
        }

        public void setWarmupOnPublish(boolean warmupOnPublish) {
            this.warmupOnPublish = warmupOnPublish;
        }
    }

    public static class Publish {
        private int maxFiles = 300;
        private long maxTotalBytes = 50L * 1024 * 1024;
        private String zipCharset = "UTF-8";
        private Set<String> reservedSlugs = Set.of("www", "api", "admin", "cdn", "static",
                "assets", "mail", "test", "demo", "docs", "help", "status", "blog");

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public long getMaxTotalBytes() {
            return maxTotalBytes;
        }

        public void setMaxTotalBytes(long maxTotalBytes) {
            this.maxTotalBytes = maxTotalBytes;
        }

        public String getZipCharset() {
            return zipCharset;
        }

        public void setZipCharset(String zipCharset) {
            this.zipCharset = zipCharset;
        }

        public Set<String> getReservedSlugs() {
            return reservedSlugs;
        }

        public void setReservedSlugs(Set<String> reservedSlugs) {
            this.reservedSlugs = reservedSlugs;
        }
    }
}
