package com.ym.intelligentcollaborativecloudgallery.manager.websocket;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.PictureEditSessionManager.DisconnectResult;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.PictureEditSessionManager.ExpiredConnection;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.PictureEditSessionManager.Snapshot;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.PictureEditSessionManager.Transition;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.disruptor.PictureEditEventProducer;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditActionEnum;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditEditor;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditMessageTypeEnum;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditRequestMessage;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditResponseMessage;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditState;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片编辑 WebSocket 处理器
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    @Resource
    private PictureEditSessionManager pictureEditSessionManager;

    @Resource
    private ObjectMapper objectMapper;

    @Value("${picture.edit.heartbeat.timeout-ms:45000}")
    private long heartbeatTimeoutMillis;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        User user = getUser(session);
        Long pictureId = getPictureId(session);
        Snapshot snapshot = pictureEditSessionManager.registerConnection(pictureId, session, user);
        PictureEditResponseMessage response = response(
                PictureEditMessageTypeEnum.INFO,
                String.format("用户 %s 加入编辑", user.getUserName()),
                true,
                user,
                snapshot.getEditor(),
                snapshot.getState()
        );
        broadcastToPicture(pictureId, response);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        pictureEditSessionManager.touchConnection(session);
        PictureEditRequestMessage request = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        pictureEditEventProducer.publishEvent(request, session, getUser(session), getPictureId(session));
    }

    @Override
    protected void handlePongMessage(@NonNull WebSocketSession session, @NonNull PongMessage message) throws Exception {
        super.handlePongMessage(session, message);
        pictureEditSessionManager.touchConnection(session);
    }

    public void handleEnterEditMessage(PictureEditRequestMessage request, WebSocketSession session, User user, Long pictureId)
            throws IOException {
        Transition transition = pictureEditSessionManager.enterEdit(pictureId, session);
        if (transition.isSuccess()) {
            String message = transition.isChanged()
                    ? String.format("用户 %s 开始编辑图片", user.getUserName())
                    : String.format("用户 %s 已持有编辑锁", user.getUserName());
            PictureEditResponseMessage response = response(
                    PictureEditMessageTypeEnum.ENTER_EDIT,
                    message,
                    true,
                    user,
                    transition.getEditor(),
                    transition.getState()
            );
            response.setRequestId(request.getRequestId());
            if (transition.isChanged()) {
                broadcastToPicture(pictureId, response);
            } else {
                sendToSession(session, response);
            }
            return;
        }
        String message = transition.getEditor() == null
                ? "当前连接已失效，请重新连接"
                : String.format("图片正在由用户 %s 编辑", displayName(transition.getEditor()));
        PictureEditResponseMessage response = response(
                PictureEditMessageTypeEnum.ENTER_EDIT,
                message,
                false,
                user,
                transition.getEditor(),
                transition.getState()
        );
        response.setRequestId(request.getRequestId());
        sendToSession(session, response);
    }

    public void handleEditActionMessage(PictureEditRequestMessage request, WebSocketSession session, User user, Long pictureId)
            throws IOException {
        PictureEditActionEnum action = PictureEditActionEnum.getEnumByValue(request.getEditAction());
        if (action == null) {
            sendError(session, user, pictureId, "无效的编辑动作", request.getRequestId());
            return;
        }
        Transition transition = pictureEditSessionManager.applyEditAction(pictureId, session, action);
        if (!transition.isSuccess()) {
            PictureEditResponseMessage response = response(
                    PictureEditMessageTypeEnum.ERROR,
                    "当前连接没有图片编辑锁，不能执行编辑操作",
                    false,
                    user,
                    transition.getEditor(),
                    transition.getState()
            );
            response.setEditAction(request.getEditAction());
            response.setRequestId(request.getRequestId());
            sendToSession(session, response);
            return;
        }
        PictureEditResponseMessage response = response(
                PictureEditMessageTypeEnum.EDIT_ACTION,
                String.format("%s 执行 %s", user.getUserName(), action.getText()),
                true,
                user,
                transition.getEditor(),
                transition.getState()
        );
        response.setEditAction(request.getEditAction());
        response.setRequestId(request.getRequestId());
        broadcastToPicture(pictureId, response);
    }

    public void handleExitEditMessage(PictureEditRequestMessage request, WebSocketSession session, User user, Long pictureId)
            throws IOException {
        Transition transition = pictureEditSessionManager.exitEdit(pictureId, session);
        if (!transition.isSuccess()) {
            PictureEditResponseMessage response = response(
                    PictureEditMessageTypeEnum.EXIT_EDIT,
                    "当前连接没有图片编辑锁，不能释放其他连接的编辑锁",
                    false,
                    user,
                    transition.getEditor(),
                    transition.getState()
            );
            response.setRequestId(request.getRequestId());
            sendToSession(session, response);
            return;
        }
        PictureEditResponseMessage response = response(
                PictureEditMessageTypeEnum.EXIT_EDIT,
                String.format("用户 %s 退出编辑图片", user.getUserName()),
                true,
                user,
                transition.getEditor(),
                transition.getState()
        );
        response.setRequestId(request.getRequestId());
        broadcastToPicture(pictureId, response);
    }

    public void handleInvalidMessage(WebSocketSession session, User user, Long pictureId, String message) throws IOException {
        sendError(session, user, pictureId, message);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        Long pictureId = getPictureId(session);
        DisconnectResult result = pictureEditSessionManager.disconnect(pictureId, session);
        if (!result.isConnected()) {
            return;
        }
        broadcastDisconnect(pictureId, result.getUser(), result.isLockReleased());
    }

    @Scheduled(fixedDelayString = "${picture.edit.heartbeat.interval-ms:15000}")
    public void maintainConnections() {
        long now = System.currentTimeMillis();
        List<ExpiredConnection> expiredConnections =
                pictureEditSessionManager.expireInactiveConnections(heartbeatTimeoutMillis, now);
        for (ExpiredConnection expiredConnection : expiredConnections) {
            try {
                broadcastDisconnect(
                        expiredConnection.getPictureId(),
                        expiredConnection.getUser(),
                        expiredConnection.isLockReleased()
                );
            } catch (IOException exception) {
                log.warn("广播心跳超时状态失败, pictureId={}", expiredConnection.getPictureId(), exception);
            }
            closeSession(expiredConnection.getSession(), CloseStatus.SESSION_NOT_RELIABLE);
        }
        ByteBuffer payload = ByteBuffer.allocate(Long.BYTES).putLong(now).flip();
        for (WebSocketSession session : pictureEditSessionManager.getAllConnections()) {
            try {
                sendToSession(session, new PingMessage(payload.asReadOnlyBuffer()));
            } catch (IOException exception) {
                handleHeartbeatFailure(session, exception);
            }
        }
    }

    private void handleHeartbeatFailure(WebSocketSession session, IOException exception) {
        User user = getUser(session);
        Long pictureId = getPictureId(session);
        log.warn("发送心跳失败, pictureId={}, userId={}", pictureId, user.getId(), exception);
        DisconnectResult result = pictureEditSessionManager.disconnect(pictureId, session);
        closeSession(session, CloseStatus.SERVER_ERROR);
        if (!result.isConnected()) {
            return;
        }
        try {
            broadcastDisconnect(pictureId, result.getUser(), result.isLockReleased());
        } catch (IOException broadcastException) {
            log.warn("广播心跳失败断线状态失败, pictureId={}", pictureId, broadcastException);
        }
    }

    private void broadcastDisconnect(
            Long pictureId,
            User user,
            boolean lockReleased
    ) throws IOException {
        Snapshot currentSnapshot = pictureEditSessionManager.getSnapshot(pictureId);
        PictureEditEditor editor = currentSnapshot.getEditor();
        PictureEditState state = currentSnapshot.getState();
        if (lockReleased) {
            PictureEditResponseMessage releaseResponse = response(
                    PictureEditMessageTypeEnum.EXIT_EDIT,
                    String.format("用户 %s 连接断开，编辑锁已释放", user.getUserName()),
                    true,
                    user,
                    editor,
                    state
            );
            broadcastToPicture(pictureId, releaseResponse);
        }
        PictureEditResponseMessage leaveResponse = response(
                PictureEditMessageTypeEnum.INFO,
                String.format("用户 %s 离开编辑", user.getUserName()),
                true,
                user,
                editor,
                state
        );
        broadcastToPicture(pictureId, leaveResponse);
    }

    private void sendError(WebSocketSession session, User user, Long pictureId, String message) throws IOException {
        sendError(session, user, pictureId, message, null);
    }

    private void sendError(
            WebSocketSession session,
            User user,
            Long pictureId,
            String message,
            String requestId
    ) throws IOException {
        Snapshot snapshot = pictureEditSessionManager.getSnapshot(pictureId);
        PictureEditResponseMessage response = response(
                PictureEditMessageTypeEnum.ERROR,
                message,
                false,
                user,
                snapshot.getEditor(),
                snapshot.getState()
        );
        response.setRequestId(requestId);
        sendToSession(session, response);
    }

    private PictureEditResponseMessage response(
            PictureEditMessageTypeEnum type,
            String message,
            boolean success,
            User user,
            PictureEditEditor editor,
            PictureEditState state
    ) {
        PictureEditResponseMessage response = new PictureEditResponseMessage();
        response.setType(type.getValue());
        response.setMessage(message);
        response.setSuccess(success);
        response.setUser(userService.getUserVO(user));
        response.setEditor(editor);
        response.setState(state);
        return response;
    }

    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage response) throws IOException {
        String payload = objectMapper.writeValueAsString(response);
        TextMessage textMessage = new TextMessage(payload);
        List<WebSocketSession> failedSessions = new ArrayList<>();
        for (WebSocketSession session : pictureEditSessionManager.getConnections(pictureId)) {
            try {
                sendToSession(session, textMessage);
            } catch (IOException exception) {
                log.warn("广播消息失败, pictureId={}, sessionId={}", pictureId, session.getId(), exception);
                failedSessions.add(session);
            }
        }
        for (WebSocketSession session : failedSessions) {
            DisconnectResult result = pictureEditSessionManager.disconnect(pictureId, session);
            closeSession(session, CloseStatus.SERVER_ERROR);
            if (result.isConnected()) {
                broadcastDisconnect(pictureId, result.getUser(), result.isLockReleased());
            }
        }
    }

    private void sendToSession(WebSocketSession session, PictureEditResponseMessage response) throws IOException {
        sendToSession(session, new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void sendToSession(WebSocketSession session, WebSocketMessage<?> message) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        }
    }

    private void closeSession(WebSocketSession session, CloseStatus closeStatus) {
        try {
            session.close(closeStatus);
        } catch (IOException exception) {
            log.warn("关闭 WebSocket 会话失败, sessionId={}", session.getId(), exception);
        }
    }

    private static String displayName(PictureEditEditor editor) {
        return editor.getUserName() == null || editor.getUserName().isEmpty()
                ? editor.getUserAccount()
                : editor.getUserName();
    }

    private static User getUser(WebSocketSession session) {
        return (User) session.getAttributes().get("user");
    }

    private static Long getPictureId(WebSocketSession session) {
        return (Long) session.getAttributes().get("pictureId");
    }
}
