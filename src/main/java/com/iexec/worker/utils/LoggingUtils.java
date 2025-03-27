/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoggingUtils {

    public static String getHighlightedMessage(String message) {
        String hashtagSequence = new String(new char[message.length()]).replace('\0', '#');
        String spaceSequence = new String(new char[message.length()]).replace('\0', ' ');

        return "\n" +
                "##" + hashtagSequence  + "##\n" +
                "# " + spaceSequence    + " #\n" +
                "# " + message          + " #\n" +
                "# " + spaceSequence    + " #\n" +
                "##" + hashtagSequence  + "##\n" +
                "\n";
    }

    public static String getHeaderFooterHashMessage(String message) {
        String hashtagSequence = new String(new char[message.length()]).replace('\0', '#');

        return "\n" +
                "##" +  hashtagSequence  + "##\n" +
                        message          + "\n" +
                "##" +  hashtagSequence  + "##\n" +
                "\n";
    }

    public static void printHighlightedMessage(String message) {
        System.out.println(getHighlightedMessage(message));
    }

    public static String prettifyDeveloperLogs(String iexecInTree, String iexecOutTree, String stdout, String stderr) {
        return "\n" +
                "#################### DEV MODE ####################\n" +
                "iexec_in folder\n" +
                "--------------------\n" +
                iexecInTree + "\n" +
                "\n" +
                "iexec_out folder\n" +
                "--------------------\n" +
                iexecOutTree + "\n" +
                "\n" +
                "stdout\n" +
                "--------------------\n" +
                stdout + "\n" +
                "\n" +
                "stderr\n" +
                "--------------------\n" +
                stderr + "\n" +
                "#################### DEV MODE ####################\n" +
                "\n";
    }


}