package com.ym.intelligentcollaborativecloudgallery.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ym.intelligentcollaborativecloudgallery.cache.CacheService;
import com.ym.intelligentcollaborativecloudgallery.cache.CacheType;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.exception.ThrowUtils;
import com.ym.intelligentcollaborativecloudgallery.manager.CosManager;
import com.ym.intelligentcollaborativecloudgallery.manager.upload.FilePictureUpload;
import com.ym.intelligentcollaborativecloudgallery.manager.upload.PictureUploadTemplate;
import com.ym.intelligentcollaborativecloudgallery.manager.upload.UrlPictureUpload;
import com.ym.intelligentcollaborativecloudgallery.model.dto.picture.*;
import com.ym.intelligentcollaborativecloudgallery.model.entity.Picture;
import com.ym.intelligentcollaborativecloudgallery.model.entity.Space;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.enums.PictureReviewStatusEnum;
import com.ym.intelligentcollaborativecloudgallery.model.vo.PictureCursorVO;
import com.ym.intelligentcollaborativecloudgallery.model.vo.PictureVO;
import com.ym.intelligentcollaborativecloudgallery.model.vo.UserVO;
import com.ym.intelligentcollaborativecloudgallery.service.PictureService;
import com.ym.intelligentcollaborativecloudgallery.mapper.PictureMapper;
import com.ym.intelligentcollaborativecloudgallery.service.SpaceService;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import com.ym.intelligentcollaborativecloudgallery.utils.ColorSimilarUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yancenxin
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-10-28 21:27:00
 */
@Service
@AllArgsConstructor
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    private final UserService userService;
    private final FilePictureUpload filePictureUpload;
    private final UrlPictureUpload urlPictureUpload;
    private final CacheService cacheService;
    private final CosManager cosManager;
    private final SpaceService spaceService;
    private TransactionTemplate transactionTemplate;

    private static final TypeReference<Page<PictureVO>> PICTURE_VO_PAGE_TYPE = new TypeReference<>() {};

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        // 如果传递了 url，才校验
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 上传图片
     *
     * @param inputSource          文件源
     * @param pictureUploadRequest 图片上传请求参数
     * @param loginUser            当前登录用户
     * @return 上传后的图片信息
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 校验空间是否存在
        var ref = new Object() {
            Long spaceId = pictureUploadRequest.getSpaceId();
        };
        if (ref.spaceId != null) {
            // 校验空间是否存在
            Space space = spaceService.getById(ref.spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在");
            // 校验空间权限
            spaceService.checkSpaceAuth(loginUser, space);
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间容量不足");
            }
        }
        // 校验是否有空间的权限
        // 判断新增还是更新
        Long pictureId;
        pictureId = pictureUploadRequest.getId();
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture picture = this.getById(pictureId);
            ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR, "图片不存在");
            // 仅管理员或本人可更新
            if (!userService.isAdmin(loginUser) && !picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 校验空间是否一致
            if (ref.spaceId == null) {
                if (picture.getSpaceId() != null) {
                    ref.spaceId = picture.getSpaceId();
                }
            } else {
                if (ObjectUtil.notEqual(ref.spaceId, picture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间不一致");
                }
            }
        }
        // 上传图片,得到图片信息
        // 按照用户ID创建文件夹，避免不同用户上传的图片重名 -> 按照空间划分目录
        String uploadPathPrefix;
        if (ref.spaceId != null) {
            // 特殊空间
            uploadPathPrefix = String.format("space/%s", ref.spaceId);
        } else {
            // 公共空间
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }
        // 根据输入源类型选择上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        var uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造Picture对象
        Picture picture = new Picture();
        // 支持外层传递的名称
        String picName = uploadPictureResult.getPicName();
        if (StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setSpaceId(ref.spaceId);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        picture.setUserId(loginUser.getId());
        picture.setPicColor(uploadPictureResult.getPicColor());
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        if (pictureId != null) {
            // 更新
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 开启事务
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (ref.spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, ref.spaceId)
                        .setSql("totalCount = totalCount + 1, totalSize = totalSize + " + picture.getPicSize())
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "空间使用额度更新失败");
            }
            return null;
        });
        return PictureVO.objToVo(picture);
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest request,
                                                        HttpServletRequest httpRequest,
                                                        CacheType cacheType) {
        String queryCondition = JSONUtil.toJsonStr(request);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes(StandardCharsets.UTF_8));
        String cacheKey = "yanPicture:listPictureVOByPage:" + hashKey;
        long current = request.getCurrent();
        long size = request.getPageSize();

        return cacheService.getOrLoad(cacheKey, cacheType, Duration.ofMinutes(1), Duration.ofMinutes(5),
                PICTURE_VO_PAGE_TYPE, () -> {
                    Page<Picture> picturePage = this.page(new Page<>(current, size), getQueryWrapper(request));
                    return getPictureVOPage(picturePage, httpRequest);
                });
    }


    /**
     * 获取QueryWrapper对象
     *
     * @param pictureQueryRequest 图片查询请求对象
     * @return QueryWrapper对象
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%xxx%" or introduction like "%xxx%")
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >= startEditTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < endEditTime
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            /* and (tag like "%\"Java\"%" and like "%\"Python\"%") */
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 编辑图片
     *
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        this.checkPictureAuth(oldPicture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        this.checkPictureAuth(oldPicture, loginUser);
        // 开启事务
        transactionTemplate.execute(status -> {
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片删除失败");
            // 更新空间的使用额度,释放额度
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, oldPicture.getSpaceId())
                    .setSql("total_count = total_count - 1, total_size = total_size - " + oldPicture.getPicSize())
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return null;
        });
        // 删除图片文件
        this.clearPictureFile(oldPicture);
    }

    @Override
    public PictureCursorVO listPictureVOByCursor(PictureCursorQueryRequest request, HttpServletRequest httpRequest) {
        int size = request.getSize();
        ThrowUtils.throwIf(size <= 0 || size > 20, ErrorCode.PARAMS_ERROR, "每次获取数量需在 1-20 之间");
        // 排序由游标接口自己控制，忽略前端传入的排序字段
        request.setSortField(null);
        QueryWrapper<Picture> queryWrapper = getQueryWrapper(request);
        Long lastCreateTime = request.getLastCreateTime();
        Long lastId = request.getLastId();
        if (lastCreateTime != null && lastId != null) {
            Date lastTime = new Date(lastCreateTime);
            // (createTime, id) < (lastCreateTime, lastId)
            queryWrapper.and(qw -> qw.lt("createTime", lastTime)
                    .or(inner -> inner.eq("createTime", lastTime).lt("id", lastId)));
        }
        // createTime 可能重复，加上 id 保证排序唯一
        queryWrapper.orderByDesc("createTime", "id");
        // 多查一条用来判断是否还有下一页，避免 count 查询
        queryWrapper.last("limit " + (size + 1));
        List<Picture> pictureList = this.list(queryWrapper);
        boolean hasMore = pictureList.size() > size;
        if (hasMore) {
            pictureList = new ArrayList<>(pictureList.subList(0, size));
        }
        Page<Picture> page = new Page<>(1, size, 0);
        page.setRecords(pictureList);
        PictureCursorVO result = new PictureCursorVO();
        result.setRecords(getPictureVOPage(page, httpRequest).getRecords());
        result.setHasMore(hasMore);
        if (!pictureList.isEmpty()) {
            Picture last = pictureList.get(pictureList.size() - 1);
            result.setNextCreateTime(last.getCreateTime().getTime());
            result.setNextId(last.getId());
        }
        return result;
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求对象
     * @param loginUser            登录用户
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");
        Long id = pictureReviewRequest.getId();
        ;
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.PARAMS_ERROR, "图片不存在");
        // 判断是否已经审核过
        if (oldPicture.getReviewStatus().equals(reviewStatus))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核!");
        // 数据库操作
        Picture updatePicture = new Picture();
        updatePicture.setId(id);
        updatePicture.setReviewStatus(reviewStatus);
        updatePicture.setReviewMessage(reviewMessage);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片审核失败");
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员上传的图片直接通过审核
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动通过");
        } else {
            // 普通用户上传的图片需要审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count >= 30, ErrorCode.PARAMS_ERROR, "批量导入数量不能超过30");
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("抓取图片失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "抓取图片失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        // 遍历元素上传图片
        int uploadCount = 0;
        for (Element element : imgElementList) {
            String fileUrl = element.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("第 {} 张图片没有 src 属性，跳过", uploadCount + 1);
                continue;
            }
            // 处理图片地址，防止转译或者和对象存储冲突的问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex != -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            try {
                // 上传图片
                PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
                pictureUploadRequest.setFileUrl(fileUrl);
                String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
                if (StrUtil.isNotBlank(namePrefix)) {
                    pictureUploadRequest.setPicName(namePrefix + "_" + (uploadCount + 1));
                } else {
                    pictureUploadRequest.setPicName(searchText + "_" + (uploadCount + 1));
                }
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("第 {} 张图片上传成功，图片ID：{}", uploadCount + 1, pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("第 {} 张图片上传失败，图片地址：{}", uploadCount + 1, fileUrl, e);
                continue;
            }
            // 达到抓取数量，停止抓取
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录引用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery().eq(Picture::getUrl, pictureUrl).count();
        if (count > 1) {
            return;
        }
        // 删除图片文件
        cosManager.deleteObject(pictureUrl);
        // 删除缩略图文件
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }

    }

    /**
     * 校验空间图片的权限
     *
     * @param picture   图片对象
     * @param loginUser 登录用户对象
     */
    @Override
    public void checkPictureAuth(Picture picture, User loginUser) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共空间，只有本人或管理员可操作
            if (!userService.isAdmin(loginUser) && !picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，只有空间的拥有者或管理员可操作
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在");
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }

    /**
     * 根据颜色搜索图片
     * @param spaceId 空间 ID
     * @param picColor 颜色值
     * @param loginUser 登录用户
     * @return 搜索到的图片列表
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR, "颜色值不能为空");
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR, "空间ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 校验空间是否存在和具有权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在");
        spaceService.checkSpaceAuth(loginUser, space);
        // 3. 查询图片
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        if (CollUtil.isEmpty(pictureList)) {
            return List.of();
        }
        // 将颜色字符串转为 color 对象进行比较
        Color targetColor = Color.decode(picColor);
        // 4. 根据颜色相似度进行筛选
        return pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    if (StrUtil.isBlank(picture.getPicColor())) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(picture.getPicColor());
                    return -ColorSimilarUtil.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(20) // 限制返回数量为 20
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest 批量编辑图片请求对象
     * @param loginUser                 登录用户对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1.获取和校验参数
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureEditByBatchRequest.getPictureIdList()), ErrorCode.PARAMS_ERROR, "图片ID列表为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2.校验空间权限
        Space space = spaceService.getById(pictureEditByBatchRequest.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在");
        spaceService.checkSpaceAuth(loginUser, space);
        // 3.查询指定图片(仅选择需要的字段)
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId,Picture::getSpaceId)
                .eq(Picture::getSpaceId, pictureEditByBatchRequest.getSpaceId())
                .in(Picture::getId, pictureEditByBatchRequest.getPictureIdList())
                .list();
        if (CollUtil.isEmpty(pictureList)) {
            return;
        }
        // 4.更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(pictureEditByBatchRequest.getCategory())) {
                picture.setCategory(pictureEditByBatchRequest.getCategory());
            }
            if (CollUtil.isNotEmpty(pictureEditByBatchRequest.getTags())) {
                picture.setTags(JSONUtil.toJsonStr(pictureEditByBatchRequest.getTags()));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureNameByRule(pictureList, nameRule);
        // 5.操作数据库更新
        boolean isSuccess = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!isSuccess, ErrorCode.OPERATION_ERROR, "批量编辑图片失败");
    }

    /**
     * nameRule 规则说明：图片{序号}
     */
    private void fillPictureNameByRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        try {
            for (int i = 0; i < pictureList.size(); i++) {
                Picture picture = pictureList.get(i);
                String newName = nameRule.replaceAll("\\{序号}", String.valueOf(i + 1));
                picture.setName(newName);
            }
        } catch (Exception e) {
            log.error("批量重命名图片失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量重命名图片失败");
        }
    }
}
