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

package com.iexec.worker.result;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Service
public class EncryptionService {

    @Value("${encryptFilePath}")
    private String scriptFilePath;

    public EncryptionService() {
    }

    public void encryptFile(String taskOutputDir, String resultZipFilePath, String publicKeyFilePath) {
        // TODO encrypt file with java code
        throw new UnsupportedOperationException("Cannot encrypt file with bash script");
        // String options = String.format("--root-dir=%s --result-file=%s --key-file=%s",
        //         taskOutputDir, resultZipFilePath, publicKeyFilePath);
        // String cmd = this.scriptFilePath + " " + options;
        // ProcessBuilder pb = new ProcessBuilder(cmd.split(" "));
        // try {
        //     Process pr = pb.start();
        //     BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        //     String line;
        //     while ((line = in.readLine()) != null) {
        //         log.info(line);
        //     }
        //     pr.waitFor();
        //     in.close();
        // } catch (Exception e) {
        //     log.error("Error while trying to encrypt result [resultZipFilePath{}, publicKeyFilePath:{}]",
        //             resultZipFilePath, publicKeyFilePath);
        //     e.printStackTrace();
        // }
    }

}
