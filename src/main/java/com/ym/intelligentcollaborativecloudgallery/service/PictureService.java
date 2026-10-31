package com.ym.intelligentcollaborativecloudgallery.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ym.intelligentcollaborativecloudgallery.model.dto.picture.PictureQueryRequest;
import com.ym.intelligentcollaborativecloudgallery.model.dto.picture.PictureUploadRequest;
import com.ym.intelligentcollaborativecloudgallery.model.dto.user.UserQueryRequest;
import com.ym.intelligentcollaborativecloudgallery.model.entity.Picture;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author yancenxin
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-10-28 21:27:00
*/
public interface PictureService extends IService<Picture> {
    void validPicture(Picture picture);

    /**
     * 上传图片
     */
    PictureVO uploadPicture(MultipartFile file, PictureUploadRequest pictureUploadRequest, User loginUser);

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 获取QueryWrapper对象
     * @param pictureQueryRequest 图片查询请求对象
     * @return QueryWrapper对象
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);
}
