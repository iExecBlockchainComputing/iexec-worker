package com.iexec.worker.feign;

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
    public interface HttpCall<T> extends Function<Object[], ResponseEntity<T>> {}

    /*
     * This method should be overridden in
     * the subclass to define the login logic.
     */
    abstract boolean login();

    /*
     * Retry configuration values (max attempts, back off delay...) 
     */
    private final int MAX_ATTEMPTS = 3;
    private final int BACK_OFF_DELAY = 2000; // 2s

    /*
     * Generic method to make http calls.
     * If "infiniteRetry" is true or the status of the call is -1
     * the method will retry infinitely until it gets a valid
     * response.
     */
    <T> ResponseEntity<T> makeHttpCall(HttpCall<T> call, Object[] args, String action) {
        return makeHttpCall(call, args, action, false);
    }

    <T> ResponseEntity<T> makeHttpCall(HttpCall<T> call, Object[] args, String action, boolean infiniteRetry) {
        int attempt = 0;
        int status = -1;

        while (shouldRetry(infiniteRetry, attempt, status)) {
            try {
                return call.apply(args);
            } catch (FeignException e) {
                status = e.status();

                log.error("Failed to make http call [action:{}, status:{}, attempt:{}]",
                        action, toHttpStatus(e.status()), attempt);

                if (isUnauthorized(e.status())) {
                    login();
                }
            }

            attempt++;
            sleep(BACK_OFF_DELAY);
        }

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

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error("Error while sleeping");
            e.printStackTrace();
        }
    }

    boolean isOk(ResponseEntity<?> response) {
        return isOk(response.getStatusCodeValue());
    }

    boolean isOk(int status) {
        return status > 0 && HttpStatus.valueOf(status).is2xxSuccessful();
    }

    boolean isUnauthorized(int status) {
        return status > 0 && HttpStatus.valueOf(status).equals(HttpStatus.UNAUTHORIZED);
    }

    boolean isForbidden(int status) {
        return status > 0 && HttpStatus.valueOf(status).equals(HttpStatus.FORBIDDEN);
    }
}