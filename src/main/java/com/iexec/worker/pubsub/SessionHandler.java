// package com.iexec.worker.pubsub;

// import org.springframework.lang.Nullable;
// import org.springframework.messaging.simp.SimpMessageType;
// import org.springframework.messaging.simp.stomp.StompCommand;
// import org.springframework.messaging.simp.stomp.StompHeaders;
// import org.springframework.messaging.simp.stomp.StompSession;
// import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

// import lombok.extern.slf4j.Slf4j;

// @Slf4j
// public class SessionHandler extends StompSessionHandlerAdapter {

//     private StompSession session;

//     @Override
//     public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
//         log.info("Connected to STOMP session [session: {}, isConnected: {}]", session.getSessionId(), session.isConnected());
//         this.session = session;
//         // reSubscribeToTopics();
//     }

//     @Override
//     public void handleException(StompSession session, @Nullable StompCommand command,
//                                 StompHeaders headers, byte[] payload, Throwable exception) {
//         SimpMessageType messageType = command != null ? command.getMessageType() : null;
//         log.error("STOMP error [session: {}, isConnected: {}, command: {}, exception: {}]",
//                 session.getSessionId(), session.isConnected(), messageType, exception.getMessage());
//         // refresh();
//     }

//     @Override
//     public void handleTransportError(StompSession session, Throwable exception) {
//         log.error("STOMP transport error [session: {}, isConnected: {}, exception: {}]",
//                 session.getSessionId(), session.isConnected(), exception.getMessage());
//         // refresh();
//     }

//     // public StompSession.Subscription subscribeToTopic(String topic, MessageHandler messageHandler) {
//     //     return session.subscribe(topic, messageHandler);
//     // }
// }