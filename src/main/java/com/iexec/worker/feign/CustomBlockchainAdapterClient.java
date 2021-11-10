/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.feign.client.BlockchainAdapterClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Service
public class CustomBlockchainAdapterClient extends BaseFeignClient {
    private final URI blockchainAdapterUri;
    private final BlockchainAdapterClient blockchainAdapterClient;

    /**
     * If the blockchain adapter url is malformed, this object won't be built.
     */
    public CustomBlockchainAdapterClient(PublicConfigurationService publicConfigurationService,
                                         BlockchainAdapterClient blockchainAdapterClient) throws URISyntaxException {
        this.blockchainAdapterClient = blockchainAdapterClient;
        try {
            this.blockchainAdapterUri = new URI(publicConfigurationService.getBlockchainAdapterUrl());
        } catch (URISyntaxException e) {
            log.error("Wrong blockchain adapter URL [blockchainAdapterUrl:{}]",
                    publicConfigurationService.getBlockchainAdapterUrl());
            throw e;
        }
    }

    @Override
    String login() {
        // No login is needed as of now.
        return null;
    }

    public Integer getBlockTime() {
        HttpCall<Integer> httpCall = args -> blockchainAdapterClient.getBlockTime(blockchainAdapterUri);
        ResponseEntity<Integer> response = makeHttpCall(httpCall, null, "getBlockTime");
        return is2xxSuccess(response) ? response.getBody() : null;
    }

}
