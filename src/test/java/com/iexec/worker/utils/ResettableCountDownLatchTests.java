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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class ResettableCountDownLatchTests {

    // region await
    @Test
    void shouldWaitUntilReleased() {
        final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                fail("Interrupted while waiting");
            }
        });
        assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));

        latch.countDown();
        assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldBlockThreadUntilTimeout() {
        final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                fail("Interrupted while waiting");
            }
        });

        assertThrows(TimeoutException.class, () -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldCountDownAndReleaseThreadOnTimedMethod() throws ExecutionException, InterruptedException, TimeoutException {
        final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
        final CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("Interrupted while waiting");
                return false;
            }
        });
        latch.countDown();

        assertTrue(future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldBlockThreadOnTimeMethodUntilTimeout() {
        final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
        final CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("Interrupted while waiting");
                return false;
            }
        });

        assertThrows(TimeoutException.class, () -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldBlockThreadOnTimeMethodAndReturnFalse() throws ExecutionException, InterruptedException, TimeoutException {
        final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
        final CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return latch.await(1, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                fail("Interrupted while waiting");
                return false;
            }
        });

        assertFalse(future.get(1, TimeUnit.SECONDS));
    }
    // endregion

    // region reset
    @Test
    void shouldBeAbleToReleaseAgain() {
        final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
        final CompletableFuture<Void> firstFuture = CompletableFuture.runAsync(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                fail("Interrupted while waiting");
            }
        });
        latch.countDown();

        assertDoesNotThrow(() -> firstFuture.get(1, TimeUnit.SECONDS));

        latch.reset();

        final CompletableFuture<Void> secondFuture = CompletableFuture.runAsync(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                fail("Interrupted while waiting");
            }
        });
        // While it has not been released a second time, it should wait.
        assertThrows(TimeoutException.class, () -> secondFuture.get(1, TimeUnit.SECONDS));

        latch.countDown();
        assertDoesNotThrow(() -> secondFuture.get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldReleaseAllThreadsOnReset() {
        final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
        final List<CompletableFuture<Void>> waitingFutures = IntStream.range(0, 5).mapToObj(i -> CompletableFuture.runAsync(() -> {
            try {
                log.info("Waiting future n°{}", i);
                latch.await();
                log.info("Completing future n°{}", i);
            } catch (InterruptedException e) {
                fail("Interrupted while waiting");
            }
        })).collect(Collectors.toList());

        waitingFutures.forEach(future -> assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS)));

        latch.reset();

        waitingFutures.forEach(future -> assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS)));
    }
    // endregion
}
