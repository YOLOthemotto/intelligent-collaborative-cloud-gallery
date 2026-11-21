package com.ym.intelligentcollaborativecloudgallery.manager.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端维护的图片权威编辑状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditState {

    private int rotation;

    private double zoom;

    private int version;
}
