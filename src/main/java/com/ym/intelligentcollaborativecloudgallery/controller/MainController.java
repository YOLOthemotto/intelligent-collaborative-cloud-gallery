package com.ym.intelligentcollaborativecloudgallery.controller;

import com.ym.intelligentcollaborativecloudgallery.common.BaseResponse;
import com.ym.intelligentcollaborativecloudgallery.common.ResultUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "健康检查", description = "主控制器")
@RestController
@RequestMapping("/")
public class MainController {
    @GetMapping("/health")
    @ApiOperation(value = "健康检查接口")
    public BaseResponse<String> health() {
        return ResultUtils.success("服务正常");
    }
}
