package com.example.artifactshare.service;

import com.example.artifactshare.config.ShareProperties;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.service.model.UploadFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 上传内容抽取:multipart 中的多个文件,或一个 zip(整目录打包,agent 产物常用形态)。
 * zip 解包时逐条目做大小/数量限制(防 zip bomb),路径穿越在发布阶段统一校验。
 */
@Component
public class UploadExtractor {

    private final ShareProperties props;

    public UploadExtractor(ShareProperties props) {
        this.props = props;
    }

    public List<UploadFile> extract(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("缺少待上传文件(files)");
        }
        List<UploadFile> result = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            if (name == null || name.isBlank()) {
                throw ApiException.badRequest("上传文件缺少文件名");
            }
            byte[] bytes = readAll(file);
            if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                unzip(name, bytes, result);
            } else {
                result.add(new UploadFile(name, bytes));
            }
        }
        if (result.isEmpty()) {
            throw ApiException.badRequest("上传内容为空");
        }
        return result;
    }

    private byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取上传文件失败: " + file.getOriginalFilename());
        }
    }

    private void unzip(String zipName, byte[] bytes, List<UploadFile> out) {
        Charset charset = Charset.forName(props.getPublish().getZipCharset());
        long total = out.stream().mapToLong(f -> f.content().length).sum();
        int count = out.size();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes), charset)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] data = zis.readAllBytes();
                total += data.length;
                count++;
                if (count > props.getPublish().getMaxFiles() || total > props.getPublish().getMaxTotalBytes()) {
                    throw ApiException.badRequest("zip 内容超过限制(max-files/max-total-bytes): " + zipName);
                }
                out.add(new UploadFile(entry.getName(), data));
            }
        } catch (IOException e) {
            throw ApiException.badRequest("zip 解压失败(编码 zip-charset=" + charset + "): " + zipName);
        }
    }
}
