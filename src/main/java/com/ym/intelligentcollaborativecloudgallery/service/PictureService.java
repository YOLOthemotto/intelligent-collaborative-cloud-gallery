package com.ym.intelligentcollaborativecloudgallery.service;

import com.ym.intelligentcollaborativecloudgallery.model.dto.picture.*;
import com.ym.intelligentcollaborativecloudgallery.model.vo.PictureCursorVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ym.intelligentcollaborativecloudgallery.cache.CacheType;
import com.ym.intelligentcollaborativecloudgallery.model.entity.Picture;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.vo.PictureVO;

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
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 分页查询图片并按指定缓存层级缓存结果。
     */
    Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest request,
                                                 HttpServletRequest httpRequest,
                                                 CacheType cacheType);

    /**
     * 获取QueryWrapper对象
     * @param pictureQueryRequest 图片查询请求对象
     * @return QueryWrapper对象
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 游标分页查询图片（按 createTime、id 倒序）
     */
    PictureCursorVO listPictureVOByCursor(PictureCursorQueryRequest request, HttpServletRequest httpRequest);

    /**
     * 图片审核
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数
     * @param picture 图片对象
     * @param loginUser 登录用户对象
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量导入图片
     * @param pictureUploadByBatchRequest 批量导入图片请求对象
     * @param loginUser 登录用户对象
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    /**
     *  清理图片文件
     * @param oldPicture 旧图片对象
     */
    void clearPictureFile(Picture oldPicture);
}
