/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.sgx;

import com.github.dockerjava.api.model.Device;
import com.iexec.commons.containers.SgxDriverMode;
import com.iexec.worker.docker.DockerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ExtendWith(OutputCaptureExtension.class)
class SgxServiceTests {
    private static final String WORKER_WALLET_ADDRESS = "0x2D29bfBEc903479fe4Ba991918bAB99B494f2bEf";

    @Autowired
    private ApplicationContext context;
    @Autowired
    private DockerService dockerService;

    @Test
    void modeNone(CapturedOutput output) {
        SgxService sgxService = new SgxService(context, dockerService, SgxDriverMode.NONE, WORKER_WALLET_ADDRESS);
        sgxService.init();
        assertAll(
                () -> assertThat(sgxService.isSgxEnabled()).isFalse(),
                () -> assertThat(output.getAll()).contains("No SGX driver defined, skipping SGX check [sgxDriverMode:NONE]")
        );
    }

    // region getSgxDevices
    @ParameterizedTest
    @MethodSource("sgxDevicesCheckParams")
    void checkGetSgxDevices(SgxDriverMode driverMode, List<Device> expectedDevices) {
        SgxService sgxService = new SgxService(context, dockerService, driverMode, WORKER_WALLET_ADDRESS);
        assertThat(sgxService.getSgxDevices()).isEqualTo(expectedDevices);
    }

    static Stream<Arguments> sgxDevicesCheckParams() {
        return Stream.of(
                Arguments.of(SgxDriverMode.NONE, List.of())
        );
    }
    // endregion

}
