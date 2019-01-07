package com.iexec.worker.config;

import com.iexec.common.config.PublicConfiguration;
import com.iexec.worker.feign.CustomFeignClient;
import org.springframework.stereotype.Service;

@Service("publicConfigurationService")
public class PublicConfigurationService {

    public long askForReplicatePeriod;

    public PublicConfigurationService(CustomFeignClient customFeignClient){
        PublicConfiguration publicConfiguration = customFeignClient.getPublicConfiguration();
        askForReplicatePeriod = publicConfiguration.getAskForReplicatePeriod();
    }
}
