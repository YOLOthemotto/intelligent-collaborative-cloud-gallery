package com.ym.intelligentcollaborativecloudgallery.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.exception.ThrowUtils;
import com.ym.intelligentcollaborativecloudgallery.mapper.SpaceUserMapper;
import com.ym.intelligentcollaborativecloudgallery.model.dto.spaceuser.SpaceUserAddRequest;
import com.ym.intelligentcollaborativecloudgallery.model.dto.spaceuser.SpaceUserCandidateQueryRequest;
import com.ym.intelligentcollaborativecloudgallery.model.dto.spaceuser.SpaceUserQueryRequest;
import com.ym.intelligentcollaborativecloudgallery.model.entity.Space;
import com.ym.intelligentcollaborativecloudgallery.model.entity.SpaceUser;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.enums.SpaceRoleEnum;
import com.ym.intelligentcollaborativecloudgallery.model.enums.UserRoleEnum;
import com.ym.intelligentcollaborativecloudgallery.model.vo.SpaceUserCandidateVO;
import com.ym.intelligentcollaborativecloudgallery.model.vo.SpaceUserVO;
import com.ym.intelligentcollaborativecloudgallery.model.vo.SpaceVO;
import com.ym.intelligentcollaborativecloudgallery.model.vo.UserVO;
import com.ym.intelligentcollaborativecloudgallery.service.SpaceService;
import com.ym.intelligentcollaborativecloudgallery.service.SpaceUserService;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
        implements SpaceUserService {

    private static final int MAX_CANDIDATE_USER_LIMIT = 50;

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private SpaceService spaceService;

    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        // 数据库操作
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }

    @Override
    public List<SpaceUserCandidateVO> listCandidateUsers(SpaceUserCandidateQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Integer limit = queryRequest.getLimit() == null ? 20 : queryRequest.getLimit();
        ThrowUtils.throwIf(limit < 1 || limit > MAX_CANDIDATE_USER_LIMIT, ErrorCode.PARAMS_ERROR,
                "返回数量必须在 1 到 " + MAX_CANDIDATE_USER_LIMIT + " 之间");
        String userName = queryRequest.getUserName();
        ThrowUtils.throwIf(userName != null && userName.length() > 256, ErrorCode.PARAMS_ERROR,
                "用户昵称长度不能超过 256 位");
        Long spaceId = queryRequest.getSpaceId();
        ThrowUtils.throwIf(spaceId != null && spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 ID 不合法");

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "userName", "userAvatar")
                .like(StrUtil.isNotBlank(userName), "userName", userName)
                .eq("isDelete", 0)
                .ne("userRole", UserRoleEnum.BAN.getValue())
                .apply(spaceId != null,
                        "id NOT IN (SELECT userId FROM space_user WHERE spaceId = {0})", spaceId)
                .orderByAsc("userName")
                .orderByAsc("id");

        Page<User> userPage = userService.page(new Page<>(1, limit), queryWrapper);
        return userPage.getRecords().stream().map(user -> {
            SpaceUserCandidateVO candidate = new SpaceUserCandidateVO();
            candidate.setId(user.getId());
            candidate.setUserName(user.getUserName());
            candidate.setUserAvatar(user.getUserAvatar());
            return candidate;
        }).collect(Collectors.toList());
    }

    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        // 创建时，空间 id 和用户 id 必填
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if (spaceRole != null && spaceRoleEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }

    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request) {
        // 对象转封装类
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        // 关联查询用户信息
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceUserVO.setUser(userVO);
        }
        // 关联查询空间信息
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
            spaceUserVO.setSpace(spaceVO);
        }
        return spaceUserVO;
    }

    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        // 判断输入列表是否为空
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        // 对象列表 => 封装对象列表
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream().map(SpaceUserVO::objToVo).collect(Collectors.toList());
        // 1. 收集需要关联查询的用户 ID 和空间 ID
        Set<Long> userIdSet = spaceUserList.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        // 2. 批量查询用户和空间
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceService.listByIds(spaceIdSet).stream()
                .collect(Collectors.groupingBy(Space::getId));
        // 3. 填充 SpaceUserVO 的用户和空间信息
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();
            // 填充用户信息
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceUserVO.setUser(userService.getUserVO(user));
            // 填充空间信息
            Space space = null;
            if (spaceIdSpaceListMap.containsKey(spaceId)) {
                space = spaceIdSpaceListMap.get(spaceId).get(0);
            }
            spaceUserVO.setSpace(SpaceVO.objToVo(space));
        });
        return spaceUserVOList;
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);
        return queryWrapper;
    }
}

