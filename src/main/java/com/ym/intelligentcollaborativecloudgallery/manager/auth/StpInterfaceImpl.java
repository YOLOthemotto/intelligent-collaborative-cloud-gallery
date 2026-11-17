package com.ym.intelligentcollaborativecloudgallery.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义权限加载接口实现类
 */
@Component
public class StpInterfaceImpl implements StpInterface {
	@Value("${server.servlet.context-path}")
	private String contextPath;

	/**
	 * 返回一个账号所拥有的权限码集合 
	 */
	@Override
	public List<String> getPermissionList(Object loginId, String loginType) {

		return new ArrayList<>();
	}

	/**
	 * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
	 */
	@Override
	public List<String> getRoleList(Object loginId, String loginType) {
		return new ArrayList<>();
	}

	/**
	 * 从请求中获取上下文对象
	 */
	private SpaceUserAuthContext getAuthContextByRequest() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
		SpaceUserAuthContext authRequest;
		// 获取请求参数
		if (ContentType.JSON.getValue().equals(contentType)) {
			String body = ServletUtil.getBody(request);
			authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
		} else {
			Map<String, String> paramMap = ServletUtil.getParamMap(request);
			authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
		}
		// 根据请求路径区分 id 字段的含义
		Long id = authRequest.getId();
		if (ObjUtil.isNotNull(id)) {
			// 获取到请求路径的业务前缀，/api/picture/aaa?a=1
			String requestURI = request.getRequestURI();
			// 先替换掉上下文，剩下的就是前缀
			String partURI = requestURI.replace(contextPath + "/", "");
			// 获取前缀的第一个斜杠前的字符串
			String moduleName = StrUtil.subBefore(partURI, "/", false);
			switch (moduleName) {
				case "picture":
					authRequest.setPictureId(id);
					break;
				case "spaceUser":
					authRequest.setSpaceUserId(id);
					break;
				case "space":
					authRequest.setSpaceId(id);
					break;
				default:
			}
		}
		return authRequest;
	}


}