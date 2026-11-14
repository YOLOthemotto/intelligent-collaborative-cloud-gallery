package com.ym.intelligentcollaborativecloudgallery.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import com.ym.intelligentcollaborativecloudgallery.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.ym.intelligentcollaborativecloudgallery.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ym.intelligentcollaborativecloudgallery.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunAiApi {

    // 读取配置文件
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";


    /**
     * 创建任务
     *
     * @param createOutPaintingTaskRequest
     * @return
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        if (createOutPaintingTaskRequest == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图参数为空");
        }
        JSONObject body = buildRequest(createOutPaintingTaskRequest);
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                // 必须开启异步处理
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(body.toString());
        // 处理响应
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            return parseCreateResponse(httpResponse.body());
        }
    }

    /**
     * 查询创建的任务结果
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId) || !taskId.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务 ID 无效");
        }
        // 处理响应
        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        try (HttpResponse httpResponse = HttpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            return parseTaskResponse(httpResponse.body());
        }
    }

    static JSONObject buildRequest(CreateOutPaintingTaskRequest request) {
        if (request.getInput() == null || StrUtil.isBlank(request.getInput().getImageUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 URL 不能为空");
        }
        CreateOutPaintingTaskRequest.Parameters parameters = request.getParameters();
        if (parameters == null) {
            parameters = new CreateOutPaintingTaskRequest.Parameters();
        }
        if ((parameters.getAngle() != null && parameters.getAngle() != 0)
                || StrUtil.isNotBlank(parameters.getOutputRatio())
                || nonZero(parameters.getTopOffset()) || nonZero(parameters.getBottomOffset())
                || nonZero(parameters.getLeftOffset()) || nonZero(parameters.getRightOffset())
                || Boolean.TRUE.equals(parameters.getBestQuality())
                || parameters.getLimitImageSize() != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前扩图模型不支持旋转、输出比例、像素偏移、最佳质量或输出大小限制");
        }
        float x = scale(parameters.getXScale());
        float y = scale(parameters.getYScale());
        JSONObject input = JSONUtil.createObj()
                .set("function", "expand")
                .set("prompt", "自然延伸原图内容，保持画面风格一致")
                .set("base_image_url", request.getInput().getImageUrl());
        JSONObject options = JSONUtil.createObj()
                .set("left_scale", x).set("right_scale", x)
                .set("top_scale", y).set("bottom_scale", y)
                .set("n", 1)
                .set("watermark", Boolean.TRUE.equals(parameters.getAddWatermark()));
        return JSONUtil.createObj().set("model", "wanx2.1-imageedit").set("input", input).set("parameters", options);
    }

    private static boolean nonZero(Integer value) {
        return value != null && value != 0;
    }

    private static float scale(Float value) {
        if (value == null) {
            return 1.0f;
        }
        if (!Float.isFinite(value) || value < 1.0f || value > 2.0f) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "扩图比例须在 1.0 至 2.0 之间");
        }
        return value;
    }

    static CreateOutPaintingTaskResponse parseCreateResponse(String body) {
        JSONObject json = JSONUtil.parseObj(body);
        checkError(json, "AI 扩图失败");
        JSONObject output = json.getJSONObject("output");
        if (output == null || StrUtil.isBlank(output.getStr("task_id")) || StrUtil.isBlank(output.getStr("task_status"))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图服务返回了无效任务信息");
        }
        CreateOutPaintingTaskResponse response = new CreateOutPaintingTaskResponse();
        CreateOutPaintingTaskResponse.Output task = new CreateOutPaintingTaskResponse.Output();
        task.setTaskId(output.getStr("task_id"));
        task.setTaskStatus(output.getStr("task_status"));
        response.setOutput(task);
        response.setRequestId(json.getStr("request_id"));
        return response;
    }

    static GetOutPaintingTaskResponse parseTaskResponse(String body) {
        JSONObject json = JSONUtil.parseObj(body);
        checkError(json, "获取任务结果失败");
        JSONObject output = json.getJSONObject("output");
        if (output == null || StrUtil.isBlank(output.getStr("task_id")) || StrUtil.isBlank(output.getStr("task_status"))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图服务返回了无效任务信息");
        }
        GetOutPaintingTaskResponse response = new GetOutPaintingTaskResponse();
        GetOutPaintingTaskResponse.Output task = new GetOutPaintingTaskResponse.Output();
        task.setTaskId(output.getStr("task_id"));
        task.setTaskStatus(output.getStr("task_status"));
        task.setSubmitTime(output.getStr("submit_time"));
        task.setScheduledTime(output.getStr("scheduled_time"));
        task.setEndTime(output.getStr("end_time"));
        task.setCode(output.getStr("code"));
        task.setMessage(output.getStr("message"));
        JSONObject metrics = output.getJSONObject("task_metrics");
        if (metrics != null) {
            GetOutPaintingTaskResponse.TaskMetrics taskMetrics = new GetOutPaintingTaskResponse.TaskMetrics();
            taskMetrics.setTotal(metrics.getInt("TOTAL"));
            taskMetrics.setSucceeded(metrics.getInt("SUCCEEDED"));
            taskMetrics.setFailed(metrics.getInt("FAILED"));
            task.setTaskMetrics(taskMetrics);
        }
        if ("SUCCEEDED".equals(task.getTaskStatus())) {
            JSONArray results = output.getJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    if (result != null && StrUtil.isNotBlank(result.getStr("url"))) {
                        task.setOutputImageUrl(result.getStr("url"));
                        break;
                    }
                }
            }
            if (StrUtil.isBlank(task.getOutputImageUrl())) {
                log.error("AI 扩图任务成功但没有图片结果，requestId={}", json.getStr("request_id"));
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图任务未返回图片");
            }
        }
        response.setOutput(task);
        response.setRequestId(json.getStr("request_id"));
        return response;
    }

    private static void checkError(JSONObject json, String message) {
        if (StrUtil.isNotBlank(json.getStr("code"))) {
            log.error("{}：code={}, message={}, requestId={}", message, json.getStr("code"), json.getStr("message"), json.getStr("request_id"));
            throw new BusinessException(ErrorCode.OPERATION_ERROR, message + "，" + json.getStr("message"));
        }
    }
}
