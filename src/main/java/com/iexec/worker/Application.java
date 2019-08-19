package com.iexec.worker;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.replicate.ReplicateRecoveryService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.LoggingUtils;
import com.iexec.worker.utils.version.VersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;


@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableRetry
@EnableAsync
@Slf4j
public class Application implements CommandLineRunner {

    @Autowired
    private CoreConfigurationService coreConfService;

    @Autowired
    private WorkerConfigurationService workerConfig;

    @Autowired PublicConfigurationService publicConfigService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private CustomFeignClient customFeignClient;

    @Autowired
    private IexecHubService iexecHubService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private ReplicateRecoveryService replicateRecoveryService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private SconeTeeService sconeTeeService;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * IMPORTANT: By default the size of the ThreadPool that is managing all the @Scheduled methods is 1.
     * For us this is a problem since we want those methods to run in different threads in parallel, so we need to
     * declare this method to set the size of the ThreadPoolTaskScheduler. If an @Scheduled method is added in the
     * project, the pool size should be increased.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        return scheduler;
    }

    @Override
    public void run(String... args) {
        log.info("Number of tasks that can run in parallel on this machine [tasks:{}]", workerConfig.getNbCPU() / 2);
        log.info("Core URL [url:{}]", coreConfService.getUrl());
        log.info("Core version [version:{}]", customFeignClient.getCoreVersion());
        log.info("Getting public configuration from the core");
        PublicConfiguration publicConfiguration = publicConfigService.getPublicConfiguration();
        log.info("Got public configuration from the core [config:{}]", publicConfiguration);

        if (!publicConfiguration.getRequiredWorkerVersion().isEmpty() &&
                !versionService.getVersion().equals(publicConfiguration.getRequiredWorkerVersion())) {

            String badVersion = String.format("Bad version! please upgrade your iexec-worker [current:%s, required:%s]",
                    versionService.getVersion(), publicConfiguration.getRequiredWorkerVersion());

            LoggingUtils.printHighlightedMessage(badVersion);
            System.exit(0);
        }

        if (workerConfig.getHttpProxyHost() != null && workerConfig.getHttpProxyPort() != null) {
            log.info("Running with proxy [proxyHost:{}, proxyPort:{}]", workerConfig.getHttpProxyHost(), workerConfig.getHttpProxyPort());
        }

        String workerAddress = credentialsService.getCredentials().getAddress();

        if (!iexecHubService.hasEnoughGas()) {
            String noEnoughGas = String.format("No enough gas! please refill your wallet [walletAddress:%s]", workerAddress);
            LoggingUtils.printHighlightedMessage(noEnoughGas);
            System.exit(0);
        }

        WorkerModel model = WorkerModel.builder()
                .name(workerConfig.getWorkerName())
                .walletAddress(workerAddress)
                .os(workerConfig.getOS())
                .cpu(workerConfig.getCPU())
                .cpuNb(workerConfig.getNbCPU())
                .memorySize(workerConfig.getMemorySize())
                .teeEnabled(sconeTeeService.isTeeEnabled())
                .build();

        customFeignClient.registerWorker(model);
        log.info("Registered the worker to the core [worker:{}]", model);

        log.info("Cool, your iexec-worker is all set!");

        // ask core for interrupted replicates
        List<String> recoveredTasks = replicateRecoveryService.recoverInterruptedReplicates();

        // clean the results folder
        resultService.cleanUnusedResultFolders(recoveredTasks);
    }
}
