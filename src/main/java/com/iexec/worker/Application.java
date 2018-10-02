package com.iexec.worker;

import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.worker.feign.CoreClient;
import com.iexec.worker.utils.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@Slf4j
public class Application implements CommandLineRunner {

	@Value("${core.address}")
	private String coreAddress;

	@Autowired
	private CoreClient coreClient;

	@Autowired
    private WorkerConfigurationService workerConfig;

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

        log.info("Address of the core [address:{}]", coreAddress);
        log.info("Version of the core [version:{}]", coreClient.getCoreVersion());
        log.info("Registering the worker to the core [worker:{}]", model);
        coreClient.registerWorker(model);
    }
}
