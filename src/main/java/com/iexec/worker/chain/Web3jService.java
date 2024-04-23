/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.chain.Web3jAbstractService;
import com.iexec.worker.config.ConfigServerConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import org.springframework.stereotype.Service;

@Service
public class Web3jService extends Web3jAbstractService {

    public Web3jService(ConfigServerConfigurationService configServerConfigurationService,
                        WorkerConfigurationService workerConfService) {
        super(
                configServerConfigurationService.getChainId(),
                !workerConfService.getOverrideBlockchainNodeAddress().isEmpty() ?
                        workerConfService.getOverrideBlockchainNodeAddress() :
                        configServerConfigurationService.getChainNodeUrl(),
                configServerConfigurationService.getBlockTime(),
                workerConfService.getGasPriceMultiplier(),
                workerConfService.getGasPriceCap(),
                configServerConfigurationService.isSidechain()
        );
    }

}
