package com.ym.intelligentcollaborativecloudgallery.manager.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditRequestMessage;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.model.vo.UserVO;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PictureEditHandlerProtocolTest {

    private final ObjectMapper objectMapper = objectMapper();
    private PictureEditHandler handler;

    @BeforeEach
    void setUp() {
        UserService userService = mock(UserService.class);
        when(userService.getUserVO(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UserVO userVO = new UserVO();
            userVO.setId(user.getId());
            userVO.setUserName(user.getUserName());
            userVO.setUserAccount(user.getUserAccount());
            return userVO;
        });
        handler = new PictureEditHandler();
        ReflectionTestUtils.setField(handler, "userService", userService);
        ReflectionTestUtils.setField(handler, "pictureEditSessionManager", new PictureEditSessionManager());
        ReflectionTestUtils.setField(handler, "objectMapper", objectMapper);
    }

    @Test
    void messagesIncludeCurrentEditorAndAuthoritativeStateForConnectAndActions() throws Exception {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        WebSocketSession aliceSession = session("alice-session", 9L, alice);
        WebSocketSession bobSession = session("bob-session", 9L, bob);

        handler.afterConnectionEstablished(aliceSession);
        JsonNode initial = lastMessage(aliceSession);
        assertEquals("INFO", initial.get("type").asText());
        assertTrue(initial.get("success").asBoolean());
        assertTrue(initial.get("editor").isNull());
        assertEquals(0, initial.get("state").get("rotation").asInt());
        assertEquals(1.0, initial.get("state").get("zoom").asDouble());
        assertEquals(0, initial.get("state").get("version").asInt());

        clearInvocations(aliceSession, bobSession);
        handler.handleEnterEditMessage(new PictureEditRequestMessage(), aliceSession, alice, 9L);
        JsonNode entered = lastMessage(aliceSession);
        assertEquals("1", entered.get("editor").get("id").asText());
        assertEquals("alice", entered.get("editor").get("userName").asText());
        assertEquals("alice@test", entered.get("editor").get("userAccount").asText());

        clearInvocations(aliceSession, bobSession);
        handler.handleEditActionMessage(
                action("ROTATE_RIGHT"), aliceSession, alice, 9L);
        JsonNode rotated = lastMessage(aliceSession);
        assertEquals(90, rotated.get("state").get("rotation").asInt());
        assertEquals(1, rotated.get("state").get("version").asInt());

        clearInvocations(aliceSession, bobSession);
        handler.afterConnectionEstablished(bobSession);
        JsonNode joined = lastMessage(bobSession);
        assertEquals("1", joined.get("editor").get("id").asText());
        assertEquals(90, joined.get("state").get("rotation").asInt());
        assertEquals(1, joined.get("state").get("version").asInt());

        clearInvocations(aliceSession, bobSession);
        handler.handleEnterEditMessage(new PictureEditRequestMessage(), bobSession, bob, 9L);
        JsonNode occupied = lastMessage(bobSession);
        assertEquals("ENTER_EDIT", occupied.get("type").asText());
        assertFalse(occupied.get("success").asBoolean());
        verify(aliceSession, never()).sendMessage(any());

        clearInvocations(aliceSession, bobSession);
        handler.handleEditActionMessage(action("ZOOM_IN"), bobSession, bob, 9L);
        JsonNode rejected = lastMessage(bobSession);
        assertEquals("ERROR", rejected.get("type").asText());
        assertFalse(rejected.get("success").asBoolean());
        assertEquals(1, rejected.get("state").get("version").asInt());
        verify(aliceSession, never()).sendMessage(any());

        clearInvocations(aliceSession, bobSession);
        handler.handleExitEditMessage(new PictureEditRequestMessage(), aliceSession, alice, 9L);
        JsonNode released = lastMessage(bobSession);
        assertTrue(released.get("success").asBoolean());
        assertTrue(released.get("editor").isNull());
        assertEquals(90, released.get("state").get("rotation").asInt());

        clearInvocations(aliceSession, bobSession);
        handler.handleEnterEditMessage(new PictureEditRequestMessage(), bobSession, bob, 9L);
        clearInvocations(aliceSession, bobSession);
        handler.handleEditActionMessage(action("ZOOM_IN"), bobSession, bob, 9L);
        JsonNode zoomed = lastMessage(aliceSession);
        assertEquals("2", zoomed.get("editor").get("id").asText());
        assertEquals(1.1, zoomed.get("state").get("zoom").asDouble());
        assertEquals(2, zoomed.get("state").get("version").asInt());
    }

    @Test
    void enterRequestIdDistinguishesTabsOwnedByTheSameUser() throws Exception {
        User user = user(7L, "same-user");
        WebSocketSession firstTab = session("first-tab", 11L, user);
        WebSocketSession secondTab = session("second-tab", 11L, user);
        handler.afterConnectionEstablished(firstTab);
        handler.afterConnectionEstablished(secondTab);

        PictureEditRequestMessage firstRequest = new PictureEditRequestMessage();
        firstRequest.setType("ENTER_EDIT");
        firstRequest.setRequestId("tab-1-enter");
        clearInvocations(firstTab, secondTab);
        handler.handleEnterEditMessage(firstRequest, firstTab, user, 11L);

        JsonNode firstSuccess = lastMessage(firstTab);
        JsonNode secondObserverBroadcast = lastMessage(secondTab);
        assertTrue(firstSuccess.get("success").asBoolean());
        assertEquals("tab-1-enter", firstSuccess.get("requestId").asText());
        assertEquals("tab-1-enter", secondObserverBroadcast.get("requestId").asText());

        PictureEditRequestMessage secondRequest = new PictureEditRequestMessage();
        secondRequest.setType("ENTER_EDIT");
        secondRequest.setRequestId("tab-2-enter");
        clearInvocations(firstTab, secondTab);
        handler.handleEnterEditMessage(secondRequest, secondTab, user, 11L);

        JsonNode secondFailure = lastMessage(secondTab);
        assertFalse(secondFailure.get("success").asBoolean());
        assertEquals("tab-2-enter", secondFailure.get("requestId").asText());
        verify(firstTab, never()).sendMessage(any());
    }

    private PictureEditRequestMessage action(String value) {
        PictureEditRequestMessage request = new PictureEditRequestMessage();
        request.setType("EDIT_ACTION");
        request.setEditAction(value);
        return request;
    }

    private JsonNode lastMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return objectMapper.readTree(captor.getAllValues().get(captor.getAllValues().size() - 1).getPayload());
    }

    private static WebSocketSession session(String id, Long pictureId, User user) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pictureId", pictureId);
        attributes.put("user", user);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    private static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setUserName(name);
        user.setUserAccount(name + "@test");
        return user;
    }

    private static ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
