package com.ym.intelligentcollaborativecloudgallery.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.ym.intelligentcollaborativecloudgallery.config.CosClientConfig;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.manager.CosManager;
import com.ym.intelligentcollaborativecloudgallery.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;


@Slf4j
public abstract class PictureUploadTemplate {
    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private CosManager cosManager;

    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1.校验图片
        validatePicture(inputSource);
        // 2.图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginalFilename(inputSource);
        // 生成安全的上传文件名，避免文件名冲突
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        // 解析结果并返回
        File file = null;
        try {
            // 3.创建临时文件,获取文件到服务器
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源
            processFile(inputSource,file);
            // 4.上传文件到对象存储服务
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 获取到图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (objectList != null && !objectList.isEmpty()) {
                // 处理 objectList
                CIObject compressedCiObject = objectList.get(0);
                return buildResult(originalFilename, compressedCiObject);
            }
            // 5.封装返回结果
            return buildResult(putObjectResult, uploadPath, originalFilename, file, imageInfo);
        } catch (IOException e) {
            log.error("文件上传到对象存储服务失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            // 6.删除临时文件
            deleteTempFile(file);
        }

    }

    /**
     * 封装返回结果
     * @param originalFilename 原始文件名
     * @param compressedCiObject 压缩后的图片对象
     * @return UploadPictureResult 上传图片结果
     */
    private UploadPictureResult buildResult(String originalFilename, CIObject compressedCiObject) {
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        // 计算图片比例，保留两位小数
        double picScale = NumberUtil.round((double) picWidth / picHeight, 2).doubleValue();
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(cosClientConfig.getHost() + '/' + compressedCiObject.getKey());
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        return uploadPictureResult;
    }

    /**
     * 封装返回结果
     *
     * @param putObjectResult  上传结果
     * @param uploadPath       上传路径
     * @param originalFilename 原始文件名
     * @param file             临时文件
     * @param imageInfo
     * @return UploadPictureResult 上传图片结果
     */
    @NotNull
    private UploadPictureResult buildResult(PutObjectResult putObjectResult, String uploadPath, String originalFilename, File file, ImageInfo imageInfo) {
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
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        return uploadPictureResult;
    }

    /**
     * 校验输入源(本地文件或 URL)
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     */
    protected abstract void validatePicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     * @return 原始文件名
     */
    protected abstract String getOriginalFilename(Object inputSource);

    /**
     * 处理输入源，将其转换为临时文件
     *
     * @param inputSource 输入源，可以是 MultipartFile、File 或 URL
     * @param file
     */
    protected abstract void processFile(Object inputSource, File file) throws IOException;

    /**
     * 删除临时文件
     *
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

}
