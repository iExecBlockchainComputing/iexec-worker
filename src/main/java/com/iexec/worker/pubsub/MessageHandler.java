// package com.iexec.worker.pubsub;

// import java.lang.reflect.Type;

// import com.iexec.common.notification.TaskNotification;

// import org.springframework.context.ApplicationEventPublisher;
// import org.springframework.lang.Nullable;
// import org.springframework.messaging.simp.stomp.StompFrameHandler;
// import org.springframework.messaging.simp.stomp.StompHeaders;

// import lombok.extern.slf4j.Slf4j;

// @Slf4j
// public class MessageHandler implements StompFrameHandler {

//     private final String workerWalletAddress;
//     private final String chainTaskId;
//     private final ApplicationEventPublisher applicationEventPublisher;

//     public MessageHandler(String walletAddress, String chainTaskId, ApplicationEventPublisher eventPublisher) {
//         this.workerWalletAddress = walletAddress;
//         this.chainTaskId = chainTaskId;
//         this.applicationEventPublisher = eventPublisher;
//     }

//     @Override
//     public Type getPayloadType(StompHeaders headers) {
//         return TaskNotification.class;
//     }

//     @Override
//     public void handleFrame(StompHeaders headers, @Nullable Object payload) {
//         if (payload == null) {
//             log.info("Payload of TaskNotification is null [chainTaskId:{}]", chainTaskId);
//             return;
//         }
//         TaskNotification taskNotification = (TaskNotification) payload;
//         boolean isNotifForEveryone = taskNotification.getWorkersAddress().isEmpty();
//         boolean isNotifForMe = taskNotification.getWorkersAddress().contains(this.workerWalletAddress);
//         if (!isNotifForEveryone && !isNotifForMe) {
//             return;
//         }
//         log.info("PubSub service received taskNotification [chainTaskId:{}, type:{}]",
//                 chainTaskId, taskNotification.getTaskNotificationType());
//         applicationEventPublisher.publishEvent(taskNotification);
//     }
// }