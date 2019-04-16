package com.iexec.worker.result;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.worker.feign.ResultRepoClient;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
public class ResultRepoService {

    private static final int BACKOFF = 5000; // 5s
    private static final int MAX_ATTEMPS = 5;


    private ResultRepoClient resultRepoClient;

    public ResultRepoService(ResultRepoClient resultRepoClient) {
        this.resultRepoClient = resultRepoClient;
    }

    @Retryable (
        value = {FeignException.class},
        maxAttempts = MAX_ATTEMPS,
        backoff = @Backoff(delay = BACKOFF)
    )
    public Optional<Eip712Challenge> getChallenge(Integer chainId) {
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
    public String uploadResult(String authorizationToken, ResultModel resultModel) {
        ResponseEntity<String> responseEntity =
                resultRepoClient.uploadResult(authorizationToken, resultModel);
        
        return responseEntity.getStatusCode().is2xxSuccessful()
                ? responseEntity.getBody()
                : "";
    }

    @Recover
    public String uploadResult(FeignException e, String authorizationToken, ResultModel resultModel) {
        log.error("Failed to upload result [attempts:{}]", MAX_ATTEMPS);
        e.printStackTrace();
        return "";
    }
}