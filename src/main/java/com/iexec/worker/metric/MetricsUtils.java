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

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * A bunch of methods useful for metrics,
 * such as min, max and mean formatting (=> 0 if no value instead of NaN).
 */
public class MetricsUtils {

    private MetricsUtils() {
    }

    static double getMin(DescriptiveStatistics statistics) {
        return statistics.getN() == 0
                ? 0
                : statistics.getMin();
    }

    static double getMax(DescriptiveStatistics statistics) {
        return statistics.getN() == 0
                ? 0
                : statistics.getMax();
    }

    static double getMean(DescriptiveStatistics statistics) {
        return statistics.getN() == 0
                ? 0
                : statistics.getMean();
    }
}
