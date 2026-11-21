package com.ym.intelligentcollaborativecloudgallery.manager.websocket.disruptor;

import com.lmax.disruptor.WorkHandler;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.PictureEditHandler;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditMessageTypeEnum;
import com.ym.intelligentcollaborativecloudgallery.manager.websocket.model.PictureEditRequestMessage;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * 图片编辑事件处理器（消费者）
 */
@Component
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Override
    public void onEvent(PictureEditEvent pictureEditEvent) throws Exception {
        PictureEditRequestMessage request = pictureEditEvent.getPictureEditRequestMessage();
        WebSocketSession session = pictureEditEvent.getSession();
        User user = pictureEditEvent.getUser();
        Long pictureId = pictureEditEvent.getPictureId();
        PictureEditMessageTypeEnum type = PictureEditMessageTypeEnum.getEnumByValue(request.getType());
        if (type == null) {
            pictureEditHandler.handleInvalidMessage(session, user, pictureId, "消息类型错误");
            return;
        }
        switch (type) {
            case ENTER_EDIT:
                pictureEditHandler.handleEnterEditMessage(request, session, user, pictureId);
                break;
            case EXIT_EDIT:
                pictureEditHandler.handleExitEditMessage(request, session, user, pictureId);
                break;
            case EDIT_ACTION:
                pictureEditHandler.handleEditActionMessage(request, session, user, pictureId);
                break;
            default:
                pictureEditHandler.handleInvalidMessage(session, user, pictureId, "消息类型错误");
                break;
        }
    }
}
