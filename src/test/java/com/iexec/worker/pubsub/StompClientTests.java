package com.iexec.worker.pubsub;

import com.iexec.worker.TestUtils;
import com.iexec.worker.TestUtils.ThreadNameWrapper;
import com.iexec.worker.config.CoreConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

public class StompClientTests {

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    CoreConfigurationService coreConfigService;

    @Mock
    RestTemplate restTemplate;

    @Spy
    @InjectMocks
    private StompClient stompClient;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldNotSubscribeToTopicWhenOneParamIsNull() {
        assertThrows(NullPointerException.class,
                () -> stompClient.subscribeToTopic("topic", null));
        assertThrows(NullPointerException.class,
                () -> stompClient.subscribeToTopic(null, mock(StompFrameHandler.class)));
    }

    @Test
    public void shouldNotSubscribeToTopicWhenSessionIsNull() {
        assertThat(stompClient.subscribeToTopic("topic", mock(StompFrameHandler.class))).isEmpty();
    }

    @Test
    public void shouldListenToSessionRequestsAsynchronouslyAndRequestAFirstSessionWhenInit()
            throws Exception {
        String mainThreadName = Thread.currentThread().getName();
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        // Get the name of the thread that runs listenToSessionRequests()
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethod(
                threadNameWrapper, invocation))
                .when(stompClient).listenToSessionRequests();
        doNothing().when(stompClient).createSession();
        
        stompClient.init();
        TimeUnit.SECONDS.sleep(6); // should be greater than StompClient#SESSION_REFRESH_DELAY
        // Make sure listenToSessionRequests() runs asynchronously
        assertThat(threadNameWrapper.value).isNotEqualTo(mainThreadName);
        // Make sure listenToSessionRequests() method is called only 1 time
        verify(stompClient).listenToSessionRequests();
        // Make sure requestNewSession() method is called only 1 time
        verify(stompClient).requestNewSession();
        // Make sure createSession() method is called only 1 time
        verify(stompClient).createSession();
    }

    @Test
    public void shouldStartOnlyOneListenerThread() throws Exception {
        stompClient.startSessionRequestListenerIfAbsent();
        stompClient.startSessionRequestListenerIfAbsent();
        TimeUnit.MILLISECONDS.sleep(10);
        // Make sure listenToSessionRequests() method is called only 1 time
        verify(stompClient).listenToSessionRequests();
    }

    @Test
    public void shouldRestartListenerThreadWhenNoOneIsFound() throws Exception {
        stompClient.restartSessionRequestListenerIfStopped();
        TimeUnit.MILLISECONDS.sleep(10);
        // Make sure listenToSessionRequests() method is called 1 time
        verify(stompClient).listenToSessionRequests();
    }

    @Test
    public void shouldNoRestartListenerThreadWhenAnotherOneIsAlreadyFound() throws Exception {
        stompClient.startSessionRequestListenerIfAbsent();
        TimeUnit.MILLISECONDS.sleep(10);
        // Make sure listenToSessionRequests() method is called only 1 time
        verify(stompClient).listenToSessionRequests();
        stompClient.restartSessionRequestListenerIfStopped();
        TimeUnit.MILLISECONDS.sleep(10);
        // Make sure listenToSessionRequests() method is still called only 1 time
        verify(stompClient).listenToSessionRequests();
    }

    @Test
    public void shouldCreateSessionOnlyOnceWhenMultipleSessionRequestsAreReceived()
            throws Exception {
        // Start listener
        CompletableFuture.runAsync(() -> stompClient.listenToSessionRequests());
        doNothing().when(stompClient).createSession();

        // Request multiple times
        stompClient.requestNewSession();
        stompClient.requestNewSession();
        stompClient.requestNewSession();
        TimeUnit.SECONDS.sleep(6); // should be greater than StompClient#SESSION_REFRESH_DELAY
        // Make sure createSession() method is called only 1 time
        verify(stompClient).createSession();
    }
}