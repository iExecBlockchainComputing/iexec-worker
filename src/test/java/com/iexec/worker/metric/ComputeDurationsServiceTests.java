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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ComputeDurationsServiceTests {
    private static final String WALLET_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private static final String CHAIN_TASK_ID_1 = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
    private static final String CHAIN_TASK_ID_2 = "0xc536af16737e02bb28100452a932056d499be3c462619751a9ed36515de64d50";
    private static final String EXPORTED_STAT_PREFIX = "iexec_";
    private static final String CONTEXT = "test";
    private static final int STATISTICS_WINDOW = 100;
    private static final List<Double> LONG_LIST_OF_VALUES = IntStream.range(0, STATISTICS_WINDOW + 1).mapToObj(i -> (double) i).collect(Collectors.toList());

    private ComputeDurationsService computeDurationsService;
    private Gauge minGauge;
    private Gauge maxGauge;
    private Gauge averageGauge;
    private Gauge countGauge;

    @BeforeEach
    void beforeEach() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        Metrics.globalRegistry.add(meterRegistry);

        computeDurationsService = new ComputeDurationsService(meterRegistry, WALLET_ADDRESS, CONTEXT, STATISTICS_WINDOW);
        minGauge = meterRegistry.find(EXPORTED_STAT_PREFIX + CONTEXT + "_duration_min").gauge();
        maxGauge = meterRegistry.find(EXPORTED_STAT_PREFIX + CONTEXT + "_duration_max").gauge();
        averageGauge = meterRegistry.find(EXPORTED_STAT_PREFIX + CONTEXT + "_duration_average").gauge();
        countGauge = meterRegistry.find(EXPORTED_STAT_PREFIX + CONTEXT + "_duration_samples_count").gauge();
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    // region Constructor
    @Test
    void shouldRegisterGauges() {
        Assertions.assertAll(
                () -> assertThat(minGauge).isNotNull(),
                () -> assertThat(maxGauge).isNotNull(),
                () -> assertThat(averageGauge).isNotNull(),
                () -> assertThat(countGauge).isNotNull()
        );
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

        Assertions.assertAll(
                () -> assertThat(computeDurationsService.getDurationForTask(CHAIN_TASK_ID_1)).contains(duration_1),
                () -> assertThat(computeDurationsService.getDurationForTask(CHAIN_TASK_ID_2)).contains(duration_2)
        );
    }
    // endregion

    // region getAggregatedMetrics
    @Test
    void shouldGetNanAggregatedDurationsSinceNoValue() {
        final AggregatedDurations aggregatedDurations = computeDurationsService.getAggregatedDurations();
        Assertions.assertAll(
                () -> assertThat(aggregatedDurations.getMinDuration()).isNaN(),
                () -> assertThat(aggregatedDurations.getMaxDuration()).isNaN(),
                () -> assertThat(aggregatedDurations.getAverageDuration()).isNaN(),
                () -> assertThat(aggregatedDurations.getDurationSamplesCount()).isZero()
        );
    }

    @Test
    void shouldGetSameAggregatedDurationSinceSingleValue() {
        final long duration = 1_000;

        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_1, duration);

        final AggregatedDurations aggregatedDurations = computeDurationsService.getAggregatedDurations();

        assertAggregatedDurations(aggregatedDurations, (double) duration, (double) duration, (double) duration, 1);
    }

    @Test
    void shouldGetDifferentAggregatedDurationSinceMultipleValues() {
        final long duration_1 = 1_000;
        final long duration_2 = 2_000;

        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_1, duration_1);
        computeDurationsService.addDurationForTask(CHAIN_TASK_ID_2, duration_2);

        final AggregatedDurations aggregatedDurations = computeDurationsService.getAggregatedDurations();

        assertAggregatedDurations(aggregatedDurations, (double) duration_1, (double) duration_2, (duration_1 + duration_2) / 2.0, 2);
    }
    // endregion

    // region Metrics evolution
    static Stream<Arguments> metricsEvolutionValuesProvider() {
        // values, expected min, expected max, expected average, expected count
        return Stream.of(
                Arguments.of(List.of(), Double.NaN, Double.NaN, Double.NaN, 0),   // No value => expected NaN
                Arguments.of(List.of(1_000.0), 1_000.0, 1_000.0, 1_000.0, 1),  // Single value => same expected value
                Arguments.of(List.of(1_000.0, 500.0), 500.0, 1_000.0, 750.0, 2), // Max as first value
                Arguments.of(List.of(1_000.0, 2_000.0), 1_000.0, 2_000.0, 1_500.0, 2),   // Max as second value
                Arguments.of(List.of(1_000.0, 2_000.0, 3_000.0), 1_000.0, 3_000.0, 2_000.0, 3), // More than 2 values
                Arguments.of(LONG_LIST_OF_VALUES, 1.0, (double) STATISTICS_WINDOW, (STATISTICS_WINDOW + 1) / 2.0, STATISTICS_WINDOW) // More than STATISTICS_WINDOW values
        );
    }

    @ParameterizedTest
    @MethodSource("metricsEvolutionValuesProvider")
    void shouldEffectivelyTrackMetricsEvolutions(List<Double> values,
                                                 Double expectedMinValue,
                                                 Double expectedMaxValue,
                                                 Double expectedAverageValue,
                                                 long expectedCountValue) {
        for (int i = 0; i < values.size(); i++) {
            computeDurationsService.addDurationForTask(i + "", values.get(i).longValue());
        }

        assertGaugeValues(expectedMinValue, expectedMaxValue, expectedAverageValue, expectedCountValue);
    }
    // endregion

    // region Utils
    private void assertAggregatedDurations(AggregatedDurations aggregatedDurations,
                                           Double expectedMinValue,
                                           Double expectedMaxValue,
                                           Double expectedAverageValue,
                                           long expectedCountValue) {
        Assertions.assertAll(
                () -> assertThat(aggregatedDurations.getMinDuration()).isEqualTo(expectedMinValue),
                () -> assertThat(aggregatedDurations.getMaxDuration()).isEqualTo(expectedMaxValue),
                () -> assertThat(aggregatedDurations.getAverageDuration()).isEqualTo(expectedAverageValue),
                () -> assertThat(aggregatedDurations.getDurationSamplesCount()).isEqualTo(expectedCountValue)
        );
    }

    private void assertGaugeValues(Double expectedMinValue,
                                   Double expectedMaxValue,
                                   Double expectedAverageValue,
                                   long expectedCountValue) {
        Assertions.assertAll(
                () -> assertThat(minGauge.value()).isEqualTo(expectedMinValue),
                () -> assertThat(maxGauge.value()).isEqualTo(expectedMaxValue),
                () -> assertThat(averageGauge.value()).isEqualTo(expectedAverageValue),
                () -> assertThat(countGauge.value()).isEqualTo(expectedCountValue)
        );
    }
    // endregion
}
