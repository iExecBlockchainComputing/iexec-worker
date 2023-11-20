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

import com.iexec.worker.utils.MaxSizeHashMap;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Simple service to store durations of a compute stage (pre, app or post-compute)
 * and aggregates them (min, max and average).
 */
public class ComputeDurationsService {
    private static final String EXPORTED_STAT_PREFIX = "iexec_";

    private final Map<String, Long> durationPerChainTaskId;
    private final DescriptiveStatistics statistics;

    public ComputeDurationsService(MeterRegistry registry,
                                   String workerWalletAddress,
                                   String context,
                                   int windowSize) {
        this.durationPerChainTaskId = new MaxSizeHashMap<>(windowSize);
        this.statistics = new DescriptiveStatistics(windowSize);

        final String[] tags = {"wallet", workerWalletAddress, "phase", context};
        Gauge.builder(EXPORTED_STAT_PREFIX + context + "_duration_min", statistics::getMin)
                .tags(tags)
                .register(registry);
        Gauge.builder(EXPORTED_STAT_PREFIX + context + "_duration_max", statistics::getMax)
                .tags(tags)
                .register(registry);
        Gauge.builder(EXPORTED_STAT_PREFIX + context + "_duration_average", statistics::getMean)
                .tags(tags)
                .register(registry);
        Gauge.builder(EXPORTED_STAT_PREFIX + context + "_duration_count", statistics::getN)
                .tags(tags)
                .register(registry);
    }

    /**
     * Stores a new duration for a task.
     *
     * @param chainTaskId Chain task id for the duration
     * @param duration    Duration for the task
     */
    public void addDurationForTask(String chainTaskId, long duration) {
        durationPerChainTaskId.put(chainTaskId, duration);
        statistics.addValue(duration);
    }

    /**
     * Returns the duration for a task.
     *
     * @param chainTaskId Chain task ID whose associated duration should be retrieved.
     * @return An {@link Optional} containing the duration,
     * {@link Optional#empty()} if no duration for given ID.
     */
    public Optional<Long> getDurationForTask(String chainTaskId) {
        return Optional.ofNullable(durationPerChainTaskId.get(chainTaskId));
    }

    /**
     * Returns a collection of all registered chain task IDs.
     *
     * @return A {@link Collection} of all registered chain task IDs.
     */
    public Collection<String> getChainTaskIds() {
        return durationPerChainTaskId.keySet();
    }

    /**
     * Aggregates durations of all tasks and returns min, max and average values.
     *
     * @return An instance of {@link AggregatedDurations}.
     */
    public AggregatedDurations getAggregatedDurations() {
        return new AggregatedDurations(
                statistics.getN(),
                statistics.getMin(),
                statistics.getMax(),
                statistics.getMean()
        );
    }
}
