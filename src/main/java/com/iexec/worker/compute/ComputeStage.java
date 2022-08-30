/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.compute;

public enum ComputeStage {

    PRE(ComputeStage.PRE_VALUE),
    POST(ComputeStage.POST_VALUE);

    public static final String PRE_VALUE = "pre";
    public static final String POST_VALUE = "post";

    private final String value;

    ComputeStage(String value) {
        this.value = value;
    }
}
