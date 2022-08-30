package com.iexec.worker.sms;

import com.iexec.sms.api.SmsClientProvider;
import com.iexec.worker.chain.IexecHubService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsClientProviderConfiguration {
    private final IexecHubService iexecHubService;

    public SmsClientProviderConfiguration(IexecHubService iexecHubService) {
        this.iexecHubService = iexecHubService;
    }

    @Bean
    SmsClientProvider smsClientProvider() {
        return new SmsClientProvider(iexecHubService);
    }
}
