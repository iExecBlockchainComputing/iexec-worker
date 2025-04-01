/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.feign;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.function.Function;

@Slf4j
public abstract class BaseFeignClient {

    /**
     * This is a generic functional interface to define an HTTP call.
     * T: type of the response body. It can be Void.
     * Object[]: array of the call arguments
     */
    public interface HttpCall<T> extends Function<String, T> {
    }

    /*
     * This method should be overridden in
     * the subclass to define the login logic.
     */
    abstract String login();

    /*
     * Retry configuration values (max attempts, back off delay...)
     */
    private static final int MAX_ATTEMPTS = 3;
    private static final int BACK_OFF_DELAY = 2000; // 2s

    /*
     * Generic method to make http calls.
     * If "infiniteRetry" is true or the status of the call is -1
     * the method will retry infinitely until it gets a valid
     * response.
     */
    <T> T makeHttpCall(HttpCall<T> call, String action, String jwtToken, T defaultValueOnRepeatedFailures) {
        int attempt = 0;
        int status = -1;

        while (shouldRetry(attempt, status)) {
            try {
                return call.apply(jwtToken);
            } catch (FeignException e) {
                status = e.status();

                final boolean containsJwt = jwtToken != null;
                if (e instanceof FeignException.Unauthorized && containsJwt) {
                    // login and update token for the next call
                    jwtToken = login();
                }
            }

            attempt++;
            log.error("Failed to make http call [action:{}, status:{}, attempt:{}]",
                    action, toHttpStatus(status), attempt);
            sleep(BACK_OFF_DELAY);
        }

        log.error("Failed to make http call [action:{}, status:{}, attempts:{}]",
                action, toHttpStatus(status), attempt);
        return defaultValueOnRepeatedFailures;
    }

    private boolean shouldRetry(int attempt, int status) {
        return attempt < MAX_ATTEMPTS || status < 0;
    }

    private String toHttpStatus(int status) {
        if (status <= 0) {
            return String.valueOf(status);
        }

        return HttpStatus.valueOf(status).toString();
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error("Thread has been interrupted while sleeping", e);
            Thread.currentThread().interrupt();
        }
    }
}
