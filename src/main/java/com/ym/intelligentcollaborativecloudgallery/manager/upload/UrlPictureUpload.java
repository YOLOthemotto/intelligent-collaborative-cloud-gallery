package com.ym.intelligentcollaborativecloudgallery.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * 图片上传模板类，提供了上传图片的通用流程，包括校验、处理、上传和结果封装等步骤。具体的图片来源（本地文件或 URL）由子类实现。
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    /**
     * 校验输入源(本地文件或 URL)
     *
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     */
    @Override
    protected void validatePicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 校验非空
        ThrowUtils.throwIf(StrUtil.isBlankIfStr(fileUrl), ErrorCode.PARAMS_ERROR, "上传的图片文件 Url 不能为空");
        // 校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传的图片文件 Url 格式不正确");
        }
        // 校验 URL 协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"), ErrorCode.PARAMS_ERROR, "仅支持 http/https 协议的图片文件 Url");
        // 发送 HEAD 请求获取文件大小和类型
        try (HttpResponse httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl).execute()) {
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 校验文件大小和类型
            String contentType = httpResponse.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                final List<String> ALLOW_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp, image/jpg");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()), ErrorCode.PARAMS_ERROR, "上传的图片文件类型不支持");
            }
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    // 5MB
                    final long MAX_FILE_SIZE = 5 * 1024 * 1024;
                    ThrowUtils.throwIf(contentLength > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, "上传的图片文件大小不能超过5MB");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传的图片文件大小不合法");
                }
            }
        }
    }

    /**
     * 获取输入源的原始文件名
     *
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     * @return 原始文件名
     */
    @Override
    protected String getOriginalFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    /**
     * 处理输入源，将其转换为临时文件
     *
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     * @param file
     */
    @Override
    protected void processFile(Object inputSource, File file) throws IOException {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, file);
    }
}
