package com.ym.intelligentcollaborativecloudgallery.manager.websocket;

import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditActionEnum;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditEditor;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditState;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每张图片的协同编辑会话状态管理器
 */
@Component
public class PictureEditSessionManager {

    private static final int DEFAULT_ROTATION = 0;
    private static final double DEFAULT_ZOOM = 1.0;
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 5.0;

    private final Map<Long, PictureEditSession> pictureEditSessions = new ConcurrentHashMap<>();

    public Snapshot registerConnection(Long pictureId, WebSocketSession session, User user) {
        return registerConnection(pictureId, session, user, System.currentTimeMillis());
    }

    Snapshot registerConnection(Long pictureId, WebSocketSession session, User user, long now) {
        synchronized (pictureEditSessions) {
            PictureEditSession pictureEditSession = getPictureEditSession(pictureId);
            pictureEditSession.connections.put(session.getId(), new Connection(session, user, now));
            return pictureEditSession.snapshot();
        }
    }

    public Transition enterEdit(Long pictureId, WebSocketSession session) {
        synchronized (pictureEditSessions) {
            PictureEditSession pictureEditSession = findPictureEditSession(pictureId);
            if (pictureEditSession == null) {
                return unavailableTransition();
            }
            if (!pictureEditSession.isActive(session)) {
                return pictureEditSession.transition(false, false);
            }
            pictureEditSession.releaseClosedLockHolder();
            if (pictureEditSession.lockOwnerSessionId == null) {
                Connection connection = pictureEditSession.connections.get(session.getId());
                pictureEditSession.lockOwnerSessionId = session.getId();
                pictureEditSession.editor = toEditor(connection.user);
                return pictureEditSession.transition(true, true);
            }
            if (Objects.equals(pictureEditSession.lockOwnerSessionId, session.getId())) {
                return pictureEditSession.transition(true, false);
            }
            return pictureEditSession.transition(false, false);
        }
    }

    public Transition applyEditAction(Long pictureId, WebSocketSession session, PictureEditActionEnum action) {
        synchronized (pictureEditSessions) {
            PictureEditSession pictureEditSession = findPictureEditSession(pictureId);
            if (pictureEditSession == null) {
                return unavailableTransition();
            }
            if (!pictureEditSession.isActive(session)
                    || !Objects.equals(pictureEditSession.lockOwnerSessionId, session.getId())) {
                return pictureEditSession.transition(false, false);
            }
            pictureEditSession.apply(action);
            return pictureEditSession.transition(true, true);
        }
    }

    public Transition exitEdit(Long pictureId, WebSocketSession session) {
        synchronized (pictureEditSessions) {
            PictureEditSession pictureEditSession = findPictureEditSession(pictureId);
            if (pictureEditSession == null) {
                return unavailableTransition();
            }
            if (!pictureEditSession.isActive(session)
                    || !Objects.equals(pictureEditSession.lockOwnerSessionId, session.getId())) {
                return pictureEditSession.transition(false, false);
            }
            pictureEditSession.releaseLock();
            return pictureEditSession.transition(true, true);
        }
    }

    public DisconnectResult disconnect(Long pictureId, WebSocketSession session) {
        synchronized (pictureEditSessions) {
            PictureEditSession pictureEditSession = findPictureEditSession(pictureId);
            if (pictureEditSession == null) {
                Snapshot snapshot = defaultSnapshot();
                return new DisconnectResult(false, false, null, snapshot.editor, snapshot.state);
            }
            Connection removed = pictureEditSession.connections.get(session.getId());
            if (removed == null || removed.session != session) {
                Snapshot snapshot = pictureEditSession.snapshot();
                return new DisconnectResult(false, false, null, snapshot.editor, snapshot.state);
            }
            pictureEditSession.connections.remove(session.getId());
            boolean lockReleased = Objects.equals(pictureEditSession.lockOwnerSessionId, session.getId());
            if (lockReleased) {
                pictureEditSession.releaseLock();
            }
            Snapshot snapshot = pictureEditSession.snapshot();
            if (pictureEditSession.connections.isEmpty()) {
                pictureEditSessions.remove(pictureId, pictureEditSession);
            }
            return new DisconnectResult(true, lockReleased, removed.user, snapshot.editor, snapshot.state);
        }
    }

    public Snapshot getSnapshot(Long pictureId) {
        synchronized (pictureEditSessions) {
            PictureEditSession pictureEditSession = findPictureEditSession(pictureId);
            return pictureEditSession == null ? defaultSnapshot() : pictureEditSession.snapshot();
        }
    }

    public List<WebSocketSession> getConnections(Long pictureId) {
        synchronized (pictureEditSessions) {
            PictureEditSession pictureEditSession = findPictureEditSession(pictureId);
            List<WebSocketSession> sessions = new ArrayList<>();
            if (pictureEditSession == null) {
                return sessions;
            }
            for (Connection connection : pictureEditSession.connections.values()) {
                sessions.add(connection.session);
            }
            return sessions;
        }
    }

    public List<WebSocketSession> getAllConnections() {
        synchronized (pictureEditSessions) {
            List<WebSocketSession> sessions = new ArrayList<>();
            for (PictureEditSession pictureEditSession : pictureEditSessions.values()) {
                for (Connection connection : pictureEditSession.connections.values()) {
                    sessions.add(connection.session);
                }
            }
            return sessions;
        }
    }

    public boolean touchConnection(WebSocketSession session) {
        return touchConnection(session, System.currentTimeMillis());
    }

    boolean touchConnection(WebSocketSession session, long now) {
        synchronized (pictureEditSessions) {
            for (PictureEditSession pictureEditSession : pictureEditSessions.values()) {
                Connection connection = pictureEditSession.connections.get(session.getId());
                if (connection != null && connection.session == session) {
                    connection.lastSeen = now;
                    return true;
                }
            }
            return false;
        }
    }

    public List<ExpiredConnection> expireInactiveConnections(long timeoutMillis, long now) {
        synchronized (pictureEditSessions) {
            List<ExpiredConnection> expiredConnections = new ArrayList<>();
            Iterator<Map.Entry<Long, PictureEditSession>> pictureIterator =
                    pictureEditSessions.entrySet().iterator();
            while (pictureIterator.hasNext()) {
                Map.Entry<Long, PictureEditSession> entry = pictureIterator.next();
                PictureEditSession pictureEditSession = entry.getValue();
                Iterator<Map.Entry<String, Connection>> iterator = pictureEditSession.connections.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Connection> connectionEntry = iterator.next();
                    Connection connection = connectionEntry.getValue();
                    if (now - connection.lastSeen < timeoutMillis) {
                        continue;
                    }
                    iterator.remove();
                    boolean lockReleased = Objects.equals(pictureEditSession.lockOwnerSessionId, connection.session.getId());
                    if (lockReleased) {
                        pictureEditSession.releaseLock();
                    }
                    Snapshot snapshot = pictureEditSession.snapshot();
                    expiredConnections.add(new ExpiredConnection(
                            entry.getKey(),
                            connection.session,
                            connection.user,
                            lockReleased,
                            snapshot.editor,
                            snapshot.state
                    ));
                }
                if (pictureEditSession.connections.isEmpty()) {
                    pictureIterator.remove();
                }
            }
            return expiredConnections;
        }
    }

    private PictureEditSession getPictureEditSession(Long pictureId) {
        return pictureEditSessions.computeIfAbsent(pictureId, ignored -> new PictureEditSession());
    }

    private PictureEditSession findPictureEditSession(Long pictureId) {
        return pictureEditSessions.get(pictureId);
    }

    private static Snapshot defaultSnapshot() {
        return new Snapshot(null, new PictureEditState(DEFAULT_ROTATION, DEFAULT_ZOOM, 0));
    }

    private static Transition unavailableTransition() {
        Snapshot snapshot = defaultSnapshot();
        return new Transition(false, false, snapshot.editor, snapshot.state);
    }

    private static PictureEditEditor toEditor(User user) {
        return new PictureEditEditor(user.getId(), user.getUserName(), user.getUserAccount());
    }

    @Getter
    @AllArgsConstructor
    public static class Snapshot {
        private final PictureEditEditor editor;
        private final PictureEditState state;
    }

    @Getter
    @AllArgsConstructor
    public static class Transition {
        private final boolean success;
        private final boolean changed;
        private final PictureEditEditor editor;
        private final PictureEditState state;
    }

    @Getter
    @AllArgsConstructor
    public static class DisconnectResult {
        private final boolean connected;
        private final boolean lockReleased;
        private final User user;
        private final PictureEditEditor editor;
        private final PictureEditState state;
    }

    @Getter
    @AllArgsConstructor
    public static class ExpiredConnection {
        private final Long pictureId;
        private final WebSocketSession session;
        private final User user;
        private final boolean lockReleased;
        private final PictureEditEditor editor;
        private final PictureEditState state;
    }

    private static class PictureEditSession {
        private final Map<String, Connection> connections = new HashMap<>();
        private String lockOwnerSessionId;
        private PictureEditEditor editor;
        private int rotation = DEFAULT_ROTATION;
        private double zoom = DEFAULT_ZOOM;
        private int version = 0;

        private boolean isActive(WebSocketSession session) {
            Connection connection = connections.get(session.getId());
            return connection != null && connection.session == session && session.isOpen();
        }

        private void releaseClosedLockHolder() {
            if (lockOwnerSessionId == null) {
                return;
            }
            Connection lockOwner = connections.get(lockOwnerSessionId);
            if (lockOwner == null || !lockOwner.session.isOpen()) {
                if (lockOwner != null) {
                    connections.remove(lockOwnerSessionId);
                }
                releaseLock();
            }
        }

        private void apply(PictureEditActionEnum action) {
            switch (action) {
                case ZOOM_IN:
                    zoom = roundZoom(Math.min(MAX_ZOOM, zoom + 0.1));
                    break;
                case ZOOM_OUT:
                    zoom = roundZoom(Math.max(MIN_ZOOM, zoom - 0.1));
                    break;
                case ROTATE_LEFT:
                    rotation = (rotation + 270) % 360;
                    break;
                case ROTATE_RIGHT:
                    rotation = (rotation + 90) % 360;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported edit action: " + action);
            }
            version++;
        }

        private void releaseLock() {
            lockOwnerSessionId = null;
            editor = null;
        }

        private Snapshot snapshot() {
            PictureEditEditor editorSnapshot = editor == null ? null : new PictureEditEditor(
                    editor.getId(),
                    editor.getUserName(),
                    editor.getUserAccount()
            );
            return new Snapshot(editorSnapshot, new PictureEditState(rotation, zoom, version));
        }

        private Transition transition(boolean success, boolean changed) {
            Snapshot snapshot = snapshot();
            return new Transition(success, changed, snapshot.editor, snapshot.state);
        }

        private static double roundZoom(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }

    private static class Connection {
        private final WebSocketSession session;
        private final User user;
        private long lastSeen;

        private Connection(WebSocketSession session, User user, long lastSeen) {
            this.session = session;
            this.user = user;
            this.lastSeen = lastSeen;
        }
    }
}
