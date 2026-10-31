package com.ym.intelligentcollaborativecloudgallery.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ym.intelligentcollaborativecloudgallery.model.dto.user.UserLoginRequest;
import com.ym.intelligentcollaborativecloudgallery.model.dto.user.UserQueryRequest;
import com.ym.intelligentcollaborativecloudgallery.model.dto.user.UserRegisterRequest;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.vo.LoginUserVO;
import com.ym.intelligentcollaborativecloudgallery.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author yancenxin
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-10-24 21:37:47
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param registerRequest 用户注册请求
     * @return 新用户id
     */
    long userRegister(UserRegisterRequest registerRequest);

    /**
     * 用户登录
     * @param userLoginRequest 用户登录请求
     * @return 登录成功的脱敏后用户信息
     */
    LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取当前登录用户信息
     * @param request HttpServletRequest对象
     * @return 当前登录的用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     * @param request HttpServletRequest对象
     * @return 注销结果
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 创建登录用户信息对象
     * @param user 用户实体对象
     * @return 登录用户信息对象
     */
    LoginUserVO createLoginUserVO(User user);

    /**
     * 密码加密
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    String getEncryptedPassword(String userPassword);

    /**
     * 获取脱敏后的用户信息
     * @param user 用户实体对象
     * @return 脱敏后的用户信息
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏后的用户信息列表
     * @param userList 用户实体对象列表
     * @return 脱敏后的用户信息列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取QueryWrapper对象
     * @param userQueryRequest 用户查询请求对象
     * @return QueryWrapper对象
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 判断当前用户是否为管理员
     * @param user 用户实体对象
     * @return 是否为管理员
     */
    boolean isAdmin(User user);

}
