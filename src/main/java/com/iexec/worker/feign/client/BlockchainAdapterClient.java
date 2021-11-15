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

package com.iexec.worker.feign.client;

import com.iexec.common.config.PublicChainConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;

// Blockchain adapter URL is dynamic, so it can't be known at compile-time.
// We have to use a trick to make this work:
// give a placeholder to `@FeignClient` annotation and send the url as a parameter in each request.
@FeignClient(name = "BlockchainAdapterClient", url = "http://placeholder.iex.ec")
public interface BlockchainAdapterClient {
    @GetMapping("/config/chain")
    ResponseEntity<PublicChainConfig> getPublicChainConfig(URI baseUrl);
}
