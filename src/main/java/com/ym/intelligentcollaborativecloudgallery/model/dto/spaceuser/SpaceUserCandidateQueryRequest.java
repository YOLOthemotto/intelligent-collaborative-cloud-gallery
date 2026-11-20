package com.ym.intelligentcollaborativecloudgallery.model.dto.spaceuser;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 空间成员候选用户查询请求
 */
@Data
public class SpaceUserCandidateQueryRequest implements Serializable {

    @ApiModelProperty(value = "用户昵称")
    private String userName;

    @ApiModelProperty(value = "空间 ID，传入后排除该空间已有成员")
    private Long spaceId;

    @ApiModelProperty(value = "返回数量，默认 20，最大 50")
    private Integer limit = 20;

    private static final long serialVersionUID = 1L;
}
