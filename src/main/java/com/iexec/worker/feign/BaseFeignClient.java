package com.iexec.worker.feign;

import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

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


    private final int MAX_ATTEMPTS = 5;
    private final int BACK_OFF_DELAY = 2000; // 2s

    @Retryable(value = FeignException.class,
               maxAttempts = MAX_ATTEMPTS, 
               backoff = @Backoff(delay = BACK_OFF_DELAY))

    <T> ResponseEntity<T> makeHttpCall(HttpCall<T> call, Object[] args, String action) {
        try {
            ResponseEntity<T> response = call.apply(args);
            return response;
        } catch (FeignException e) {
            if (isUnauthorized(e.status())) {
                login();
                // return httpCallFunction(function, args, action);
            }

            throw e;
        }
    }

    @Recover
    private <T> ResponseEntity<T> makeHttpCall(FeignException e, HttpCall<T> call, Object[] args, String action) {
        log.error("Failed while making http call [action:{}, status:{}, httpCallArgs:{}, attempts:{}]",
                action, HttpStatus.valueOf(e.status()), args, MAX_ATTEMPTS);
        e.printStackTrace();
        return ResponseEntity.status(e.status()).build();
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