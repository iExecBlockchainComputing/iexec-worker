package com.iexec.worker.pubsub;

import com.iexec.common.result.UploadResultMessage;
import com.iexec.worker.utils.CoreConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.annotation.PostConstruct;
import java.lang.reflect.Type;

@Slf4j
@Service
public class SubscribeService extends StompSessionHandlerAdapter {

    private CoreConfigurationService coreConfigurationService;
    private StompSession session;

    public SubscribeService(CoreConfigurationService coreConfigurationService) {
        this.coreConfigurationService = coreConfigurationService;
    }

    @PostConstruct
    private void run() {
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());

        String url = "ws://"
                + coreConfigurationService.getHost()
                + ":"
                + coreConfigurationService.getPort()
                + "/connect";
        stompClient.connect(url, this);
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("SubscribeService set up [session: {}]", session.getSessionId());
        this.session = session;
        session.subscribe("/topic/uploadResult", this);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return UploadResultMessage.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        UploadResultMessage uploadResultMessage = (UploadResultMessage) payload;
        log.info("Received uploadResult message {}", uploadResultMessage);

    }
}