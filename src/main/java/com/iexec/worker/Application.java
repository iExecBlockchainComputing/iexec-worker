/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker;

import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.LoginService;
import com.iexec.worker.replicate.ReplicateRecoveryService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.utils.LoggingUtils;
import com.iexec.worker.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;

@Slf4j
@EnableRetry
@EnableAsync
@SpringBootApplication
@Profile("!test")
@ConfigurationPropertiesScan
public class Application implements CommandLineRunner {

    private final String workerWalletAddress;
    private final IexecHubService iexecHubService;
    private final LoginService loginService;
    private final WorkerService workerService;
    private final ReplicateRecoveryService replicateRecoveryService;
    private final ResultService resultService;

    public Application(String workerWalletAddress,
                       IexecHubService iexecHubService,
                       LoginService loginService,
                       WorkerService workerService,
                       ReplicateRecoveryService replicateRecoveryService,
                       ResultService resultService) {
        this.workerWalletAddress = workerWalletAddress;
        this.iexecHubService = iexecHubService;
        this.loginService = loginService;
        this.workerService = workerService;
        this.replicateRecoveryService = replicateRecoveryService;
        this.resultService = resultService;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) {
        if (!iexecHubService.hasEnoughGas()) {
            String noEnoughGas = "No enough gas! please refill your wallet [walletAddress:%s]";
            String formatted = String.format(noEnoughGas, workerWalletAddress);
            LoggingUtils.printHighlightedMessage(formatted);
            System.exit(0);
        }

        if (StringUtils.isEmpty(loginService.login())) {
            String message = "Worker wasn't able to login, stopping...";
            LoggingUtils.printHighlightedMessage(message);
            System.exit(0);
        }

        if (!workerService.registerWorker()) {
            String message = "Worker couldn't be registered, stopping...";
            LoggingUtils.printHighlightedMessage(message);
            System.exit(0);
        }

        log.info("Cool, your iexec-worker is all set!");

        // recover interrupted replicates
        List<String> recoveredTasks = replicateRecoveryService.recoverInterruptedReplicates();

        // clean the results folder
        resultService.cleanUnusedResultFolders(recoveredTasks);
    }
}
