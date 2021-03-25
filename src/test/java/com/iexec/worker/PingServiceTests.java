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

import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.worker.WorkerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class PingServiceTests {

    public static final String SESSION_ID = "SESSION_ID";
    public static final String OTHER_SESSION_ID = "OTHER_SESSION_ID";

    @InjectMocks
    private PingService pingService;
    @Mock
    private CustomCoreFeignClient customCoreFeignClient;
    @Mock
    private CoreConfigurationService coreConfigurationService;
    @Mock
    private WorkerService workerService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);

    }

    @Test
    public void shouldPingAndDoNothingElseSincePongIsEmpty() {
        when(customCoreFeignClient.ping()).thenReturn("");

        pingService.pingScheduler();

        verify(coreConfigurationService, times(0))
                .setCoreSessionId(SESSION_ID);
        verify(workerService, times(0))
                .restartGracefully();
    }

    @Test
    public void shouldPingAndDoNothingElseSincePongIsNull() {
        when(customCoreFeignClient.ping()).thenReturn(null);

        pingService.pingScheduler();

        verify(coreConfigurationService, times(0))
                .setCoreSessionId(SESSION_ID);
        verify(workerService, times(0))
                .restartGracefully();
    }

    @Test
    public void shouldPingAndSetNewSessionSincePreviousSessionIsEmpty() {
        when(customCoreFeignClient.ping()).thenReturn(SESSION_ID);
        when(coreConfigurationService.getCoreSessionId()).thenReturn("");

        pingService.pingScheduler();

        verify(coreConfigurationService, times(1))
                .setCoreSessionId(SESSION_ID);
    }

    @Test
    public void shouldPingAndSetNewSessionSincePreviousSessionIsNull() {
        when(customCoreFeignClient.ping()).thenReturn(SESSION_ID);
        when(coreConfigurationService.getCoreSessionId()).thenReturn(null);

        pingService.pingScheduler();

        verify(coreConfigurationService, times(1))
                .setCoreSessionId(SESSION_ID);
    }

    @Test
    public void shouldPingAndRestart() {
        when(customCoreFeignClient.ping()).thenReturn(SESSION_ID);
        when(coreConfigurationService.getCoreSessionId()).thenReturn(OTHER_SESSION_ID);

        pingService.pingScheduler();

        verify(workerService, times(1))
                .restartGracefully();
    }

}