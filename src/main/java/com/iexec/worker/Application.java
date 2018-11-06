package com.iexec.worker;


import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.CoreWorkerClient;
import com.iexec.worker.utils.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.web3j.crypto.Credentials;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@Slf4j
public class Application implements CommandLineRunner {

    @Value("${core.host}")
    private String coreHost;

    @Value("${core.port}")
    private String corePort;

    @Autowired
    private CoreWorkerClient coreWorkerClient;

    @Autowired
    private WorkerConfigurationService workerConfig;

    @Autowired
    private CredentialsService credentialsService;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        WorkerConfigurationModel model = WorkerConfigurationModel.builder()
                .name(workerConfig.getWorkerName())
                .os(workerConfig.getOS())
                .cpu(workerConfig.getCPU())
                .cpuNb(workerConfig.getNbCPU())
                .build();

        log.info("Configuration of the worker [configuration:{}]", model);
        log.info("Number of tasks that can run in parallel on this machine [tasks:{}]", workerConfig.getNbCPU() / 2);
        log.info("Address of the core [address:{}]", "http://" + coreHost + ":" + corePort);
        log.info("Version of the core [version:{}]", coreWorkerClient.getCoreVersion());
        log.info("Get configuration of the core [config:{}]", coreWorkerClient.getPublicConfiguration());

        log.info("Registering the worker to the core [worker:{}]", model);
        coreWorkerClient.registerWorker(model);

        Credentials credentials = credentialsService.getCredentials();

    }
}
