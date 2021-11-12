package com.iexec.worker.config;

import com.iexec.worker.feign.CustomBlockchainAdapterClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class BlockchainAdapterConfigurationServiceTest {
    @Mock
    private CustomBlockchainAdapterClient customBlockchainAdapterClient;

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldGetBlockTime() {
        when(customBlockchainAdapterClient.getBlockTime()).thenReturn(1);

        BlockchainAdapterConfigurationService blockchainAdapterConfigurationService =
                new BlockchainAdapterConfigurationService(customBlockchainAdapterClient);

        final Duration blockTime = blockchainAdapterConfigurationService.getBlockTime();
        assertThat(blockTime).isEqualTo(Duration.ofSeconds(1));
        assertThat(blockTime.toMillis()).isEqualTo(1000);
    }
}