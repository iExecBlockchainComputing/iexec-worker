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

package com.iexec.worker.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaxSizeHashMapTests {
    @Test
    void shouldNotExceedMaxSizeWhenAddSingleValue() {
        final int maxSize = 5;

        final Map<Integer, Integer> map = new MaxSizeHashMap<>(maxSize);

        // Add `maxSize` number of values
        // `map` should grow each time
        for (int i = 0; i < maxSize; i++) {
            map.put(i, i);
            assertThat(map).hasSize(i + 1);
        }

        // `map` should still contain all values
        for (int i = 0; i < maxSize; i++) {
            assertThat(map).containsKey(i);
        }

        // Add new element
        // Map should not grow,
        // and key "0" should not be accessible anymore
        map.put(maxSize, maxSize);
        assertThat(map)
                .hasSize(maxSize)
                .doesNotContainKey(0);
    }

    @Test
    void shouldNotExceedMaxSizeWhenPutAll() {
        final Map<Integer, Integer> map = new MaxSizeHashMap<>(1);
        final LinkedHashMap<Integer, Integer> unlimitedMap = new LinkedHashMap<>();
        unlimitedMap.put(0, 0);
        unlimitedMap.put(1, 1);
        unlimitedMap.put(2, 2);

        map.putAll(unlimitedMap);

        // Map should not have more than a single element: 2
        assertThat(map)
                .hasSize(1)
                .doesNotContainKey(0)
                .doesNotContainKey(1)
                .containsKey(2);
    }
}
