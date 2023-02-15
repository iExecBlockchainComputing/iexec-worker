/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker;

import com.iexec.worker.TestUtils.ThreadNameWrapper;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.feign.LoginService;
import com.iexec.worker.feign.client.CoreClient;
import com.iexec.worker.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PingServiceTests {

    private static final String PING_THREAD_NAME = "ping-1";
    private static final String SESSION_ID = "SESSION_ID";
    private static final String OTHER_SESSION_ID = "OTHER_SESSION_ID";

    @Mock
    private CoreClient coreClient;
    @Mock
    private CoreConfigurationService coreConfigurationService;
    @Mock
    private LoginService loginService;
    @Mock
    private WorkerService workerService;

    @Spy
    @InjectMocks
    private PingService pingService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        when(loginService.getToken()).thenReturn("Bearer token");
    }

    @Test
    void shouldRunPingAsynchronouslyWhenTriggeredOneTime() {
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        String mainThreadName = Thread.currentThread().getName();
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethod(threadNameWrapper, invocation))
                .when(pingService).pingScheduler();
        
        pingService.triggerSchedulerPing();
        // Make sure pingScheduler() method is called 1 time
        verify(pingService, timeout(100)).pingScheduler();
        // Make sure ping() method is called 1 time
        verify(coreClient).ping(anyString());
        assertThat(threadNameWrapper.value)
                .isEqualTo(PING_THREAD_NAME)
                .isNotEqualTo(mainThreadName);
    }

    /**
     * In this test the thread that runs "pingScheduler()" method will sleep after its
     * execution to make sure that is considered busy. The second call to the method
     * "pingScheduler()" should not be executed.
     */
    @Test
    void shouldRunPingSchedulerOnlyOnceWhenTriggeredTwoTimesSimultaneously() {
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        String mainThreadName = Thread.currentThread().getName();
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethodThenSleepSomeMillis(
                threadNameWrapper, invocation, 100)) // sleep duration > test duration
                .when(pingService).pingScheduler();

        // Trigger 2 times
        pingService.triggerSchedulerPing();
        pingService.triggerSchedulerPing();
        // Make sure pingScheduler() method is called 1 time
        verify(pingService, timeout(100)).pingScheduler();
        // Make sure ping() method is called 1 time
        verify(coreClient).ping(anyString());
        assertThat(threadNameWrapper.value)
                .isEqualTo(PING_THREAD_NAME)
                .isNotEqualTo(mainThreadName);
    }

    @Test
    void shouldRunPingSchedulerTwoConsecutiveTimesWhenTriggeredTwoConsecutiveTimes() {
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        String mainThreadName = Thread.currentThread().getName();
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethod(
                threadNameWrapper, invocation))
                .when(pingService).pingScheduler();

        // Trigger 1st time
        pingService.triggerSchedulerPing();
        // Make sure pingScheduler() method is called 1st time
        verify(pingService, timeout(500)).pingScheduler();
        // Make sure ping() method is called 1st time
        verify(coreClient, timeout(500)).ping(anyString());
        assertThat(threadNameWrapper.value)
                .isEqualTo(PING_THREAD_NAME)
                .isNotEqualTo(mainThreadName);

        // Trigger 2nd time
        threadNameWrapper.value = "";
        pingService.triggerSchedulerPing();
        // Make sure pingScheduler() method is called 2nd time
        verify(pingService, timeout(500).times(2)).pingScheduler();
        // Make sure ping() method is called 2nd time
        verify(coreClient, timeout(500).times(2)).ping(anyString());
        assertThat(threadNameWrapper.value)
                .isEqualTo(PING_THREAD_NAME)
                .isNotEqualTo(mainThreadName);
    }

    /**
     * This test makes sure that the queue of the executor which runs "pingScheduler()"
     * method is of size 1 and that it drops excessive incoming requests when an existing
     * request is already in the queue.
     * As you will notice, in the test we check that the method was called 2 times not
     * 1 time. That's because the queue is instantly emptied the first time so the queue
     * can accept the second request. So 2 is the least we can have.
     */
    @Test
    void shouldDropThirdAndForthPingRequestsWhenTriggeredMultipleTimes() {
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        String mainThreadName = Thread.currentThread().getName();
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethodThenSleepSomeMillis(
                threadNameWrapper, invocation, 10))
                .when(pingService).pingScheduler();
        when(coreClient.ping(anyString())).thenReturn(ResponseEntity.ok(SESSION_ID));
        when(coreConfigurationService.getCoreSessionId()).thenReturn(SESSION_ID);

        // Trigger 4 times
        pingService.triggerSchedulerPing();
        pingService.triggerSchedulerPing();
        pingService.triggerSchedulerPing();
        pingService.triggerSchedulerPing();
        // Make sure pingScheduler() method is called only 2 times
        verify(pingService, after(1000).times(2)).pingScheduler();
        // Make sure ping() method is called only 2 times
        verify(coreClient, times(2)).ping(anyString());
        assertThat(threadNameWrapper.value)
                .isEqualTo(PING_THREAD_NAME)
                .isNotEqualTo(mainThreadName);
    }

    @Test
    void shouldPingAndLogInWhenUnauthorized() {
        when(coreClient.ping(any())).thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        pingService.pingScheduler();
        verify(loginService).login();
        verifyNoInteractions(coreConfigurationService, workerService);
    }

    @Test
    void shouldPingAndDoNothingElseSincePongIsEmpty() {
        when(coreClient.ping(anyString())).thenReturn(ResponseEntity.ok(""));
        pingService.pingScheduler();
        verifyNoInteractions(coreConfigurationService, workerService);
    }

    @Test
    void shouldPingAndDoNothingElseSincePongIsNull() {
        when(coreClient.ping(anyString())).thenReturn(ResponseEntity.ok(null));
        pingService.pingScheduler();
        verifyNoInteractions(coreConfigurationService, workerService);
    }

    @Test
    void shouldPingAndDoNothingElseWhenSameSession() {
        when(coreClient.ping(anyString())).thenReturn(ResponseEntity.ok(SESSION_ID));
        when(coreConfigurationService.getCoreSessionId()).thenReturn(SESSION_ID);
        pingService.pingScheduler();
        verifyNoInteractions(workerService);
    }

    @Test
    void shouldPingAndSetNewSessionSincePreviousSessionIsEmpty() {
        when(coreClient.ping(anyString())).thenReturn(ResponseEntity.ok(SESSION_ID));
        when(coreConfigurationService.getCoreSessionId()).thenReturn("");
        pingService.pingScheduler();
        verify(coreConfigurationService).setCoreSessionId(anyString());
        verifyNoInteractions(workerService);
    }

    @Test
    void shouldPingAndSetNewSessionSincePreviousSessionIsNull() {
        when(coreClient.ping(anyString())).thenReturn(ResponseEntity.ok(SESSION_ID));
        when(coreConfigurationService.getCoreSessionId()).thenReturn(null);
        pingService.pingScheduler();
        verify(coreConfigurationService).setCoreSessionId(anyString());
        verifyNoInteractions(workerService);
    }

    @Test
    void shouldPingAndRestart() {
        when(coreClient.ping(anyString())).thenReturn(ResponseEntity.ok(SESSION_ID));
        when(coreConfigurationService.getCoreSessionId()).thenReturn(OTHER_SESSION_ID);
        pingService.pingScheduler();
        verify(workerService).restartGracefully();
    }
}
