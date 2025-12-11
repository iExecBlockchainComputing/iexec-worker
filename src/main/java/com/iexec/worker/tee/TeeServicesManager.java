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
import org.springframework.stereotype.Service;

@Service
public class TeeServicesManager {

    private final TeeTdxService teeTdxService;
    private final TeeSconeService teeSconeService;
    private final TeeGramineService teeGramineService;

    public TeeServicesManager(final TeeTdxService teeTdxService,
                              final TeeSconeService teeSconeService,
                              final TeeGramineService teeGramineService) {
        this.teeTdxService = teeTdxService;
        this.teeSconeService = teeSconeService;
        this.teeGramineService = teeGramineService;
    }

    public TeeService getTeeService(final TeeFramework teeFramework) {
        if (teeFramework == null) {
            throw new IllegalArgumentException("TEE framework can't be null.");
        }

        return switch (teeFramework) {
            case TDX -> teeTdxService;
            case SCONE -> teeSconeService;
            case GRAMINE -> teeGramineService;
        };
    }
}
