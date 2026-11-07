package com.ym.intelligentcollaborativecloudgallery.manager;

import cn.hutool.core.io.FileUtil;
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
import java.util.ArrayList;
import java.util.List;

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
        // 图片压缩(转成 webp 格式)
        String webpKey = FileUtil.mainName(key) + ".webp";
        List<PicOperations.Rule> rules = new ArrayList<>();
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webpKey);
        compressRule.setBucket(cosClientConfig.getBucketName());
        compressRule.setRule("imageMogr2/format/webp");
        // 缩略图处理 仅对大于 20KB 的图片生成缩略图
        if (file.length() > 20 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail" + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setBucket(cosClientConfig.getBucketName());
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>",256,256));
            rules.add(thumbnailRule);
        }
        rules.add(compressRule);
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     * @param key 文件在 COS 中的路径
     * @throws CosClientException  客户端异常
     * @throws CosServiceException 服务端异常
     */
    public void deleteObject(String key) throws CosClientException, CosServiceException {
        cosClient.deleteObject(cosClientConfig.getBucketName(), key);
    }
}
