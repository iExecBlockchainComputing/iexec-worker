/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.metric;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsControllerTests {
    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private MetricsController metricsController;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region getWorkerMetrics
    @Test
    void shouldGetWorkerMetrics() {
        final WorkerMetrics metrics = mock(WorkerMetrics.class);
        when(metricsService.getWorkerMetrics()).thenReturn(metrics);

        final ResponseEntity<WorkerMetrics> response = metricsController.getWorkerMetrics();

        Assertions.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(response.getBody()).isEqualTo(metrics);
    }
    // endregion
}
