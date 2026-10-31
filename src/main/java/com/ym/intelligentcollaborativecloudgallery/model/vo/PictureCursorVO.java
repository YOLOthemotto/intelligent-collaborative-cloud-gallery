package com.ym.intelligentcollaborativecloudgallery.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片游标分页结果
 */
@Data
public class PictureCursorVO implements Serializable {

    private List<PictureVO> records;

    /**
     * 是否还有下一页
     */
    private boolean hasMore;

    /**
     * 下一页游标：本页最后一条的创建时间（毫秒时间戳）
     */
    private Long nextCreateTime;

    /**
     * 下一页游标：本页最后一条的 id
     */
    private Long nextId;

    private static final long serialVersionUID = 1L;
}
