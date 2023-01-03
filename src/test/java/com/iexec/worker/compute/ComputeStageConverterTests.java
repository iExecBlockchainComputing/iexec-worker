package com.iexec.worker.compute;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.iexec.worker.compute.ComputeStage.POST;
import static com.iexec.worker.compute.ComputeStage.PRE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComputeStageConverterTests {
    private final ComputeStageConverter computeStageConverter = new ComputeStageConverter();

    static Stream<Arguments> correctConversions() {
        return Stream.of(
                Arguments.of("PRE", PRE),
                Arguments.of("Pre", PRE),
                Arguments.of("pre", PRE),
                Arguments.of("POST", POST),
                Arguments.of("Post", POST),
                Arguments.of("post", POST)
        );
    }

    @ParameterizedTest
    @MethodSource("correctConversions")
    void shouldConvert(String name, ComputeStage expectedValue) {
        assertEquals(expectedValue, computeStageConverter.convert(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "app"})
    void shouldRejectConversion(String name) {
        assertThrows(IllegalArgumentException.class, () -> computeStageConverter.convert(name));
    }
}