package com.ym.intelligentcollaborativecloudgallery.api.imageSerach.sub;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Random;

@Slf4j
public class GetImagePageUrlApi {
    /**
     * 获取以图搜图地址
     */
    public static String getImagePageUrl(String imageUrl) {
        // 1. 准备请求参数
        Map<String, Object> formData = Map.of("image", imageUrl, "tn", "pc", "from", "pc", "image_source", "PC_UPLOAD_URL");
        // 随机生成acs-token
        String acsToken = RandomUtil.randomString(1);
        // 获取当前时间戳
        long uptime = System.currentTimeMillis();
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;

        // 2. 发送请求
        try (HttpResponse httpResponse = HttpRequest.post(url).form(formData).header("acs-token", acsToken).timeout(5000).execute()) {
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 3. 解析响应
            String body = httpResponse.body();
            JSONObject result = JSONUtil.parseObj(body);
            if (!Integer.valueOf(0).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            JSONObject data = result.getJSONObject("data");
            // 对 URL 进行解码
            String rawUrl = data.getStr("url");
            String searchResultUrl = URLUtil.decode(rawUrl, "UTF-8");
            if (StrUtil.isBlank(searchResultUrl)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取以图搜图页面地址失败");
            }
            return searchResultUrl;
        } catch (Exception e) {
            log.error("获取以图搜图页面地址失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取以图搜图页面地址失败");
        }

    }

    public static void main(String[] args) {
        String imageUrl = "https://yan-1308992853.cos.ap-chongqing.myqcloud.com/space/2118985771569111042/2026-11-08_9iXS0oRlnrY1gGRH.webp";
        String searchResultUrl = getImagePageUrl(imageUrl);
        System.out.println("以图搜图页面地址: " + searchResultUrl);
    }
}

