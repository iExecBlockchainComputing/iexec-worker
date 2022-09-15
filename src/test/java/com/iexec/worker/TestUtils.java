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

package com.iexec.worker;

import org.mockito.invocation.InvocationOnMock;

import java.util.concurrent.TimeUnit;

public class TestUtils {

    public static class ThreadNameWrapper {
        public String value;
    }

    public static Object saveThreadNameThenCallRealMethodThenSleepSomeMillis(
            ThreadNameWrapper threadNameWrapper,
            InvocationOnMock invocation, int sleepDuration) throws Throwable {
        Object invocationResult = saveThreadNameThenCallRealMethod(threadNameWrapper, invocation);
        TimeUnit.MILLISECONDS.sleep(sleepDuration);
        return invocationResult;
    }

    public static Object saveThreadNameThenCallRealMethod(
            ThreadNameWrapper threadNameWrapper, InvocationOnMock invocation)
            throws Throwable {
        // Save the name of the current thread
        saveThreadName(threadNameWrapper);
        // Then call real method
        return invocation.callRealMethod();
    }

    public static Void saveThreadNameThenSleepSomeMillis(
            ThreadNameWrapper threadNameWrapper, int sleepDuration) throws Throwable {
        saveThreadName(threadNameWrapper);
        TimeUnit.MILLISECONDS.sleep(sleepDuration);
        return null;
    }

    public static Void saveThreadName(ThreadNameWrapper threadNameWrapper) {
        // Save the name of the current thread
        threadNameWrapper.value = Thread.currentThread().getName();
        return null;
    }
}
