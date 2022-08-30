package com.iexec.worker.tee.gramine;

import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeWorkflowConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeeGramineServiceTests {
    private static final String SESSION_ID = "0x123_session_id";
    private static final String SPS_URL = "http://spsUrl";
    private static final TeeSessionGenerationResponse TEE_SESSION_GENERATION_RESPONSE = new TeeSessionGenerationResponse(
            SESSION_ID,
            SPS_URL
    );

    @Mock
    SgxService sgxService;
    @Mock
    SmsClientProvider smsClientProvider;
    @Mock
    TeeWorkflowConfigurationService teeWorkflowConfigurationService;

    @InjectMocks
    TeeGramineService teeGramineService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region prepareTeeForTask
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x123", "chainTaskId"})
    void shouldPrepareTeeForTask(String chainTaskId) {
        assertTrue(teeGramineService.prepareTeeForTask(chainTaskId));

        verifyNoInteractions(sgxService, smsClientProvider, teeWorkflowConfigurationService);
    }
    // endregion

    // region buildPreComputeDockerEnv
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x123", "chainTaskId"})
    void shouldBuildPreComputeDockerEnv(String chainTaskId) {
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(chainTaskId).build();
        final List<String> env = teeGramineService.buildPreComputeDockerEnv(taskDescription, TEE_SESSION_GENERATION_RESPONSE);

        assertEquals(2, env.size());
        assertTrue(env.containsAll(List.of(
                "sps=http://spsUrl",
                "session=0x123_session_id"
        )));
    }
    // endregion

    // region buildComputeDockerEnv
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x123", "chainTaskId"})
    void shouldBuildComputeDockerEnv(String chainTaskId) {
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(chainTaskId).build();
        final List<String> env = teeGramineService.buildComputeDockerEnv(taskDescription, TEE_SESSION_GENERATION_RESPONSE);

        assertEquals(2, env.size());
        assertTrue(env.containsAll(List.of(
                "sps=http://spsUrl",
                "session=0x123_session_id"
        )));
    }
    // endregion

    // region buildPostComputeDockerEnv
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x123", "chainTaskId"})
    void shouldBuildPostComputeDockerEnv(String chainTaskId) {
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(chainTaskId).build();
        final List<String> env = teeGramineService.buildPostComputeDockerEnv(taskDescription, TEE_SESSION_GENERATION_RESPONSE);

        assertEquals(2, env.size());
        assertTrue(env.containsAll(List.of(
                "sps=http://spsUrl",
                "session=0x123_session_id"
        )));
    }
    // endregion

    // region getAdditionalBindings
    @Test
    void shouldGetAdditionalBindings() {
        final Collection<String> bindings = teeGramineService.getAdditionalBindings();

        assertEquals(1, bindings.size());
        assertTrue(bindings.contains("/var/run/aesmd/aesm.socket:/var/run/aesmd/aesm.socket"));
    }
    // endregion
}