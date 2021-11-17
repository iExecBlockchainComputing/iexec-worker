package com.iexec.worker.config;

import com.iexec.common.config.PublicChainConfig;
import com.iexec.worker.feign.CustomBlockchainAdapterClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class BlockchainAdapterConfigurationServiceTest {
    private static final Integer CHAIN_ID = 0;
    private static final boolean IS_SIDECHAIN = true;
    private static final String NODE_ADDRESS = "0x1";
    private static final String HUB_ADDRESS = "0x2";
    private static final Duration BLOCK_TIME = Duration.ofSeconds(1);

    @Mock
    private CustomBlockchainAdapterClient customBlockchainAdapterClient;

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldGetBlockTime() {
        when(customBlockchainAdapterClient.getPublicChainConfig()).thenReturn(
                PublicChainConfig.builder()
                        .chainId(CHAIN_ID)
                        .isSidechain(IS_SIDECHAIN)
                        .nodeAddress(NODE_ADDRESS)
                        .hubAddress(HUB_ADDRESS)
                        .blockTime(BLOCK_TIME)
                        .build()
        );

        BlockchainAdapterConfigurationService blockchainAdapterConfigurationService =
                new BlockchainAdapterConfigurationService(customBlockchainAdapterClient);

        assertThat(blockchainAdapterConfigurationService.getChainId())
                .isEqualTo(CHAIN_ID);
        assertThat(blockchainAdapterConfigurationService.isSidechain())
                .isEqualTo(IS_SIDECHAIN);
        assertThat(blockchainAdapterConfigurationService.getNodeAddress())
                .isEqualTo(NODE_ADDRESS);
        assertThat(blockchainAdapterConfigurationService.getHubAddress())
                .isEqualTo(HUB_ADDRESS);
        assertThat(blockchainAdapterConfigurationService.getBlockTime())
                .isEqualTo(BLOCK_TIME);
    }
}