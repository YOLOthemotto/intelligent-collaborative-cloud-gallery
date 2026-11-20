package com.ym.intelligentcollaborativecloudgallery.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ym.intelligentcollaborativecloudgallery.config.JsonConfig;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.model.dto.spaceuser.SpaceUserCandidateQueryRequest;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.vo.SpaceUserCandidateVO;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceUserCandidateServiceTest {

    private final UserService userService = mock(UserService.class);
    private SpaceUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpaceUserServiceImpl();
        ReflectionTestUtils.setField(service, "userService", userService);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void queriesOnlyDropdownFieldsAndExcludesDisabledDeletedAndExistingMembers() throws Exception {
        User user = new User();
        user.setId(9007199254740993L);
        user.setUserName("Alice");
        user.setUserAvatar("https://example.test/avatar.png");
        user.setUserAccount("private-account");
        user.setUserProfile("private-profile");
        user.setUserRole("user");
        Page<User> result = new Page<>(1, 5);
        result.setRecords(List.of(user));
        when(userService.page(any(Page.class), any(Wrapper.class))).thenReturn(result);

        SpaceUserCandidateQueryRequest request = new SpaceUserCandidateQueryRequest();
        request.setUserName("Ali");
        request.setSpaceId(42L);
        request.setLimit(5);

        List<SpaceUserCandidateVO> candidates = service.listCandidateUsers(request);

        ArgumentCaptor<Wrapper<User>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userService).page(any(Page.class), wrapperCaptor.capture());
        Wrapper<User> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("userName LIKE"));
        assertTrue(sqlSegment.contains("userRole <>"));
        assertTrue(sqlSegment.contains("isDelete ="));
        assertTrue(sqlSegment.contains("id NOT IN (SELECT userId FROM space_user WHERE spaceId ="));
        assertTrue(wrapper.getSqlSelect().contains("id"));
        assertTrue(wrapper.getSqlSelect().contains("userName"));
        assertTrue(wrapper.getSqlSelect().contains("userAvatar"));
        assertEquals(3, wrapper.getSqlSelect().split(",").length);

        assertEquals(1, candidates.size());
        ObjectMapper objectMapper = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        JsonNode json = objectMapper.valueToTree(candidates.get(0));
        assertEquals("9007199254740993", json.get("id").asText());
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "userName", "userAvatar"), fields);
    }

    @Test
    void validatesLimitAndSpaceId() {
        SpaceUserCandidateQueryRequest invalidLimit = new SpaceUserCandidateQueryRequest();
        invalidLimit.setLimit(51);
        assertThrows(BusinessException.class, () -> service.listCandidateUsers(invalidLimit));

        SpaceUserCandidateQueryRequest invalidSpaceId = new SpaceUserCandidateQueryRequest();
        invalidSpaceId.setSpaceId(0L);
        assertThrows(BusinessException.class, () -> service.listCandidateUsers(invalidSpaceId));
    }
}
