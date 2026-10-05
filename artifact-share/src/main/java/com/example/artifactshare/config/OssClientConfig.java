package com.example.artifactshare.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssClientConfig {

    private static final Logger log = LoggerFactory.getLogger(OssClientConfig.class);

    /**
     * 未配置 AK/SK 时用占位凭证启动,保证服务可跑、接口可自测;
     * 真正发布会在 ArtifactPublishService 中前置报"OSS 未配置"。
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(ShareProperties props) {
        ShareProperties.Oss oss = props.getOss();
        String ak = oss.getAccessKeyId();
        String sk = oss.getAccessKeySecret();
        if (ak.isBlank() || sk.isBlank()) {
            log.warn("OSS 未配置(share.oss.access-key-id/secret 为空),发布接口将返回 503;"
                    + "请设置环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET");
            ak = "not-configured";
            sk = "not-configured";
        }
        return new OSSClientBuilder().build(oss.getEndpoint(), ak, sk);
    }
}
