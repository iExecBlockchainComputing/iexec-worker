/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.tee;

import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.worker.tee.gramine.TeeGramineService;
import com.iexec.worker.tee.scone.TeeSconeService;
import com.iexec.worker.tee.tdx.TeeTdxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class TeeServicesManagerTests {

    @Mock
    TeeTdxService teeTdxService;
    @Mock
    TeeSconeService teeSconeService;
    @Mock
    TeeGramineService teeGramineService;

    @InjectMocks
    TeeServicesManager teeServicesManager;

    static Stream<Arguments> teeServices() {
        return Stream.of(
                Arguments.of(TeeFramework.TDX, TeeTdxService.class),
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