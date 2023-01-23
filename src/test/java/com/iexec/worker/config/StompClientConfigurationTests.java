/*
 * Copyright 2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class StompClientConfigurationTests {
    StompClientConfiguration stompClientConfiguration = new StompClientConfiguration();

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateStompClient() {
        final WebSocketStompClient stompClient = stompClientConfiguration.stompClient();
        assertAll(
                () -> assertThat(stompClient).isNotNull(),
                () -> assertThat(stompClient).extracting(WebSocketStompClient::isAutoStartup).isEqualTo(true),
                () -> assertThat(stompClient).extracting(WebSocketStompClient::getMessageConverter).isInstanceOf(MappingJackson2MessageConverter.class),
                () -> assertThat(stompClient).extracting(WebSocketStompClient::getTaskScheduler).isInstanceOf(ConcurrentTaskScheduler.class),
                () -> assertThat(stompClient).extracting(WebSocketStompClient::getWebSocketClient).isInstanceOf(StandardWebSocketClient.class)
        );
    }
}
