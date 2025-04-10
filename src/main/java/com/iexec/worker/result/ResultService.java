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

package com.iexec.worker.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.lifecycle.purge.ExpiringTaskMapFactory;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.result.ResultModel;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.common.worker.result.ResultUtils;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.iexec.commons.poco.chain.DealParams.DROPBOX_RESULT_STORAGE_PROVIDER;
import static com.iexec.commons.poco.chain.DealParams.IPFS_RESULT_STORAGE_PROVIDER;
import static com.iexec.commons.poco.utils.BytesUtils.stringToBytes;

@Slf4j
@Service
public class ResultService implements Purgeable {
    public static final String ERROR_FILENAME = "error.txt";

    private final WorkerConfigurationService workerConfigService;
    private final SignerService signerService;
    private final IexecHubService iexecHubService;
    private final PublicConfigurationService publicConfigurationService;
    private final Map<String, ResultInfo> resultInfoMap = ExpiringTaskMapFactory.getExpiringTaskMap();
    private final ObjectMapper mapper = new ObjectMapper();

    public ResultService(
            WorkerConfigurationService workerConfigService,
            SignerService signerService,
            IexecHubService iexecHubService,
            PublicConfigurationService publicConfigurationService) {
        this.workerConfigService = workerConfigService;
        this.signerService = signerService;
        this.iexecHubService = iexecHubService;
        this.publicConfigurationService = publicConfigurationService;
    }

    public ResultInfo getResultInfos(final String chainTaskId) {
        return resultInfoMap.get(chainTaskId);
    }

    public String getResultFolderPath(final String chainTaskId) {
        return workerConfigService.getTaskIexecOutDir(chainTaskId);
    }

    public boolean isResultFolderFound(final String chainTaskId) {
        return new File(getResultFolderPath(chainTaskId)).exists();
    }

    public String getResultZipFilePath(final String chainTaskId) {
        return getResultFolderPath(chainTaskId) + ".zip";
    }

    public boolean isResultZipFound(final String chainTaskId) {
        return new File(getResultZipFilePath(chainTaskId)).exists();
    }

    public boolean writeErrorToIexecOut(final String chainTaskId, final ReplicateStatus errorStatus,
                                        final ReplicateStatusCause errorCause) {
        final String errorContent = String.format("[IEXEC] Error occurred while computing"
                + " the task [error:%s, cause:%s]", errorStatus, errorCause);
        final ComputedFile computedFile = ComputedFile.builder()
                .deterministicOutputPath(IexecFileHelper.SLASH_IEXEC_OUT +
                        File.separator + ERROR_FILENAME)
                .build();
        final String computedFileJsonAsString;
        try {
            computedFileJsonAsString = new ObjectMapper().writeValueAsString(computedFile);
        } catch (JsonProcessingException e) {
            log.error("Failed to prepare computed file [chainTaskId:{}]", chainTaskId, e);
            return false;
        }
        final String hostIexecOutSlash = workerConfigService.getTaskIexecOutDir(chainTaskId)
                + File.separator;
        return FileHelper.createFolder(hostIexecOutSlash)
                && FileHelper.writeFile(hostIexecOutSlash + ERROR_FILENAME,
                errorContent.getBytes())
                && FileHelper.writeFile(hostIexecOutSlash
                + IexecFileHelper.COMPUTED_JSON, computedFileJsonAsString.getBytes());
    }

    public void saveResultInfo(final TaskDescription taskDescription,
                               final ComputedFile computedFile) {
        final ResultInfo resultInfo = ResultInfo.builder()
                .image(taskDescription.getAppUri())
                .cmd(taskDescription.getDealParams().getIexecArgs())
                .deterministHash(computedFile != null ? computedFile.getResultDigest() : "")
                .datasetUri(taskDescription.getDatasetUri())
                .build();

        resultInfoMap.put(taskDescription.getChainTaskId(), resultInfo);
    }

    public ResultModel getResultModelWithZip(final String chainTaskId) {
        final ResultInfo resultInfo = getResultInfos(chainTaskId);
        byte[] zipResultAsBytes = new byte[0];
        final String zipLocation = getResultZipFilePath(chainTaskId);
        try {
            zipResultAsBytes = Files.readAllBytes(Paths.get(zipLocation));
        } catch (IOException e) {
            log.error("Failed to get zip result [chainTaskId:{}, zipLocation:{}]", chainTaskId, zipLocation, e);
        }

        return ResultModel.builder()
                .chainTaskId(chainTaskId)
                .image(resultInfo.getImage())
                .cmd(resultInfo.getCmd())
                .zip(zipResultAsBytes)
                .deterministHash(resultInfo.getDeterministHash())
                .build();
    }

    public void cleanUnusedResultFolders(final List<String> recoveredTasks) {
        for (final String chainTaskId : getAllChainTaskIdsInResultFolder()) {
            if (!recoveredTasks.contains(chainTaskId)) {
                purgeTask(chainTaskId);
            }
        }
    }

    public List<String> getAllChainTaskIdsInResultFolder() {
        final File resultsFolder = new File(workerConfigService.getWorkerBaseDir());
        final String[] chainTaskIdFolders = resultsFolder.list((current, name) -> new File(current, name).isDirectory());

        if (chainTaskIdFolders == null || chainTaskIdFolders.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(chainTaskIdFolders);
    }

    /*
     * For Cloud computing basic
     * - upload from worker requested
     * - send link from worker requested
     *
     * But for other work-flows
     * - link could be retrieved from core before finalize
     *
     * */
    public String uploadResultAndGetLink(final WorkerpoolAuthorization workerpoolAuthorization) {
        final String chainTaskId = workerpoolAuthorization.getChainTaskId();
        final TaskDescription task = iexecHubService.getTaskDescription(chainTaskId);

        if (task == null) {
            log.error("Cannot get result link (task missing) [chainTaskId:{}]", chainTaskId);
            return "";
        }

        // Offchain computing - basic & tee
        if (task.containsCallback()) {
            log.info("Web3 storage, no need to upload [chainTaskId:{}]", chainTaskId);
            return buildResultLink("ethereum", task.getCallback());
        }

        // Cloud computing - tee
        if (task.isTeeTask()) {
            log.info("Web2 storage, already uploaded (with tee) [chainTaskId:{}]", chainTaskId);
            return getWeb2ResultLink(task);
        }

        // Cloud computing - basic
        final boolean isIpfsStorageRequest = IPFS_RESULT_STORAGE_PROVIDER.equals(task.getDealParams().getIexecResultStorageProvider());
        final String resultProxyURL = task.getDealParams().getIexecResultStorageProxy();
        final boolean isUpload = upload(workerpoolAuthorization, resultProxyURL);
        if (isIpfsStorageRequest && isUpload) {
            log.info("Web2 storage, just uploaded (with basic) [chainTaskId:{}]", chainTaskId);
            return getWeb2ResultLink(task);//retrieves ipfs only
        }

        log.info("Cannot uploadResultAndGetLink [chainTaskId:{}, isIpfsStorageRequest:{}, chainTaskId:{}]",
                chainTaskId, isIpfsStorageRequest, isUpload);
        return "";
    }

    private boolean upload(final WorkerpoolAuthorization workerpoolAuthorization,
                           final String resultProxyUrl) {
        final String chainTaskId = workerpoolAuthorization.getChainTaskId();
        final String authorizationToken = getIexecUploadToken(workerpoolAuthorization, resultProxyUrl);
        if (authorizationToken.isEmpty()) {
            log.error("Empty authorizationToken, cannot upload result [chainTaskId:{}]", chainTaskId);
            return false;
        }

        try {
            publicConfigurationService
                    .createResultProxyClientFromURL(resultProxyUrl)
                    .addResult(authorizationToken, getResultModelWithZip(chainTaskId));
            return true;
        } catch (Exception e) {
            log.error("Empty location, cannot upload result [chainTaskId:{}]", chainTaskId, e);
            return false;
        }
    }

    private String getWeb2ResultLink(final TaskDescription task) {
        final String chainTaskId = task.getChainTaskId();
        final String storage = task.getDealParams().getIexecResultStorageProvider();

        switch (storage) {
            case IPFS_RESULT_STORAGE_PROVIDER:
                try {
                    final String resultProxyUrl = task.getDealParams().getIexecResultStorageProxy();
                    final String ipfsHash = publicConfigurationService
                            .createResultProxyClientFromURL(resultProxyUrl)
                            .getIpfsHashForTask(chainTaskId);
                    return buildResultLink(storage, "/ipfs/" + ipfsHash);
                } catch (RuntimeException e) {
                    log.error("Cannot get tee web2 result link (result-proxy issue) [chainTaskId:{}]", chainTaskId, e);
                    return "";
                }
            case DROPBOX_RESULT_STORAGE_PROVIDER:
                return buildResultLink(storage, "/results/" + chainTaskId);
            default:
                log.error("Cannot get tee web2 result link (storage missing) [chainTaskId:{}]", chainTaskId);
                return "";
        }
    }

    String buildResultLink(final String storage, final String location) {
        return String.format("{ \"storage\": \"%s\", \"location\": \"%s\" }", storage, location);
    }

    /**
     * Gets and returns a JWT against a valid {@code WorkerpoolAuthorization}
     *
     * @param workerpoolAuthorization The auhtorization
     * @return The JWT
     */
    // TODO Add JWT validation
    public String getIexecUploadToken(final WorkerpoolAuthorization workerpoolAuthorization,
                                      final String resultProxyUrl) {
        try {
            final String hash = workerpoolAuthorization.getHash();
            final String authorization = signerService.signMessageHash(hash).getValue();
            if (authorization.isEmpty()) {
                log.error("Couldn't sign hash for an unknown reason [hash:{}]", hash);
                return "";
            }
            return publicConfigurationService
                    .createResultProxyClientFromURL(resultProxyUrl)
                    .getJwt(authorization, workerpoolAuthorization);
        } catch (Exception e) {
            log.error("Failed to get upload token", e);
            return "";
        }
    }

    public boolean isResultAvailable(final String chainTaskId) {
        return isResultZipFound(chainTaskId);
    }

    public ComputedFile readComputedFile(final String chainTaskId) {
        final ComputedFile computedFile = IexecFileHelper.readComputedFile(chainTaskId,
                workerConfigService.getTaskOutputDir(chainTaskId));
        if (computedFile == null) {
            log.error("Failed to read computed file (computed.json missing) [chainTaskId:{}]", chainTaskId);
        }
        return computedFile;
    }

    public ComputedFile getComputedFile(final String chainTaskId) {
        final ComputedFile computedFile = readComputedFile(chainTaskId);
        if (computedFile == null) {
            log.error("Failed to getComputedFile (computed.json missing) [chainTaskId:{}]", chainTaskId);
            return null;
        }
        if (computedFile.getResultDigest() == null || computedFile.getResultDigest().isEmpty()) {
            String resultDigest = computeResultDigest(computedFile);
            if (resultDigest.isEmpty()) {
                log.error("Failed to getComputedFile (resultDigest is empty " +
                                "but cant compute it) [chainTaskId:{}, computedFile:{}]",
                        chainTaskId, computedFile);
                return null;
            }
            computedFile.setResultDigest(resultDigest);
        }
        return computedFile;
    }

    /**
     * Writes computed file submitted to the worker through a REST call.
     * TODO: check compute stage is successful
     *
     * @param computedFile computed file to be written
     * @return {@literal true} is computed file is successfully written to disk, {@literal false} otherwise
     */
    public boolean writeComputedFile(final ComputedFile computedFile) {
        if (computedFile == null || StringUtils.isEmpty(computedFile.getTaskId())) {
            log.error("Cannot write computed file [computedFile:{}]", computedFile);
            return false;
        }
        final String chainTaskId = computedFile.getTaskId();
        log.debug("Received computed file [chainTaskId:{}, computedFile:{}]", chainTaskId, computedFile);
        final ChainTask chainTask = iexecHubService.getChainTask(chainTaskId).orElse(null);
        final ChainTaskStatus chainTaskStatus = chainTask != null ? chainTask.getStatus() : null;
        if (chainTaskStatus != ChainTaskStatus.ACTIVE) {
            log.error("Cannot write computed file if task is not active [chainTaskId:{}, computedFile:{}, chainTaskStatus:{}]",
                    chainTaskId, computedFile, chainTaskStatus);
            return false;
        }
        final String computedFilePath = workerConfigService.getTaskOutputDir(chainTaskId)
                + IexecFileHelper.SLASH_COMPUTED_JSON;
        if (new File(computedFilePath).exists()) {
            log.error("Cannot write computed file if already written [chainTaskId:{}, computedFile:{}]",
                    chainTaskId, computedFile);
            return false;
        }
        if (!BytesUtils.isNonZeroedBytes32(computedFile.getResultDigest())) {
            log.error("Cannot write computed file if result digest is invalid [chainTaskId:{}, computedFile:{}]",
                    chainTaskId, computedFile);
            return false;
        }
        final ChainDeal chainDeal = iexecHubService.getChainDeal(chainTask.getDealid()).orElse(null);
        if (chainDeal == null || !TeeUtils.isTeeTag(chainDeal.getTag())) {
            log.error("Cannot write computed file if task is not of TEE type [chainTaskId:{}, computedFile:{}]",
                    chainTaskId, computedFile);
            return false;
        }
        // should always be TEE with a valid signature
        if (StringUtils.isEmpty(computedFile.getEnclaveSignature())
                || stringToBytes(computedFile.getEnclaveSignature()).length != 65) {
            log.error("Cannot write computed file if TEE signature is invalid [chainTaskId:{}, computedFile:{}]",
                    chainTaskId, computedFile);
            return false;
        }
        try {
            final String json = mapper.writeValueAsString(computedFile);
            Files.write(Paths.get(computedFilePath), json.getBytes());
        } catch (IOException e) {
            log.error("Cannot write computed file if write failed [chainTaskId:{}, computedFile:{}]",
                    chainTaskId, computedFile, e);
            return false;
        }
        return true;
    }

    public String computeResultDigest(final ComputedFile computedFile) {
        final String chainTaskId = computedFile.getTaskId();
        final String resultDigest;
        if (iexecHubService.getTaskDescription(chainTaskId).containsCallback()) {
            resultDigest = ResultUtils.computeWeb3ResultDigest(computedFile);
        } else {
            resultDigest = ResultUtils.computeWeb2ResultDigest(computedFile,
                    workerConfigService.getTaskOutputDir(chainTaskId));
        }
        if (resultDigest.isEmpty()) {
            log.error("Failed to computeResultDigest (resultDigest empty) [chainTaskId:{}, computedFile:{}]",
                    chainTaskId, computedFile);
            return "";
        }
        return resultDigest;
    }

    // region Purge

    /**
     * Purge results from given task, especially its result folder.
     *
     * @param chainTaskId ID of the task to purge.
     * @return {@literal true} if task folder has been deleted
     * and task data has been cleared from this service;
     * {@literal false} otherwise.
     */
    @Override
    public boolean purgeTask(final String chainTaskId) {
        log.debug("purgeTask [chainTaskId:{}]", chainTaskId);
        final String taskBaseDir = workerConfigService.getTaskBaseDir(chainTaskId);

        resultInfoMap.remove(chainTaskId);
        FileHelper.deleteFolder(taskBaseDir);

        final boolean deletedInMap = !resultInfoMap.containsKey(chainTaskId);
        final boolean deletedTaskFolder = !new File(taskBaseDir).exists();

        final boolean deleted = deletedInMap && deletedTaskFolder;
        if (deleted) {
            log.info("The result of the chainTaskId has been deleted [chainTaskId:{}]", chainTaskId);
        } else {
            log.warn("The result of the chainTaskId couldn't be deleted [chainTaskId:{}, deletedInMap:{}, deletedTaskFolder:{}]",
                    chainTaskId, deletedInMap, deletedTaskFolder);
        }

        return deleted;
    }

    /**
     * Purge results from all known tasks, especially their result folders.
     */
    @Override
    @PreDestroy
    public void purgeAllTasksData() {
        log.info("Method purgeAllTasksData() called to perform task data cleanup.");
        final List<String> tasksIds = new ArrayList<>(resultInfoMap.keySet());
        tasksIds.forEach(this::purgeTask);
    }
    // endregion
}
