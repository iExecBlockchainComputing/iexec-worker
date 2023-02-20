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

package com.iexec.worker.utils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Work as a {@link CountDownLatch} that can be reset.
 * <p>
 * Internally, this wraps a standard {@link CountDownLatch}.
 * When it is reset, then the current latch is released and a new latch is created.
 * This means all waiting threads will awake. This avoids them from being paused forever.
 */
public class ResettableCountDownLatch {
    private final int initialCount;
    // Required to achieve thread-safety on the `reset` method
    private final AtomicReference<CountDownLatch> latchHolder;

    public ResettableCountDownLatch(int  count) {
        initialCount = count;
        latchHolder = new AtomicReference<>(new CountDownLatch(count));
    }

    /**
     * Unblock all waiting threads and reset the count to the initial count.
     */
    public void reset() {
        final CountDownLatch oldLatch = latchHolder.getAndSet(new CountDownLatch(initialCount));
        if (oldLatch != null) {
            // Checking the count each time to prevent unnecessary countdowns due to parallel countdowns
            while (0L < oldLatch.getCount()) {
                oldLatch.countDown();
            }
        }
    }

    /**
     * @see CountDownLatch#countDown()
     */
    public void countDown() {
        latchHolder.get().countDown();
    }

    /**
     * @see CountDownLatch#await()
     */
    public void await() throws InterruptedException {
        latchHolder.get().await();
    }

    /**
     * @see CountDownLatch#await(long, TimeUnit)
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return latchHolder.get().await(timeout, unit);
    }
}
