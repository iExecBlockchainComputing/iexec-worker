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

import com.iexec.common.config.PublicChainConfig;
import com.iexec.worker.feign.client.BlockchainAdapterClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CustomBlockchainAdapterClient extends BaseFeignClient {
    private final BlockchainAdapterClient blockchainAdapterClient;

    /**
     * If the blockchain adapter url is malformed, this object won't be built.
     */
    public CustomBlockchainAdapterClient(BlockchainAdapterClient blockchainAdapterClient) {
        this.blockchainAdapterClient = blockchainAdapterClient;
    }

    @Override
    String login() {
        // No login is needed as of now.
        return null;
    }

    public PublicChainConfig getPublicChainConfig() {
        HttpCall<PublicChainConfig> httpCall = args -> blockchainAdapterClient.getPublicChainConfig();
        ResponseEntity<PublicChainConfig> response = makeHttpCall(httpCall, null, "getPublicChainConfig");
        return is2xxSuccess(response) ? response.getBody() : null;
    }

}
