package com.ym.intelligentcollaborativecloudgallery.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 空间成员候选用户
 */
@Data
public class SpaceUserCandidateVO implements Serializable {

    @ApiModelProperty(value = "用户 ID")
    private Long id;

    @ApiModelProperty(value = "用户昵称")
    private String userName;

    @ApiModelProperty(value = "用户头像")
    private String userAvatar;

    private static final long serialVersionUID = 1L;
}
