package com.iexec.worker.pubsub;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.result.TaskNotification;
import com.iexec.common.result.TaskNotificationType;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.feign.ResultRepoClient;
import com.iexec.worker.result.Eip712ChallengeService;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.annotation.PostConstruct;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Slf4j
@Service
public class SubscriptionService extends StompSessionHandlerAdapter {

    private final String coreHost;
    private final int corePort;
    private final String workerWalletAddress;
    // external services
    private ResultRepoClient resultRepoClient;
    private ResultService resultService;
    private RevealService revealService;
    private CustomFeignClient feignClient;
    private Eip712ChallengeService eip712ChallengeService;
    private PublicConfigurationService publicConfigurationService;
    // internal components
    private StompSession session;
    private Map<String, StompSession.Subscription> chainTaskIdToSubscription;
    private WebSocketStompClient stompClient;
    private String url;

    public SubscriptionService(CoreConfigurationService coreConfigurationService,
                               WorkerConfigurationService workerConfigurationService,
                               ResultRepoClient resultRepoClient,
                               ResultService resultService,
                               RevealService revealService,
                               CustomFeignClient feignClient,
                               Eip712ChallengeService eip712ChallengeService,
                               PublicConfigurationService publicConfigurationService) {
        this.resultRepoClient = resultRepoClient;
        this.resultService = resultService;
        this.revealService = revealService;
        this.feignClient = feignClient;
        this.eip712ChallengeService = eip712ChallengeService;
        this.publicConfigurationService = publicConfigurationService;

        this.coreHost = coreConfigurationService.getHost();
        this.corePort = coreConfigurationService.getPort();
        this.workerWalletAddress = workerConfigurationService.getWorkerWalletAddress();

        chainTaskIdToSubscription = new ConcurrentHashMap<>();
        url = "ws://" + coreHost + ":" + corePort + "/connect";
    }

    @PostConstruct
    private void run() {
        this.restartStomp();
    }

    private void restartStomp() {
        log.info("Starting STOMP");
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        this.stompClient = new WebSocketStompClient(webSocketClient);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        this.stompClient.setTaskScheduler(new ConcurrentTaskScheduler());
        this.stompClient.connect(url, this);
        this.reSubscribeToTopics();
        log.info("Started STOMP");
    }

    private void reSubscribeToTopics() {
        for (String chainTaskId : chainTaskIdToSubscription.keySet()) {
            unsubscribeFromTopic(chainTaskId);
            subscribeToTopic(chainTaskId);
        }
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("SubscriptionService set up [session: {}, isConnected: {}]", session.getSessionId(), session.isConnected());
        this.session = session;
    }

    @Override
    public void handleException(StompSession session, @Nullable StompCommand command,
                                StompHeaders headers, byte[] payload, Throwable exception) {
        log.error("Received handleException [session: {}, isConnected: {}, Exception: {}]",
                session.getSessionId(), session.isConnected(), exception.getMessage());
        this.restartStomp();
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        log.info("Received handleTransportError [session: {}, isConnected: {}]", session.getSessionId(), session.isConnected());
        this.restartStomp();
    }

    private void unsubscribeFromTopic(String chainTaskId) {
        if (chainTaskIdToSubscription.containsKey(chainTaskId)) {
            chainTaskIdToSubscription.get(chainTaskId).unsubscribe();
            chainTaskIdToSubscription.remove(chainTaskId);
            log.info("Unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("Already unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        }
    }

    public void subscribeToTopic(String chainTaskId) {
        if (!chainTaskIdToSubscription.containsKey(chainTaskId)) {
            StompSession.Subscription subscription = session.subscribe(getTaskTopicName(chainTaskId), new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return TaskNotification.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    TaskNotification taskNotification = (TaskNotification) payload;
                    handleTaskNotification(taskNotification);
                }
            });
            chainTaskIdToSubscription.put(chainTaskId, subscription);
            log.info("Subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, getTaskTopicName(chainTaskId));
        } else {
            log.info("Already subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, getTaskTopicName(chainTaskId));
        }
    }

    private void handleTaskNotification(TaskNotification notif) {
        if (notif.getWorkersAddress().contains(workerWalletAddress)
                || notif.getWorkersAddress().isEmpty()) {
            log.info("Received notification [notification:{}]", notif);

            TaskNotificationType type = notif.getTaskNotificationType();
            String chainTaskId = notif.getChainTaskId();

            switch (type) {
                case PLEASE_ABORT_CONTRIBUTION_TIMEOUT:
                    abortContributionTimeout(chainTaskId);
                    break;

                case PLEASE_ABORT_CONSENSUS_REACHED:
                    abortConsensusReached(chainTaskId);
                    break;

                case PLEASE_REVEAL:
                    reveal(chainTaskId);
                    break;

                case PLEASE_UPLOAD:
                    uploadResult(chainTaskId);
                    break;

                case COMPLETED:
                    completeTask(chainTaskId);
                    break;

                default:
                    break;
            }
        }
    }

    private void reveal(String chainTaskId) {
        log.info("Trying to reveal [chainTaskId:{}]", chainTaskId);
        if (!revealService.canReveal(chainTaskId)) {
            log.warn("The worker will not be able to reveal [chainTaskId:{}]", chainTaskId);
            feignClient.updateReplicateStatus(chainTaskId, CANT_REVEAL);
        }

        if (!revealService.hasEnoughGas()) {
            feignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            System.exit(0);
        }

        feignClient.updateReplicateStatus(chainTaskId, REVEALING);
        Optional<ChainReceipt> optionalChainReceipt = revealService.reveal(chainTaskId);
        if (!optionalChainReceipt.isPresent()) {
            feignClient.updateReplicateStatus(chainTaskId, REVEAL_FAILED);
            return;
        }

        feignClient.updateReplicateStatus(chainTaskId, REVEALED, optionalChainReceipt.get());
    }

    private void abortConsensusReached(String chainTaskId) {
        cleanReplicate(chainTaskId);
        feignClient.updateReplicateStatus(chainTaskId, ABORTED_ON_CONSENSUS_REACHED);
    }

    private void abortContributionTimeout(String chainTaskId) {
        cleanReplicate(chainTaskId);
        feignClient.updateReplicateStatus(chainTaskId, ABORTED_ON_CONTRIBUTION_TIMEOUT);
    }

    private void uploadResult(String chainTaskId) {
        feignClient.updateReplicateStatus(chainTaskId, RESULT_UPLOADING);
        Eip712Challenge eip712Challenge = resultRepoClient.getChallenge(publicConfigurationService.getChainId());
        String authorizationToken = eip712ChallengeService.buildAuthorizationToken(eip712Challenge);
        resultRepoClient.uploadResult(authorizationToken, resultService.getResultModelWithZip(chainTaskId));
        feignClient.updateReplicateStatus(chainTaskId, RESULT_UPLOADED);
    }

    private void completeTask(String chainTaskId) {
        cleanReplicate(chainTaskId);
        feignClient.updateReplicateStatus(chainTaskId, COMPLETED);
    }

    private void cleanReplicate(String chainTaskId) {
        // unsubscribe from the topic and remove the associated result from the machine
        unsubscribeFromTopic(chainTaskId);
        resultService.removeResult(chainTaskId);
    }

    private String getTaskTopicName(String chainTaskId) {
        return "/topic/task/" + chainTaskId;
    }
}