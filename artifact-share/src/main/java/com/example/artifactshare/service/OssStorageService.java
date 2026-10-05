package com.example.artifactshare.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.example.artifactshare.config.ShareProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;

/** OSS 对象读写:统一落在 <key-prefix>/<slug>/ 前缀下 */
@Service
public class OssStorageService {

    private final OSS oss;
    private final ShareProperties props;

    public OssStorageService(OSS oss, ShareProperties props) {
        this.oss = oss;
        this.props = props;
    }

    public String slugPrefix(String slug) {
        return props.getOss().getKeyPrefix() + "/" + slug;
    }

    public void put(String objectKey, byte[] body, String contentType, String cacheControl) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(body.length);
        // Content-Type 决定浏览器渲染还是下载,必须显式设置
        metadata.setContentType(contentType);
        if (cacheControl != null && !cacheControl.isBlank()) {
            metadata.setCacheControl(cacheControl);
        }
        oss.putObject(props.getOss().getBucket(), objectKey, new ByteArrayInputStream(body), metadata);
    }

    /** 删除某个 slug 前缀下的全部对象(分页批量删) */
    public void deleteBySlug(String slug) {
        String bucket = props.getOss().getBucket();
        ListObjectsV2Request request = new ListObjectsV2Request(bucket);
        request.setPrefix(slugPrefix(slug) + "/");
        ListObjectsV2Result result;
        do {
            result = oss.listObjectsV2(request);
            List<String> keys = result.getObjectSummaries().stream()
                    .map(OSSObjectSummary::getKey)
                    .toList();
            if (!keys.isEmpty()) {
                oss.deleteObjects(new DeleteObjectsRequest(bucket).withKeys(keys));
            }
            request.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());
    }

    /**
     * 改名:把 fromSlug 前缀下全部对象服务端复制到 toSlug 前缀(元数据保留),
     * 全部复制成功后再删旧对象——复制中途失败不破坏原数据,删除中途失败只留孤儿对象。
     */
    public void moveToPrefix(String fromSlug, String toSlug) {
        String bucket = props.getOss().getBucket();
        String srcPrefix = slugPrefix(fromSlug) + "/";
        String dstPrefix = slugPrefix(toSlug) + "/";
        ListObjectsV2Request request = new ListObjectsV2Request(bucket);
        request.setPrefix(srcPrefix);
        ListObjectsV2Result result;
        do {
            result = oss.listObjectsV2(request);
            for (OSSObjectSummary summary : result.getObjectSummaries()) {
                String relative = summary.getKey().substring(srcPrefix.length());
                oss.copyObject(bucket, summary.getKey(), bucket, dstPrefix + relative);
            }
            request.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());
        deleteBySlug(fromSlug);
    }
}
