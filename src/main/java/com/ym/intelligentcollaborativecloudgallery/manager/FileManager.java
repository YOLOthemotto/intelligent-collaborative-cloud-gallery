package com.ym.intelligentcollaborativecloudgallery.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.ym.intelligentcollaborativecloudgallery.common.ResultUtils;
import com.ym.intelligentcollaborativecloudgallery.config.CosClientConfig;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.exception.ThrowUtils;
import com.ym.intelligentcollaborativecloudgallery.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class FileManager {
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 校验图片
        validatePicture(multipartFile);
        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = multipartFile.getOriginalFilename();
        // 生成安全的上传文件名，避免文件名冲突
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid, FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        // 解析结果并返回
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            // 计算图片比例，保留两位小数
            double picScale = NumberUtil.round((double) picWidth / picHeight, 2).doubleValue();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + uploadPath);
            uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            return uploadPictureResult;
        } catch (IOException e) {
            log.error("文件上传到对象存储服务失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            deleteTempFile(file);
        }

    }

    /**
     * 删除临时文件
     * @param tempFile 要删除的临时文件
     */
    private static void deleteTempFile(File tempFile) {
        //删除临时文件
        if (tempFile != null && tempFile.exists()) {
            boolean deletedResult = tempFile.delete();
            if (!deletedResult) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    /**
     * 校验图片文件是否合法
     * @param multipartFile 要校验的图片文件
     */
    private void validatePicture(MultipartFile multipartFile) {
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

}
