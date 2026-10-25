package com.ym.intelligentcollaborativecloudgallery.model.dto.user;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求
 */
@Data
public class UserRegisterRequest implements Serializable {

    private final static long serialVersionUID = 1L;

    @ApiModelProperty(value = "用户账号", required = true)
    private String userAccount;

    @ApiModelProperty(value = "用户密码", required = true)
    private String userPassword;

    @ApiModelProperty(value = "确认密码", required = true)
    private String checkPassword;
}
