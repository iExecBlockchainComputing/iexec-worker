/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

import java.util.Map;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class BaseFeignClient {

    /**
     * This is a generic functional interface to define an HTTP call.
     * T: type of the response body. It can be Void.
     * Object[]: array of the call arguments
     */
    public interface HttpCall<T> extends Function<Map<String, Object>, ResponseEntity<T>> {}

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
    <T> ResponseEntity<T> makeHttpCall(HttpCall<T> call, Map<String, Object> args, String action) {
        return makeHttpCall(call, args, action, false);
    }

    <T> ResponseEntity<T> makeHttpCall(HttpCall<T> call, Map<String, Object> args, String action, boolean infiniteRetry) {
        int attempt = 0;
        int status = -1;

        while (shouldRetry(infiniteRetry, attempt, status)) {
            try {
                return call.apply(args);
            } catch (FeignException e) {
                status = e.status();

                if (is4xxClientError(status) && args != null && args.containsKey("jwtoken")) {
                    // login and update token for the next call
                    String newJwToken = login();
                    args.put("jwtoken", newJwToken);
                }
            }

            attempt++;
            sleep(BACK_OFF_DELAY);
        }

        log.error("Failed to make http call [action:{}, status:{}, attempts:{}]",
                action, toHttpStatus(status), attempt);
        return ResponseEntity.status(status).build();
    }

    private boolean shouldRetry(boolean infiniteRetry, int attempt, int status) {
        return infiniteRetry || attempt < MAX_ATTEMPTS || status < 0;
    }

    private String toHttpStatus(int status) {
        if (status <= 0) {
            return String.valueOf(status);
        }

        return HttpStatus.valueOf(status).toString();
    }

    boolean is2xxSuccess(ResponseEntity<?> response) {
        int status = response.getStatusCodeValue();
        return status > 0 && HttpStatus.valueOf(status).is2xxSuccessful();
    }

    boolean is4xxClientError(int status) {
        return status > 0 && HttpStatus.valueOf(status).is4xxClientError();
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error("Error while sleeping", e);
        }
    }
}