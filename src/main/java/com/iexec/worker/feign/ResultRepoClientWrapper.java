package com.iexec.worker.feign;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
@Slf4j
public class ResultRepoClientWrapper {

    private static final int BACKOFF = 5000; // 5s
    private static final int MAX_ATTEMPS = 5;


    private ResultRepoClient resultRepoClient;

    public ResultRepoClientWrapper(ResultRepoClient resultRepoClient) {
        this.resultRepoClient = resultRepoClient;
    }

    @Retryable (
        value = {FeignException.class},
        maxAttempts = MAX_ATTEMPS,
        backoff = @Backoff(delay = BACKOFF)
    )
    public Optional<Eip712Challenge> getResultRepoChallenge(Integer chainId) {
            return Optional.of(resultRepoClient.getChallenge(chainId));
    }

    @Recover
    public Optional<Eip712Challenge> getResultRepoChallenge(FeignException e, Integer chainId) {
        log.error("Failed to getResultRepoChallenge [attempts:{}]", MAX_ATTEMPS);
        e.printStackTrace();
        return Optional.empty();
    }

    @Retryable (
        value = {FeignException.class},
        maxAttempts = MAX_ATTEMPS,
        backoff = @Backoff(delay = BACKOFF)
    )
    public boolean uploadResult(String authorizationToken, ResultModel resultModel) {
            return resultRepoClient.uploadResult(authorizationToken, resultModel)
                    .getStatusCode()
                    .is2xxSuccessful();
    }

    @Recover
    public boolean uploadResult(FeignException e, String authorizationToken, ResultModel resultModel) {
        log.error("Failed to upload result [attempts:{}]", MAX_ATTEMPS);
        e.printStackTrace();
        return false;
    }
}