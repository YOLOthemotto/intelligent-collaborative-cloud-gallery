package com.ym.intelligentcollaborativecloudgallery.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.ym.intelligentcollaborativecloudgallery.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

@Component
public class CosManager {
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传文件到腾讯云 COS
     *
     * @param key  文件在 COS 中的路径
     * @param file 要上传的文件
     * @return 上传结果
     * @throws CosClientException  客户端异常
     * @throws CosServiceException 服务端异常
     */
    public PutObjectResult putObject(String key, File file)
            throws CosClientException, CosServiceException {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucketName(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载文件从腾讯云 COS
     * @param key 文件在 COS 中的路径
     * @return 下载的文件
     * @throws CosClientException  客户端异常
     * @throws CosServiceException 服务端异常
     */
    public COSObject getObject(String key) throws CosClientException, CosServiceException {
        return cosClient.getObject(cosClientConfig.getBucketName(), key);
    }

    /**
     * 上传并解析图片信息
     * @param key  文件在 COS 中的路径
     * @param file 要上传的文件
     * @return 上传结果
     * @throws CosClientException  客户端异常
     * @throws CosServiceException 服务端异常
     */
    public PutObjectResult putPictureObject(String key, File file)
            throws CosClientException, CosServiceException {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucketName(), key, file);
        // 对面向图片进行处理，例如生成缩略图、提取 EXIF 信息等
        PicOperations picOperations = new PicOperations();
        // 1 表示提取图片信息，0 表示不提取
        picOperations.setIsPicInfo(1);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }
}
