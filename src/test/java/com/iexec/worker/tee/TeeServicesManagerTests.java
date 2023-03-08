package com.iexec.worker.tee;

import com.iexec.common.tee.TeeFramework;
import com.iexec.worker.tee.gramine.TeeGramineService;
import com.iexec.worker.tee.scone.TeeSconeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TeeServicesManagerTests {

    @Mock
    TeeSconeService teeSconeService;
    @Mock
    TeeGramineService teeGramineService;

    @InjectMocks
    TeeServicesManager teeServicesManager;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    static Stream<Arguments> teeServices() {
        return Stream.of(
                Arguments.of(TeeFramework.SCONE, TeeSconeService.class),
                Arguments.of(TeeFramework.GRAMINE, TeeGramineService.class)
        );
    }

    @ParameterizedTest
    @MethodSource("teeServices")
    void shouldReturnTeeService(TeeFramework framework, Class<? super TeeService> teeService) {
        assertInstanceOf(teeService, teeServicesManager.getTeeService(framework));
    }

    @Test
    void shouldThrowSinceNullProvider() {
        assertThrows(IllegalArgumentException.class, () -> teeServicesManager.getTeeService(null));
    }
}