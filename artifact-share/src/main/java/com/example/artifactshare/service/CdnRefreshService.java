package com.example.artifactshare.service;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.profile.DefaultProfile;
import com.example.artifactshare.config.ShareProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * CDN 缓存预热/刷新,走阿里云 CommonRequest(产品 Cdn,版本 2018-05-10),避免额外 SDK 依赖。
 * 发布成功后预热入口 URL;下线后刷新目录,让缓存尽快失效。预热/刷新有每日配额,失败不影响发布。
 */
@Service
public class CdnRefreshService {

    private static final Logger log = LoggerFactory.getLogger(CdnRefreshService.class);
    private static final String CDN_DOMAIN = "cdn.aliyuncs.com";
    private static final String CDN_VERSION = "2018-05-10";

    private final ShareProperties props;
    private volatile IAcsClient client;

    public CdnRefreshService(ShareProperties props) {
        this.props = props;
    }

    @Async
    public void warmup(String publicUrl) {
        if (!props.getCdn().isEnabled() || !props.getCdn().isWarmupOnPublish()) {
            return;
        }
        try {
            CommonRequest request = baseRequest("PushObjectCache");
            request.putQueryParameter("ObjectPath", publicUrl);
            request.putQueryParameter("Area", "cn");
            CommonResponse response = client().getCommonResponse(request);
            log.info("CDN 预热已提交: {} -> {}", publicUrl, response.getData());
        } catch (Exception e) {
            log.warn("CDN 预热失败(不影响发布): {} - {}", publicUrl, e.getMessage());
        }
    }

    @Async
    public void purge(String publicUrl) {
        if (!props.getCdn().isEnabled()) {
            return;
        }
        try {
            CommonRequest request = baseRequest("RefreshObjectCaches");
            request.putQueryParameter("ObjectPath", publicUrl);
            request.putQueryParameter("ObjectType", "Directory");
            CommonResponse response = client().getCommonResponse(request);
            log.info("CDN 缓存刷新已提交: {} -> {}", publicUrl, response.getData());
        } catch (Exception e) {
            log.warn("CDN 缓存刷新失败: {} - {}", publicUrl, e.getMessage());
        }
    }

    private CommonRequest baseRequest(String action) {
        CommonRequest request = new CommonRequest();
        request.setSysDomain(CDN_DOMAIN);
        request.setSysVersion(CDN_VERSION);
        request.setSysAction(action);
        return request;
    }

    private IAcsClient client() {
        IAcsClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    ShareProperties.Cdn cfg = props.getCdn();
                    DefaultProfile profile = DefaultProfile.getProfile(
                            cfg.getRegion(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());
                    client = new DefaultAcsClient(profile);
                }
                c = client;
            }
        }
        return c;
    }
}
