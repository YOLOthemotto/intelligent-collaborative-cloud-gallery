package com.ym.intelligentcollaborativecloudgallery.api.aliyunai;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ym.intelligentcollaborativecloudgallery.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.ym.intelligentcollaborativecloudgallery.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.model.dto.picture.CreatePictureOutPaintingTaskRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AliYunAiApiTest {
    private CreateOutPaintingTaskRequest request() {
        CreateOutPaintingTaskRequest request = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl("https://example.com/image.jpg");
        request.setInput(input);
        return request;
    }

    @Test
    void translatesScalesAndUsesImageEditContract() {
        CreateOutPaintingTaskRequest request = request();
        CreateOutPaintingTaskRequest.Parameters parameters = new CreateOutPaintingTaskRequest.Parameters();
        parameters.setXScale(1.5f);
        parameters.setYScale(2.0f);
        request.setParameters(parameters);
        JSONObject body = AliYunAiApi.buildRequest(request);
        assertEquals("wanx2.1-imageedit", body.getStr("model"));
        assertEquals("expand", body.getJSONObject("input").getStr("function"));
        assertEquals("https://example.com/image.jpg", body.getJSONObject("input").getStr("base_image_url"));
        assertNotNull(body.getJSONObject("input").getStr("prompt"));
        JSONObject options = body.getJSONObject("parameters");
        assertEquals(1.5f, options.getFloat("left_scale"));
        assertEquals(1.5f, options.getFloat("right_scale"));
        assertEquals(2.0f, options.getFloat("top_scale"));
        assertEquals(2.0f, options.getFloat("bottom_scale"));
        assertEquals(1, options.getInt("n"));
    }

    @Test
    void rejectsUnsupportedOrOutOfRangeParameters() {
        CreateOutPaintingTaskRequest request = request();
        CreateOutPaintingTaskRequest.Parameters parameters = new CreateOutPaintingTaskRequest.Parameters();
        request.setParameters(parameters);
        parameters.setXScale(2.1f);
        assertThrows(BusinessException.class, () -> AliYunAiApi.buildRequest(request));
        parameters.setXScale(Float.NaN);
        assertThrows(BusinessException.class, () -> AliYunAiApi.buildRequest(request));
        parameters.setXScale(1f);
        parameters.setTopOffset(10);
        assertThrows(BusinessException.class, () -> AliYunAiApi.buildRequest(request));
    }

    @Test
    void acceptsFrontendCamelCaseParameters() throws Exception {
        CreatePictureOutPaintingTaskRequest request = new ObjectMapper().readValue(
                "{\"pictureId\":1,\"parameters\":{\"xScale\":1.5,\"yScale\":2.0}}",
                CreatePictureOutPaintingTaskRequest.class);
        assertEquals(1.5f, request.getParameters().getXScale());
        assertEquals(2.0f, request.getParameters().getYScale());
    }

    @Test
    void adaptsAsyncResultsAndPreservesFailure() {
        assertEquals("abc", AliYunAiApi.parseCreateResponse("{\"request_id\":\"r\",\"output\":{\"task_id\":\"abc\",\"task_status\":\"PENDING\"}}").getOutput().getTaskId());
        GetOutPaintingTaskResponse result = AliYunAiApi.parseTaskResponse(
                "{\"output\":{\"task_id\":\"abc\",\"task_status\":\"SUCCEEDED\",\"results\":[{\"url\":\"https://example.com/result.png\"}],\"task_metrics\":{\"TOTAL\":1,\"SUCCEEDED\":1,\"FAILED\":0}}}");
        assertEquals("https://example.com/result.png", result.getOutput().getOutputImageUrl());
        assertEquals(1, result.getOutput().getTaskMetrics().getSucceeded());
        result = AliYunAiApi.parseTaskResponse("{\"output\":{\"task_id\":\"abc\",\"task_status\":\"FAILED\",\"code\":\"InvalidParameter\",\"message\":\"bad image\"}}");
        assertEquals("FAILED", result.getOutput().getTaskStatus());
        assertEquals("bad image", result.getOutput().getMessage());
        assertThrows(BusinessException.class, () -> AliYunAiApi.parseTaskResponse("{\"code\":\"InvalidApiKey\",\"message\":\"bad key\"}"));
        assertThrows(BusinessException.class, () -> AliYunAiApi.parseTaskResponse("{\"output\":{\"task_id\":\"abc\",\"task_status\":\"SUCCEEDED\",\"results\":[]}}"));
    }
}
