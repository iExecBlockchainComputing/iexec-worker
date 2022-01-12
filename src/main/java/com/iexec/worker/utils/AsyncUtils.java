/*
* Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
public class AsyncUtils {

    private AsyncUtils() {}

    /**
     * The default {@link CompletableFuture#runAsync(Runnable)} fails silently when an exception
     * is thrown in the running thread. This wrapper method adds an exception handler that logs
     * a custom error message as well as the exception itself.
     * 
     * @param context custom identified logged in the error message
     * @param runnable the task to run
     * @param executor the executor used to run the task
     */
    public static void runAsyncTask(String context, Runnable runnable, Executor executor) {
        log.debug("Running async task [context:{}]", context);
        CompletableFuture
                .runAsync(runnable, executor)
                .exceptionally(error -> handleAsyncTaskError(context, error));
    }

    /**
     * Print custom error message when a problem occurs in an async task.
     */
    private static Void handleAsyncTaskError(String context, Throwable e) {
        if (e != null) {
            log.error("Error occurred in async task [context:{}]", context, e);
        }
        return null;
    }

}
