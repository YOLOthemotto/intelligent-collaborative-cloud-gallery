package com.ym.intelligentcollaborativecloudgallery.service.impl;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ym.intelligentcollaborativecloudgallery.constant.UserConstant;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.model.dto.user.UserLoginRequest;
import com.ym.intelligentcollaborativecloudgallery.model.dto.user.UserQueryRequest;
import com.ym.intelligentcollaborativecloudgallery.model.enums.UserRoleEnum;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.exception.ThrowUtils;
import com.ym.intelligentcollaborativecloudgallery.model.dto.user.UserRegisterRequest;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.vo.LoginUserVO;
import com.ym.intelligentcollaborativecloudgallery.model.vo.UserVO;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import com.ym.intelligentcollaborativecloudgallery.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yancenxin
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-10-24 21:37:47
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * 用户注册
     *
     * @param registerRequest 用户注册请求
     * @return 新用户id
     */
    @Override
    public long userRegister(UserRegisterRequest registerRequest) {
        // 1. 校验用户注册请求参数
        ThrowUtils.throwIf(StrUtil.hasBlank(registerRequest.getUserAccount(), registerRequest.getUserPassword(), registerRequest.getCheckPassword()), ErrorCode.PARAMS_ERROR, "注册请求参数不能为空");
        //用户账号长度校验
        ThrowUtils.throwIf(registerRequest.getUserAccount().length() < 4, ErrorCode.PARAMS_ERROR, "用户账号长度不能小于4位");
        //用户密码长度校验
        ThrowUtils.throwIf(registerRequest.getUserPassword().length() < 8, ErrorCode.PARAMS_ERROR, "用户密码长度不能小于8位");
        //确认密码长度校验
        ThrowUtils.throwIf(registerRequest.getCheckPassword().length() < 8, ErrorCode.PARAMS_ERROR, "确认密码长度不能小于8位");
        //用户密码和确认密码是否一致校验
        ThrowUtils.throwIf(!registerRequest.getUserPassword().equals(registerRequest.getCheckPassword()), ErrorCode.PARAMS_ERROR, "用户密码和确认密码不一致");
        // 2. 校验用户账号是否已存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", registerRequest.getUserAccount());
        long count = this.baseMapper.selectCount(queryWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "用户账号已存在");
        // 3. 密码加密
        String encryptedPassword = getEncryptedPassword(registerRequest.getUserPassword());
        // 4. 保存用户信息到数据库
        User user = new User();
        user.setUserAccount(registerRequest.getUserAccount());
        user.setUserPassword(encryptedPassword);
        user.setUserName("user_" + System.currentTimeMillis());
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "用户注册失败");
        return user.getId();
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录请求
     * @param request HttpServletRequest对象
     * @return 登录成功的脱敏后用户信息
     */
    @Override
    public LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 1. 校验用户登录请求参数
        ThrowUtils.throwIf(StrUtil.hasBlank(userLoginRequest.getUserAccount(), userLoginRequest.getUserPassword()), ErrorCode.PARAMS_ERROR, "登录请求参数不能为空");
        // 2. 对用户密码进行加密
        String encryptedPassword = getEncryptedPassword(userLoginRequest.getUserPassword());
        // 3. 查询用户信息
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userLoginRequest.getUserAccount());
        queryWrapper.eq("userPassword", encryptedPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 4. 校验用户信息是否存在
        if (user == null) {
            log.error("用户登录失败，用户账号或密码错误，userAccount={}", userLoginRequest.getUserAccount());
        }
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR, "用户账号或密码错误");
        // 5. 保存用户登录态到 session
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        // 6. 返回脱敏后的用户信息
        return createLoginUserVO(user);
    }

    /**
     * 获取当前登录用户信息
     *
     * @param request HttpServletRequest对象
     * @return 当前登录的用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        if (userObj instanceof User) {
            User currentUser = (User) userObj;
            // 从数据库中查询最新的用户信息
            User user = this.getById(currentUser.getId());
            if (user == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
            }
            return user;
        }
        return null;
    }

    /**
     * 用户注销
     *
     * @param request HttpServletRequest对象
     * @return 注销结果
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    @Override
    public LoginUserVO createLoginUserVO(User user) {
        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setId(user.getId());
        loginUserVO.setUserAccount(user.getUserAccount());
        loginUserVO.setUserName(user.getUserName());
        loginUserVO.setUserAvatar(user.getUserAvatar());
        loginUserVO.setUserProfile(user.getUserProfile());
        loginUserVO.setUserRole(user.getUserRole());
        loginUserVO.setEditTime(user.getEditTime());
        loginUserVO.setCreateTime(user.getCreateTime());
        loginUserVO.setUpdateTime(user.getUpdateTime());
        loginUserVO.setVipExpireTime(user.getVipExpireTime());
        loginUserVO.setVipCode(user.getVipCode());
        loginUserVO.setVipNumber(user.getVipNumber());
        return loginUserVO;
    }

    @Override
    public String getEncryptedPassword(String userPassword) {
        // 加盐，这里使用固定的盐值，实际应用中可以使用随机盐值并存储在数据库中
        String salt = "randomSaltValue";
        // 返回加密后的密码
        return DigestUtils.md5DigestAsHex((userPassword + salt).getBytes());
    }

    /**
     * 获取脱敏后的用户信息
     *
     * @param user 用户实体对象
     * @return 脱敏后的用户信息
     */
    @Override
    public UserVO getUserVO(User user) {
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUserAccount(user.getUserAccount());
        userVO.setUserName(user.getUserName());
        userVO.setUserAvatar(user.getUserAvatar());
        userVO.setUserProfile(user.getUserProfile());
        userVO.setUserRole(user.getUserRole());
        userVO.setCreateTime(user.getCreateTime());
        userVO.setVipExpireTime(user.getVipExpireTime());
        userVO.setVipCode(user.getVipCode());
        userVO.setVipNumber(user.getVipNumber());
        return userVO;
    }

    /**
     * 获取脱敏后的用户信息列表
     *
     * @param userList 用户实体对象列表
     * @return 脱敏后的用户信息列表
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (userList == null || userList.isEmpty()) {
            return List.of();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    /**
     * 获取QueryWrapper对象
     *
     * @param userQueryRequest 用户查询请求对象
     * @return QueryWrapper对象
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户查询请求不能为空");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(userQueryRequest.getId()), "id", userQueryRequest.getId());
        queryWrapper.eq(StrUtil.isNotBlank(userQueryRequest.getUserRole()), "userRole", userQueryRequest.getUserRole());
        queryWrapper.like(StrUtil.isNotBlank(userQueryRequest.getUserAccount()), "userAccount", userQueryRequest.getUserAccount());
        queryWrapper.like(StrUtil.isNotBlank(userQueryRequest.getUserName()), "userName", userQueryRequest.getUserName());
        queryWrapper.like(StrUtil.isNotBlank(userQueryRequest.getUserProfile()), "userProfile", userQueryRequest.getUserProfile());
        queryWrapper.orderBy(StrUtil.isNotEmpty(userQueryRequest.getSortField()), "ascend".equals(userQueryRequest.getSortOrder()), userQueryRequest.getSortField());
        return queryWrapper;
    }
}




