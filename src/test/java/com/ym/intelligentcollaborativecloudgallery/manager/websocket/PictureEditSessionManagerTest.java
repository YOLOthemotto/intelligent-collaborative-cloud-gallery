package com.ym.intelligentcollaborativecloudgallery.manager.websocket;

import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditActionEnum;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PictureEditSessionManagerTest {

    private final PictureEditSessionManager manager = new PictureEditSessionManager();

    @Test
    void onlyOneConnectionCanAcquireTheLockAndActionsRequireIt() {
        Long pictureId = 1L;
        WebSocketSession firstSession = session("first");
        WebSocketSession secondSession = session("second");
        manager.registerConnection(pictureId, firstSession, user(1L, "alice"));
        manager.registerConnection(pictureId, secondSession, user(2L, "bob"));

        PictureEditSessionManager.Snapshot initial = manager.getSnapshot(pictureId);
        assertEquals(0, initial.getState().getRotation());
        assertEquals(1.0, initial.getState().getZoom());
        assertEquals(0, initial.getState().getVersion());

        PictureEditSessionManager.Transition firstEnter = manager.enterEdit(pictureId, firstSession);
        assertTrue(firstEnter.isSuccess());
        assertEquals(1L, firstEnter.getEditor().getId());
        assertFalse(manager.enterEdit(pictureId, secondSession).isSuccess());

        PictureEditSessionManager.Transition duplicateEnter = manager.enterEdit(pictureId, firstSession);
        assertTrue(duplicateEnter.isSuccess());
        assertFalse(duplicateEnter.isChanged());
        assertFalse(manager.applyEditAction(
                pictureId, secondSession, PictureEditActionEnum.ZOOM_IN).isSuccess());

        PictureEditSessionManager.Transition rotate =
                manager.applyEditAction(pictureId, firstSession, PictureEditActionEnum.ROTATE_RIGHT);
        PictureEditSessionManager.Transition zoom =
                manager.applyEditAction(pictureId, firstSession, PictureEditActionEnum.ZOOM_IN);
        assertEquals(90, rotate.getState().getRotation());
        assertEquals(1.1, zoom.getState().getZoom());
        assertEquals(2, zoom.getState().getVersion());
    }

    @Test
    void stateSurvivesWhileConnectionsRemainThenResetsAfterLastDisconnect() {
        Long pictureId = 2L;
        WebSocketSession firstSession = session("first");
        WebSocketSession secondSession = session("second");
        User firstUser = user(1L, "alice");
        User secondUser = user(2L, "bob");
        manager.registerConnection(pictureId, firstSession, firstUser);
        manager.registerConnection(pictureId, secondSession, secondUser);
        manager.enterEdit(pictureId, firstSession);
        manager.applyEditAction(pictureId, firstSession, PictureEditActionEnum.ROTATE_LEFT);

        assertTrue(manager.exitEdit(pictureId, firstSession).isSuccess());
        assertTrue(manager.enterEdit(pictureId, secondSession).isSuccess());
        assertFalse(manager.exitEdit(pictureId, firstSession).isSuccess());
        PictureEditSessionManager.DisconnectResult staleDisconnect = manager.disconnect(pictureId, firstSession);
        assertFalse(staleDisconnect.isLockReleased());
        assertEquals(2L, manager.getSnapshot(pictureId).getEditor().getId());
        assertEquals(270, manager.getSnapshot(pictureId).getState().getRotation());

        assertTrue(manager.exitEdit(pictureId, secondSession).isSuccess());
        WebSocketSession reconnect = session("reconnect");
        manager.registerConnection(pictureId, reconnect, firstUser);
        assertTrue(manager.enterEdit(pictureId, reconnect).isSuccess());
        assertEquals(270, manager.getSnapshot(pictureId).getState().getRotation());

        PictureEditSessionManager.DisconnectResult reconnectDisconnect = manager.disconnect(pictureId, reconnect);
        assertTrue(reconnectDisconnect.isLockReleased());
        assertNull(reconnectDisconnect.getEditor());
        assertEquals(270, reconnectDisconnect.getState().getRotation());
        assertEquals(1, reconnectDisconnect.getState().getVersion());

        manager.disconnect(pictureId, secondSession);
        WebSocketSession freshSession = session("fresh");
        PictureEditSessionManager.Snapshot freshSnapshot =
                manager.registerConnection(pictureId, freshSession, firstUser);
        assertNull(freshSnapshot.getEditor());
        assertEquals(0, freshSnapshot.getState().getRotation());
        assertEquals(1.0, freshSnapshot.getState().getZoom());
        assertEquals(0, freshSnapshot.getState().getVersion());
    }

    @Test
    void timeoutReleasesOnlyExpiredOwnerAndKeepsCanvasState() {
        Long pictureId = 3L;
        WebSocketSession editorSession = session("editor");
        WebSocketSession observerSession = session("observer");
        manager.registerConnection(pictureId, editorSession, user(1L, "alice"), 1000);
        manager.registerConnection(pictureId, observerSession, user(2L, "bob"), 1050);
        manager.enterEdit(pictureId, editorSession);
        manager.applyEditAction(pictureId, editorSession, PictureEditActionEnum.ZOOM_OUT);

        List<PictureEditSessionManager.ExpiredConnection> expired =
                manager.expireInactiveConnections(100, 1110);

        assertEquals(1, expired.size());
        assertTrue(expired.get(0).isLockReleased());
        assertEquals(editorSession, expired.get(0).getSession());
        assertNull(expired.get(0).getEditor());
        assertEquals(0.9, expired.get(0).getState().getZoom());
        assertEquals(1, expired.get(0).getState().getVersion());
        assertEquals(observerSession, manager.getConnections(pictureId).get(0));
        assertTrue(manager.enterEdit(pictureId, observerSession).isSuccess());
    }

    @Test
    void lastExpiredConnectionDiscardsTransientState() {
        Long pictureId = 5L;
        WebSocketSession session = session("only");
        User user = user(1L, "alice");
        manager.registerConnection(pictureId, session, user, 1000);
        manager.enterEdit(pictureId, session);
        manager.applyEditAction(pictureId, session, PictureEditActionEnum.ROTATE_LEFT);
        manager.applyEditAction(pictureId, session, PictureEditActionEnum.ZOOM_IN);

        List<PictureEditSessionManager.ExpiredConnection> expired =
                manager.expireInactiveConnections(100, 1100);

        assertEquals(1, expired.size());
        assertTrue(expired.get(0).isLockReleased());
        assertEquals(270, expired.get(0).getState().getRotation());
        assertEquals(1.1, expired.get(0).getState().getZoom());
        assertEquals(2, expired.get(0).getState().getVersion());

        WebSocketSession nextSession = session("next");
        PictureEditSessionManager.Snapshot next =
                manager.registerConnection(pictureId, nextSession, user);
        assertNull(next.getEditor());
        assertEquals(0, next.getState().getRotation());
        assertEquals(1.0, next.getState().getZoom());
        assertEquals(0, next.getState().getVersion());
    }

    @Test
    void concurrentEnterHasOneWinner() throws Exception {
        Long pictureId = 4L;
        WebSocketSession firstSession = session("first");
        WebSocketSession secondSession = session("second");
        manager.registerConnection(pictureId, firstSession, user(1L, "alice"));
        manager.registerConnection(pictureId, secondSession, user(2L, "bob"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<PictureEditSessionManager.Transition>> futures = Arrays.asList(
                    executor.submit(() -> {
                        start.await();
                        return manager.enterEdit(pictureId, firstSession);
                    }),
                    executor.submit(() -> {
                        start.await();
                        return manager.enterEdit(pictureId, secondSession);
                    })
            );
            start.countDown();
            long winners = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .filter(PictureEditSessionManager.Transition::isSuccess)
                    .count();
            assertEquals(1, winners);
            assertNotNull(manager.getSnapshot(pictureId).getEditor());
        } finally {
            executor.shutdownNow();
        }
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setUserName(name);
        user.setUserAccount(name + "@test");
        return user;
    }
}
