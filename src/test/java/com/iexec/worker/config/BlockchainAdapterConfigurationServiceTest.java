package com.iexec.worker.config;

import com.iexec.common.config.PublicChainConfig;
import com.iexec.worker.feign.CustomBlockchainAdapterClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class BlockchainAdapterConfigurationServiceTest {
    @Mock
    private CustomBlockchainAdapterClient customBlockchainAdapterClient;

    @BeforeEach
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldGetBlockTime() {
        when(customBlockchainAdapterClient.getPublicChainConfig()).thenReturn(
                PublicChainConfig.builder().blockTime(Duration.ofSeconds(1)).build()
        );

        BlockchainAdapterConfigurationService blockchainAdapterConfigurationService =
                new BlockchainAdapterConfigurationService(customBlockchainAdapterClient);

        final Duration blockTime = blockchainAdapterConfigurationService.getBlockTime();
        assertThat(blockTime).isEqualTo(Duration.ofSeconds(1));
        assertThat(blockTime.toMillis()).isEqualTo(1000);
    }
}