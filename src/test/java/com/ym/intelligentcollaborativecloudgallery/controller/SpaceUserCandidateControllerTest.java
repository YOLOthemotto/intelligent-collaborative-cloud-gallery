package com.ym.intelligentcollaborativecloudgallery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ym.intelligentcollaborativecloudgallery.config.JsonConfig;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.exception.ErrorCode;
import com.ym.intelligentcollaborativecloudgallery.exception.GlobalExceptionHandler;
import com.ym.intelligentcollaborativecloudgallery.model.dto.spaceuser.SpaceUserCandidateQueryRequest;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.vo.SpaceUserCandidateVO;
import com.ym.intelligentcollaborativecloudgallery.service.SpaceUserService;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaceUserCandidateControllerTest {

    private final SpaceUserService spaceUserService = mock(SpaceUserService.class);
    private final UserService userService = mock(UserService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SpaceUserController controller = new SpaceUserController();
        ReflectionTestUtils.setField(controller, "spaceUserService", spaceUserService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ObjectMapper objectMapper =
                new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void returnsCandidateContractForLoggedInUser() throws Exception {
        when(userService.getLoginUser(any(HttpServletRequest.class))).thenReturn(new User());
        SpaceUserCandidateVO candidate = new SpaceUserCandidateVO();
        candidate.setId(9007199254740993L);
        candidate.setUserName("Alice");
        candidate.setUserAvatar("https://example.test/avatar.png");
        when(spaceUserService.listCandidateUsers(any(SpaceUserCandidateQueryRequest.class)))
                .thenReturn(List.of(candidate));

        mockMvc.perform(post("/spaceUser/list/candidate")
                        .contentType("application/json")
                        .content("{\"userName\":\"Ali\",\"spaceId\":42,\"limit\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.data[0].userName").value("Alice"))
                .andExpect(jsonPath("$.data[0].userAvatar").value("https://example.test/avatar.png"))
                .andExpect(jsonPath("$.data[0].userAccount").doesNotExist())
                .andExpect(jsonPath("$.data[0].userProfile").doesNotExist())
                .andExpect(jsonPath("$.data[0].userRole").doesNotExist());

        verify(userService).getLoginUser(any(HttpServletRequest.class));
        verify(spaceUserService).listCandidateUsers(any(SpaceUserCandidateQueryRequest.class));
    }

    @Test
    void rejectsRequestBeforeQueryingCandidatesWhenNotLoggedIn() throws Exception {
        when(userService.getLoginUser(any(HttpServletRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));

        mockMvc.perform(post("/spaceUser/list/candidate")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));

        verify(spaceUserService, never())
                .listCandidateUsers(any(SpaceUserCandidateQueryRequest.class));
    }
}
