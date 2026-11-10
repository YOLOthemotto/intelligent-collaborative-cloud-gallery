package com.ym.intelligentcollaborativecloudgallery.api.imageSerach;

import com.ym.intelligentcollaborativecloudgallery.api.imageSerach.model.ImageSearchResult;
import com.ym.intelligentcollaborativecloudgallery.api.imageSerach.sub.GetImageFirstUrlApi;
import com.ym.intelligentcollaborativecloudgallery.api.imageSerach.sub.GetImageListApi;
import com.ym.intelligentcollaborativecloudgallery.api.imageSerach.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        return GetImageListApi.getImageList(imageFirstUrl);
    }

    public static void main(String[] args) {
        List<ImageSearchResult> imageList = searchImage("https://yan-1308992853.cos.ap-chongqing.myqcloud.com/space/2118985771569111042/2026-11-08_9iXS0oRlnrY1gGRH.webp");
        System.out.println("结果列表" + imageList);
    }
}
