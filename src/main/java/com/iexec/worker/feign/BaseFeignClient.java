package com.iexec.worker.feign;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import feign.FeignException;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class BaseFeignClient {

    private final int MAX_ATTEMPTS = 3;
    private final int BACK_OFF_DELAY = 3000; // 3s

    /*
     * This method should be overridden in the subclass. 
     */
    abstract void login();


    @Retryable(value = FeignException.class, maxAttempts = MAX_ATTEMPTS,
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

        try {
            Function<Object[], ResponseEntity<R>> function = (Function<Object[], ResponseEntity<R>>) method;


        } catch (FeignException e) {
            if (isUnauthorized(e.status())) {
                login();
                makeHttpCall(method, args, action);
            }

            throw e;
        }
    }

    @Recover
    private <R, T> ResponseEntity<R> makeHttpCall(FeignException e, T method, Object[] args, String action) {
        log.error("Failed while making http call [action:{}, status:{}, httpCallArgs:{}]",
                action, HttpStatus.valueOf(e.status()), args);
        return null;
    }







    /*
     * Consumer call: one argument, no return 
     */
    @Retryable(value = FeignException.class, maxAttempts = MAX_ATTEMPTS,
            backoff = @Backoff(delay = BACK_OFF_DELAY))
    void httpCallConsumer(Consumer<Object[]> consumer, Object[] args, String action) {
        try {
            consumer.accept(args);
        } catch (FeignException e) {
            if (isUnauthorized(e.status())) {
                login();
                httpCallConsumer(consumer, args, action);
            }

            throw e;
        }
    }

    @Recover
    void httpCallConsumer(FeignException e,
                          Consumer<Object[]> consumer,
                          Object[] args,
                          String action) {
        log.error("Failed while making http call [action:{}, status:{}, httpCallArgs:{}]",
                action, HttpStatus.valueOf(e.status()), args);
    }

    /*
     * Supplier call: no arguments, with return 
     */
    @Retryable(value = FeignException.class, maxAttempts = MAX_ATTEMPTS,
            backoff = @Backoff(delay = BACK_OFF_DELAY))
    <T> Optional<ResponseEntity<T>> httpCallSupplier(Supplier<ResponseEntity<T>> supplier, String action) {
        try {
            return Optional.of(supplier.get());
        } catch (FeignException e) {
            if (isUnauthorized(e.status())) {
                login();
                httpCallSupplier(supplier, args, action);
            }

            throw e;
        }
    }

    @Recover
    Optional<ResponseEntity> httpCallSupplier(FeignException e,
                          Supplier<ResponseEntity> supplier,
                          String action) {
        log.error("Failed while making http call [action:{}, status:{}, httpCallArgs:{}]",
                action, HttpStatus.valueOf(e.status()), args);
        return Optional.empty();
    }

    /*
     * Functional call: one argument, with return 
     */
    @Retryable(value = FeignException.class, maxAttempts = MAX_ATTEMPTS,
            backoff = @Backoff(delay = BACK_OFF_DELAY))
    Optional<ResponseEntity> httpCallFunction(Function<Object[], ResponseEntity> function,
                                          Object[] args,
                                          String action) {
        try {
            ResponseEntity response = function.apply(args);
            return Optional.of(response);
        } catch (FeignException e) {
            if (isUnauthorized(e.status())) {
                login();
                // return httpCallFunction(function, args, action);
            }

            throw e;
        }
    }

    @Recover
    ResponseEntity httpCallFunction(FeignException e,
                                              Function<Object[], ResponseEntity> function,
                                              Object[] args,
                                              String action) {
        log.error("Failed while making http call [action:{}, status:{}, httpCallArgs:{}]",
                action, HttpStatus.valueOf(e.status()), args);
        return ResponseEntity.status(e.status());
    }

    boolean isOk(int status) {
        return status > 0 && HttpStatus.valueOf(status).equals(HttpStatus.OK);
    }

    boolean isUnauthorized(int status) {
        return status > 0 && HttpStatus.valueOf(status).equals(HttpStatus.UNAUTHORIZED);
    }

    boolean isForbidden(int status) {
        return status > 0 && HttpStatus.valueOf(status).equals(HttpStatus.FORBIDDEN);
    }


    // public void makeHttpCall(HttpCall f, Object[] args) {
        // get token
        // if unauth login and retry
        // if other retry for n times

        // coreClient.getPublicConfiguration        ();
        // coreClient.getCoreVersion                ();
        // coreClient.registerWorker                (token, model);
        // coreClient.getMissedTaskNotifications    (token, lastAvailableBlockNumber);
        // coreClient.getAvailableReplicate         (token, lastAvailableBlockNumber);
        // coreClient.updateReplicateStatus         (token, chainTaskId, details);

        // resultClient.getChallenge                (chainId)
        // resultClient.uploadResult                (token, resultModel)

        // smsClient.getTaskSecretsFromSms          (smsRequest)
        // smsClient.generateSecureSession          (smsRequest)
    // }
}