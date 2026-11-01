package com.ym.intelligentcollaborativecloudgallery.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class FilePictureUpload extends PictureUploadTemplate {
    /**
     * 校验输入源(本地文件或 URL)
     *
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     */
    @Override
    protected void validatePicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        // 校验图片类型、大小等
        ThrowUtils.throwIf(multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "上传的图片文件不能为空");
        // 1.校验文件大小
        long fileSize = multipartFile.getSize();
        // 5MB
        final long MAX_FILE_SIZE = 5 * 1024 * 1024;
        ThrowUtils.throwIf(fileSize > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, "上传的图片文件大小不能超过5MB");
        // 2.校验文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(fileSuffix == null || fileSuffix.isEmpty(), ErrorCode.PARAMS_ERROR, "上传的图片文件必须有后缀名");
        final List<String> ALLOW_SUFFIXES = List.of("jpg", "jpeg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_SUFFIXES.contains(fileSuffix.toLowerCase()), ErrorCode.PARAMS_ERROR, "上传的图片文件类型不支持");

    }

    /**
     * 获取输入源的原始文件名
     *
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     * @return 原始文件名
     */
    @Override
    protected String getOriginalFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    /**
     * 处理输入源，将其转换为临时文件
     *
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     * @param file 临时文件
     */
    @Override
    protected void processFile(Object inputSource, File file) throws IOException {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }
}
