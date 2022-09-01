package com.iexec.worker.pubsub;

import com.iexec.worker.TestUtils;
import com.iexec.worker.TestUtils.ThreadNameWrapper;
import com.iexec.worker.config.CoreConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class StompClientServiceTests {

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    CoreConfigurationService coreConfigService;

    @Mock
    RestTemplate restTemplate;

    @Spy
    @InjectMocks
    private StompClientService stompClientService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    //region subscribeToTopic
    @Test
    void shouldNotSubscribeToTopicWhenOneParamIsNull() {
        assertThrows(NullPointerException.class,
                () -> stompClientService.subscribeToTopic("topic", null));
        assertThrows(NullPointerException.class,
                () -> stompClientService.subscribeToTopic(null, mock(StompFrameHandler.class)));
    }

    @Test
    void shouldNotSubscribeToTopicWhenSessionIsNull() {
        assertThat(stompClientService.subscribeToTopic("topic", mock(StompFrameHandler.class))).isEmpty();
    }

    @Test
    void shouldSubscribeToTopic() {
        StompSession stompSession = mock(StompSession.class);
        ReflectionTestUtils.setField(stompClientService, "stompSession", stompSession);
        StompFrameHandler messageHandler = mock(SubscriptionService.MessageHandler.class);
        StompSession.Subscription subscription = mock(StompSession.Subscription.class);
        when(stompSession.subscribe("topic", messageHandler)).thenReturn(subscription);
        assertThat(stompClientService.subscribeToTopic("topic", messageHandler))
                .isNotEmpty()
                .contains(subscription);
    }
    //endregion

    //region requestNewSession
    @Test
    void shouldRequestSessionWhenStompSessionIsNull(CapturedOutput output) {
        stompClientService.requestNewSession();
        assertThat(output.getOut()).contains("Requested a new STOMP session");
    }

    @Test
    void shouldRequestSessionWhenStompSessionIsNotConnected(CapturedOutput output) {
        StompSession stompSession = mock(StompSession.class);
        ReflectionTestUtils.setField(stompClientService, "stompSession", stompSession);
        when(stompSession.isConnected()).thenReturn(false);
        stompClientService.requestNewSession();
        assertThat(output.getOut()).contains("Requested a new STOMP session");
    }

    @Test
    void shouldNotRequestSessionWhenStompSessionIsConnected(CapturedOutput output) {
        StompSession stompSession = mock(StompSession.class);
        ReflectionTestUtils.setField(stompClientService, "stompSession", stompSession);
        when(stompSession.isConnected()).thenReturn(true);
        stompClientService.requestNewSession();
        assertThat(output.getOut()).contains("A valid STOMP session exists, ignoring this request");
    }
    //endregion

    @Test
    void shouldListenToSessionRequestsAsynchronouslyAndRequestAFirstSessionWhenInit()
            throws Exception {
        String mainThreadName = Thread.currentThread().getName();
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        // Get the name of the thread that runs listenToSessionRequests()
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethod(
                threadNameWrapper, invocation))
                .when(stompClientService).listenToSessionRequests();
        // Reduce session refresh back off duration to make test faster.
        doAnswer((invocation) -> backOffBriefly()).when(stompClientService).backOff();
        // Don't execute session creation with remote server.
        doNothing().when(stompClientService).createSession();
        
        stompClientService.init();
        waitSessionRequestExecution();
        // Make sure listenToSessionRequests() runs asynchronously
        assertThat(threadNameWrapper.value).isNotEqualTo(mainThreadName);
        // Make sure listenToSessionRequests() method is called only 1 time
        verify(stompClientService).listenToSessionRequests();
        // Make sure requestNewSession() method is called only 1 time
        verify(stompClientService).requestNewSession();
        // Make sure createSession() method is called only 1 time
        verify(stompClientService).createSession();
    }

    @Test
    void shouldStartOnlyOneListenerThread() {
        final CompletableFuture<Void> firstListener =
                stompClientService.startSessionRequestListenerIfAbsent();
        final CompletableFuture<Void> secondListener =
                stompClientService.startSessionRequestListenerIfAbsent();

        assertThat(secondListener).isNull();
        assertThat(firstListener).isNotNull();
    }

    @Test
    void shouldRestartListenerThreadWhenNoOneIsFound() {
        assertThat(stompClientService.restartSessionRequestListenerIfStopped()).isNotNull();
    }

    @Test
    void shouldNoRestartListenerThreadWhenAnotherOneIsAlreadyFound() throws Exception {
        stompClientService.startSessionRequestListenerIfAbsent();
        TimeUnit.MILLISECONDS.sleep(10);
        // Make sure listenToSessionRequests() method is called only 1 time
        verify(stompClientService).listenToSessionRequests();
        stompClientService.restartSessionRequestListenerIfStopped();
        TimeUnit.MILLISECONDS.sleep(10);
        // Make sure listenToSessionRequests() method is still called only 1 time
        verify(stompClientService).listenToSessionRequests();
    }

    @Test
    void shouldCreateSessionOnlyOnceWhenMultipleSessionRequestsAreReceived() throws Exception {
        // Reduce session refresh back off duration to make test faster.
        doAnswer(invocation -> backOffBriefly()).when(stompClientService).backOff();
        // Don't execute session creation with remote server.
        doNothing().when(stompClientService).createSession();
        // Start listener
        CompletableFuture.runAsync(() -> stompClientService.listenToSessionRequests());
        waitForListener();

        // Request multiple times
        stompClientService.requestNewSession();
        stompClientService.requestNewSession();
        stompClientService.requestNewSession();
        waitSessionRequestExecution();
        // Make sure createSession() method is called only 1 time
        verify(stompClientService).createSession();
    }

    private void waitForListener() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(10);
    }

    private Void backOffBriefly() throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        return null;
    }

    private void waitSessionRequestExecution() throws InterruptedException {
        // should wait more than StompClient#SESSION_REFRESH_DELAY
        // which is changed to 1 second in backOffBriefly().
        TimeUnit.SECONDS.sleep(2);
    }
}