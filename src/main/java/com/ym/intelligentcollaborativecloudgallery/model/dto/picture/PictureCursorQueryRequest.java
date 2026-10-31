package com.ym.intelligentcollaborativecloudgallery.model.dto.picture;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 图片游标查询请求（固定按 createTime、id 倒序）
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PictureCursorQueryRequest extends PictureQueryRequest implements Serializable {

    /**
     * 上一页最后一条的创建时间（毫秒时间戳），首次请求不传
     */
    private Long lastCreateTime;

    /**
     * 上一页最后一条的 id，首次请求不传
     */
    private Long lastId;

    /**
     * 每次获取的条数
     */
    private int size = 20;

    private static final long serialVersionUID = 1L;
}
