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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ComputeDurationsServiceTests {
    private final static String WALLET_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String CHAIN_TASK_ID_1 = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
    private final static String CHAIN_TASK_ID_2 = "0xc536af16737e02bb28100452a932056d499be3c462619751a9ed36515de64d50";
    private static final String CONTEXT = "test";

    private MeterRegistry meterRegistry;
    private ComputeDurationsService computeDurationsService;
    private Gauge minGauge;
    private Gauge maxGauge;
    private Gauge averageGauge;

    @BeforeEach
    void beforeEach() {
        meterRegistry = new SimpleMeterRegistry();
        Metrics.globalRegistry.add(meterRegistry);
        computeDurationsService = new ComputeDurationsService(meterRegistry, WALLET_ADDRESS, CONTEXT);
        minGauge = meterRegistry.find(CONTEXT + "_duration_min").gauge();
        maxGauge = meterRegistry.find(CONTEXT + "_duration_max").gauge();
        averageGauge = meterRegistry.find(CONTEXT + "_duration_average").gauge();
    }

    @AfterEach
    void afterEach() {
        meterRegistry.clear();
        Metrics.globalRegistry.clear();
    }

    // region Constructor
    @Test
    void shouldRegisterGauges() {
        assertThat(meterRegistry.find("task.duration.min.test"))
                .isNotNull();
        assertThat(meterRegistry.find("task.duration.max.test"))
                .isNotNull();
        assertThat(meterRegistry.find("task.duration.average.test"))
                .isNotNull();
    }
    // endregion

    // region addDurationForTask
    @Test
    void shouldAddNewDuration() {
        final long duration = 1_000;

        assertThat(computeDurationsService.getChainTaskIds()).isEmpty();
        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_1, duration);
        assertThat(computeDurationsService.getDurationForTask(CHAIN_TASK_ID_1))
                .contains(duration);
    }

    @Test
    void shouldAddMultipleDurations() {
        final long duration_1 = 1_000;
        final long duration_2 = 2_000;

        assertThat(computeDurationsService.getChainTaskIds()).isEmpty();
        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_1, duration_1);
        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_2, duration_2);
        assertThat(computeDurationsService.getDurationForTask(CHAIN_TASK_ID_1))
                .contains(duration_1);
        assertThat(computeDurationsService.getDurationForTask(CHAIN_TASK_ID_2))
                .contains(duration_2);
    }
    // endregion

    // region getAggregatedMetrics
    @Test
    void shouldGetNanAggregatedDurationsSinceNoValue() {
        final AggregatedDurations aggregatedDurations = computeDurationsService.getAggregatedDurations();
        assertThat(aggregatedDurations.getMinDuration()).isNaN();
        assertThat(aggregatedDurations.getMaxDuration()).isNaN();
        assertThat(aggregatedDurations.getAverageDuration()).isNaN();
    }

    @Test
    void shouldGetSameAggregatedDurationSinceSingleValue() {
        final long duration = 1_000;

        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_1, duration);

        final AggregatedDurations aggregatedDurations = computeDurationsService.getAggregatedDurations();
        assertThat(aggregatedDurations.getMinDuration()).isEqualTo(duration);
        assertThat(aggregatedDurations.getMaxDuration()).isEqualTo(duration);
        assertThat(aggregatedDurations.getAverageDuration()).isEqualTo(duration);
    }

    @Test
    void shouldGetDifferentAggregatedDurationSinceMultipleValues() {
        final long duration_1 = 1_000;
        final long duration_2 = 2_000;

        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_1, duration_1);
        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_2, duration_2);

        final AggregatedDurations aggregatedDurations = computeDurationsService.getAggregatedDurations();
        assertThat(aggregatedDurations.getMinDuration()).isEqualTo(duration_1);
        assertThat(aggregatedDurations.getMaxDuration()).isEqualTo(duration_2);
        assertThat(aggregatedDurations.getAverageDuration()).isEqualTo((duration_1 + duration_2) / 2.0);
    }
    // endregion

    // region Metrics evolution
    static Stream<Arguments> metricsEvolutionValuesProvider() {
        // values, expected min, expected max, expected average
        return Stream.of(
                Arguments.of(List.of(), Double.NaN, Double.NaN, Double.NaN),   // No value => expected NaN
                Arguments.of(List.of(1_000.0), 1_000.0, 1_000.0, 1_000.0),  // Single value => same expected value
                Arguments.of(List.of(1_000.0, 500.0), 500.0, 1_000.0, 750.0), // Max as first value
                Arguments.of(List.of(1_000.0, 2_000.0), 1_000.0, 2_000.0, 1_500.0),   // Max as second value
                Arguments.of(List.of(1_000.0, 2_000.0, 3_000.0), 1_000.0, 3_000.0, 2_000.0) // More than 2 values
        );
    }

    @ParameterizedTest
    @MethodSource("metricsEvolutionValuesProvider")
    void shouldEffectivelyTrackMetricsEvolutions(List<Double> values,
                                                 Double expectedMinValue,
                                                 Double expectedMaxValue,
                                                 Double expectedAverageValue) {
        for (int i = 0; i < values.size(); i++) {
            computeDurationsService.addDurationForTask(i + "", values.get(i).longValue());
        }

        assertThat(minGauge.value()).isEqualTo(expectedMinValue);
        assertThat(maxGauge.value()).isEqualTo(expectedMaxValue);
        assertThat(averageGauge.value()).isEqualTo(expectedAverageValue);
    }
    // endregion
}
