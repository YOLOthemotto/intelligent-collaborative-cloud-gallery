package com.ym.intelligentcollaborativecloudgallery.aop;

import com.ym.intelligentcollaborativecloudgallery.annotation.AuthCheck;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.enums.UserRoleEnum;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;

    /**
     * 环绕通知，拦截带有 @AuthCheck 注解的方法
     * @param joinPoint 切入点
     * @param authCheck 注解
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("@annotation(authCheck)")
    public Object doIntercept(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "无法获取请求上下文");
        }
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 如果没有指定必须的角色，直接执行方法
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 必须有权限才能执行方法
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问");
        }
        // 要求需要管理员权限，但当前用户不是管理员
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !userRoleEnum.equals(UserRoleEnum.ADMIN)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问");
        }
        // 执行方法
        return joinPoint.proceed();
    }
}
