package com.ym.intelligentcollaborativecloudgallery.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
public enum UserRoleEnum {
    /**
     * 普通用户
     */
    USER("用户", "user"),
    /**
     * 管理员
     */
    ADMIN("管理员", "admin");

    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据值获取枚举
     * @param value 值
     * @return 枚举对象
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjectUtil.isNull(value)) {
            return null;
        }
        for (UserRoleEnum role : UserRoleEnum.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        return null;
    }
}
