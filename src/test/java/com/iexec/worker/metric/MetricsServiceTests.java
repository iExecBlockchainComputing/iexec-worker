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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MetricsServiceTests {
    private final static String CHAIN_TASK_ID_1 = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
    private final static String CHAIN_TASK_ID_2 = "0xc536af16737e02bb28100452a932056d499be3c462619751a9ed36515de64d50";

    @Mock
    private ComputeDurationsService preComputeDurationsService;
    @Mock
    private ComputeDurationsService appComputeDurationsService;
    @Mock
    private ComputeDurationsService postComputeDurationsService;

    private MetricsService metricsService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        metricsService = new MetricsService(
                preComputeDurationsService,
                appComputeDurationsService,
                postComputeDurationsService
        );
    }

    // region getWorkerMetrics
    @Test
    void shouldGetWorkerMetrics() {
        when(appComputeDurationsService.getChainTaskIds()).thenReturn(List.of(CHAIN_TASK_ID_1, CHAIN_TASK_ID_2));

        // First task has completed
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(1_000L));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(3_000L));
        when(postComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(2_000L));

        // Second task has completed
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(1_500L));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(3_500L));
        when(postComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(2_500L));

        when(preComputeDurationsService.getAggregatedDurations()).thenReturn(new AggregatedDurations(
                2,
                1_000.0,
                1_500.0,
                1_250.0
        ));
        when(appComputeDurationsService.getAggregatedDurations()).thenReturn(new AggregatedDurations(
                2,
                3_000.0,
                3_500.0,
                3_250.0
        ));
        when(postComputeDurationsService.getAggregatedDurations()).thenReturn(new AggregatedDurations(
                2,
                2_000.0,
                2_500.0,
                2_250.0
        ));

        final WorkerMetrics workerMetrics = metricsService.getWorkerMetrics();

        final WorkerMetrics expectedWorkerMetrics = new WorkerMetrics(
                new AggregatedDurations(2, 1_000.0, 1_500.0, 1_250.0),
                new AggregatedDurations(2, 3_000.0, 3_500.0, 3_250.0),
                new AggregatedDurations(2, 2_000.0, 2_500.0, 2_250.0),
                new AggregatedDurations(2, 6_000.0, 7_500.0, 6_750.0)
        );

        assertThat(workerMetrics).isEqualTo(expectedWorkerMetrics);
    }
    // endregion

    // region getCompleteComputeMetrics
    @Test
    void shouldGetCompleteComputeMetrics() {
        when(appComputeDurationsService.getChainTaskIds()).thenReturn(List.of(CHAIN_TASK_ID_1, CHAIN_TASK_ID_2));

        // First task has completed
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(1_000L));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(3_000L));
        when(postComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(2_000L));

        // Second task has completed
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(1_500L));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(3_500L));
        when(postComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(2_500L));

        final AggregatedDurations completeComputeMetrics = metricsService.getCompleteComputeMetrics();

        assertThat(completeComputeMetrics.getMinDuration()).isEqualTo(6_000.0);
        assertThat(completeComputeMetrics.getMaxDuration()).isEqualTo(7_500.0);
        assertThat(completeComputeMetrics.getAverageDuration()).isEqualTo(6_750.0);
    }
    // endregion

    // region getCompleteComputeDurations
    @Test
    void shouldGetCompleteComputeDurations() {
        when(appComputeDurationsService.getChainTaskIds()).thenReturn(List.of(CHAIN_TASK_ID_1, CHAIN_TASK_ID_2));

        // First task has completed
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(1_000L));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(3_000L));
        when(postComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(2_000L));

        // Second task has completed
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(1_500L));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(3_500L));
        when(postComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(2_500L));

        final List<Double> completeComputeDurations = metricsService.getCompleteComputeDurations();

        assertThat(completeComputeDurations).containsExactlyInAnyOrder(
                6_000.0,
                7_500.0
        );
    }

    @Test
    void shouldGetOnlyCompleteComputeDurations() {
        when(appComputeDurationsService.getChainTaskIds()).thenReturn(List.of(CHAIN_TASK_ID_1, CHAIN_TASK_ID_2));

        // First task has completed
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(1_000L));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(3_000L));
        when(postComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).thenReturn(Optional.of(2_000L));

        // Second task has completed
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(1_500L));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).thenReturn(Optional.of(3_500L));


        final List<Double> completeComputeDurations = metricsService.getCompleteComputeDurations();

        assertThat(completeComputeDurations).containsExactly(6_000.0);
    }
    // endregion

    // region getCompleteComputeDuration
    static Stream<Arguments> getCompleteComputeDurationArguments() {
        return Stream.of(
                // preComputeDuration, appComputeDuration, postComputeDuration, expectedTotalDuration
                Arguments.of(1_000L, 3_000L, 2_000L, 6_000.0),  // TEE task with pre/app/post
                Arguments.of(null, 3_000L, 2_000L, 5_000.0),    // TEE task with app/post
                Arguments.of(1_000L, null, 2_000L, null),       // Should not happen
                Arguments.of(1_000L, 3_000L, null, null),       // Task probably not finished
                Arguments.of(null, 3_000L, null, null),         // Task probably not finished
                Arguments.of(1_000L, null, null, null),         // Task probably not finished
                Arguments.of(null, null, null, null)            // Unknown task
        );
    }

    @ParameterizedTest
    @MethodSource("getCompleteComputeDurationArguments")
    void shouldGetCompleteComputeDuration(Long preComputeDuration,
                                          Long appComputeDuration,
                                          Long postComputeDuration,
                                          Double expectedTotalDuration) {
        when(preComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1))
                .thenReturn(Optional.ofNullable(preComputeDuration));
        when(appComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1))
                .thenReturn(Optional.ofNullable(appComputeDuration));
        when(postComputeDurationsService.getDurationForTask(CHAIN_TASK_ID_1))
                .thenReturn(Optional.ofNullable(postComputeDuration));

        final Optional<Double> completeComputeDuration = metricsService.getCompleteComputeDuration(CHAIN_TASK_ID_1);
        if (expectedTotalDuration == null) {
            assertThat(completeComputeDuration).isEmpty();
        } else {
            assertThat(completeComputeDuration).contains(expectedTotalDuration);
        }
    }
    // endregion
}
