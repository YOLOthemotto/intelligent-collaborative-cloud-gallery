package com.ym.intelligentcollaborativecloudgallery.manager.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前图片编辑者的公开身份信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditEditor {

    private Long id;

    private String userName;

    private String userAccount;
}
