/*
 * Copyright 2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.chain;

import com.iexec.commons.poco.chain.SignerService;
import com.iexec.worker.chain.event.LatestBlockEvent;
import com.iexec.worker.config.ConfigServerConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSubscribe;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.protocol.websocket.events.NewHeadsNotification;
import org.web3j.utils.Async;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class WebSocketBlockchainListener {

    static final String LATEST_BLOCK_METRIC_NAME = "iexec.chain.block.latest";
    static final String TX_COUNT_METRIC_NAME = "iexec.chain.wallet.tx-count";

    private static final String SUBSCRIBE_METHOD = "eth_subscribe";
    private static final String UNSUBSCRIBE_METHOD = "eth_unsubscribe";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final String walletAddress;
    private final Web3j web3Client;
    private final WebSocketService webSocketService;
    private final AtomicLong lastSeenBlock;
    private final AtomicLong latestTxGauge;
    private final AtomicLong pendingTxGauge;
    private Disposable newHeads;

    public WebSocketBlockchainListener(final ApplicationEventPublisher applicationEventPublisher,
                                       final ConfigServerConfigurationService configServerConfigurationService,
                                       final SignerService signerService,
                                       final WorkerConfigurationService workerConfigurationService) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.walletAddress = signerService.getAddress();
        final String nodeUrl = !workerConfigurationService.getOverrideBlockchainNodeAddress().isEmpty() ?
                workerConfigurationService.getOverrideBlockchainNodeAddress() :
                configServerConfigurationService.getChainNodeUrl();
        final String wsUrl = nodeUrl.replace("http", "ws");
        this.webSocketService = new WebSocketService(wsUrl, false);
        this.web3Client = Web3j.build(new HttpService(nodeUrl),
                configServerConfigurationService.getBlockTime().toMillis(), Async.defaultExecutorService());
        lastSeenBlock = Metrics.gauge(LATEST_BLOCK_METRIC_NAME, new AtomicLong(0));
        latestTxGauge = Metrics.gauge(TX_COUNT_METRIC_NAME, List.of(Tag.of("block", "latest")), new AtomicLong(0));
        pendingTxGauge = Metrics.gauge(TX_COUNT_METRIC_NAME, List.of(Tag.of("block", "pending")), new AtomicLong(0));
    }

    @Scheduled(fixedRate = 5000)
    public void run() throws ConnectException {
        if (newHeads != null && !newHeads.isDisposed()) {
            return;
        }

        log.warn("web socket disconnection detected");
        webSocketService.connect();

        final Request<?, EthSubscribe> newHeadsRequest = new Request<>(
                SUBSCRIBE_METHOD,
                List.of("newHeads", Map.of()),
                webSocketService,
                EthSubscribe.class
        );

        final Flowable<NewHeadsNotification> newHeadsEvents = webSocketService.subscribe(
                newHeadsRequest, UNSUBSCRIBE_METHOD, NewHeadsNotification.class);
        newHeads = newHeadsEvents.subscribe(this::processHead, this::handleError);
    }

    private void processHead(final NewHeadsNotification event) throws IOException {
        final long blockNumber = Numeric.toBigInt(event.getParams().getResult().getNumber()).longValue();
        final String blockHash = event.getParams().getResult().getHash();
        final long blockTimestamp = Numeric.toBigInt(event.getParams().getResult().getTimestamp()).longValue();
        lastSeenBlock.set(blockNumber);
        final BigInteger pendingTxCount = web3Client.ethGetTransactionCount(walletAddress,
                DefaultBlockParameterName.PENDING).send().getTransactionCount();
        final BigInteger latestTxCount = web3Client.ethGetTransactionCount(walletAddress,
                DefaultBlockParameterName.LATEST).send().getTransactionCount();
        log.info("Transaction count [block:{}, pending:{}, latest:{}]",
                lastSeenBlock, pendingTxCount, latestTxCount);
        pendingTxGauge.set(pendingTxCount.longValue());
        latestTxGauge.set(latestTxCount.longValue());
        applicationEventPublisher.publishEvent(new LatestBlockEvent(this, blockNumber, blockHash, blockTimestamp));
    }

    private void handleError(final Throwable t) {
        log.error("An error happened during subscription", t);
    }

}
