package com.ym.intelligentcollaborativecloudgallery.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.ym.intelligentcollaborativecloudgallery.annotation.AuthCheck;
import com.ym.intelligentcollaborativecloudgallery.common.BaseResponse;
import com.ym.intelligentcollaborativecloudgallery.common.ResultUtils;
import com.ym.intelligentcollaborativecloudgallery.constant.UserConstant;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.manager.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {
    @Resource
    private CosManager cosManager;

    /**
     * 测试文件上传接口
     *
     * @param file
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile file) throws IOException {
        //文件目录
        String fileName = file.getOriginalFilename();
        String filePath = String.format("/test/%s", fileName);
        File tempFile = null;
        try {
            tempFile = File.createTempFile(filePath, null);
            file.transferTo(tempFile);
            cosManager.putObject(filePath, tempFile);
            //返回文件访问路径
            return ResultUtils.success(filePath);
        } catch (IOException e) {
            log.error("file upload failed,filepath ={}", filePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            System.out.println("临时文件路径：" + tempFile.getAbsolutePath());
            System.out.println("系统临时目录：" + System.getProperty("java.io.tmpdir"));
            //删除临时文件
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 测试文件下载接口
     *
     * @param filePath
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/download")
    public void testDownloadFile(String filePath, HttpServletResponse response) {
        COSObjectInputStream cosObjectInput = null;
        try {
            COSObject cosObject = cosManager.getObject(filePath);
            cosObjectInput = cosObject.getObjectContent();
            response.setContentType("application/octet-stream; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filePath + "\"");
            response.getOutputStream().write(cosObjectInput.readAllBytes());
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download failed,filepath ={}", filePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件下载失败");

        } finally {
            //关闭COSObjectInputStream
            if (cosObjectInput != null) {
                try {
                    cosObjectInput.close();
                } catch (IOException e) {
                    log.warn("Failed to close COSObjectInputStream", e);
                }
            }
        }
    }
}
