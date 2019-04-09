package com.iexec.worker.dataset;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.sms.SmsSecretRequest;
import com.iexec.common.sms.SmsSecretResponse;
import com.iexec.common.sms.SmsSecretResponse.TaskSecrets;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.SmsClientWrapper;
import com.iexec.worker.utils.FileHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Sign;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

@Slf4j
@Service
public class DatasetService {

    private static final String DATASET_SECRET_FILENAME = "dataset.secret";

    private final WorkerConfigurationService configurationService;
    private final CredentialsService credentialsService;
    private final SmsClientWrapper smsClientWrapper;

    public DatasetService(WorkerConfigurationService configurationService,
                          CredentialsService credentialsService,
                          SmsClientWrapper smsClientWrapper) {
        this.configurationService = configurationService;
        this.credentialsService = credentialsService;
        this.smsClientWrapper = smsClientWrapper;
    }

    /*
     * In order to keep a linear replicate workflow, we'll always have the steps:
     * APP_DOWNLOADING, ..., DATA_DOWNLOADING, ..., COMPUTING (even when the dataset requested is 0x0).
     * In the 0x0 dataset case, we'll have an empty datasetUri, and we'll consider the dataset as downloaded
     */
    public boolean downloadDataset(String chainTaskId, String datasetUri) {
        if (chainTaskId.isEmpty()) {
            log.error("Failed to downloadDataset, chainTaskId shouldn't be empty [chainTaskId:{}, datasetUri:{}]",
                    chainTaskId, datasetUri);
            return false;
        }
        if (datasetUri.isEmpty()) {
            log.info("There's nothing to download for this task [chainTaskId:{}, datasetUri:{}]",
                    chainTaskId, datasetUri);
            return true;
        }
        return FileHelper.downloadFileInDirectory(datasetUri, getDatasetFolderPath(chainTaskId));
    }

    public String getDatasetFolderPath(String chainTaskId) {
        return configurationService.getResultBaseDir() + File.separator + chainTaskId + FileHelper.SLASH_INPUT;
    }

    public boolean decryptData(ContributionAuthorization contributionAuth) {

        String hash = HashUtils.concatenateAndHash(contributionAuth.getWorkerWallet(),
                contributionAuth.getChainTaskId(), contributionAuth.getEnclaveChallenge());

        Sign.SignatureData workerSignature = Sign.signPrefixedMessage(
                BytesUtils.stringToBytes(hash), credentialsService.getCredentials().getEcKeyPair());

        SmsSecretRequest smsSecretRequest = SmsSecretRequest.builder()
                .chainTaskId(contributionAuth.getChainTaskId())
                .workerAddress(contributionAuth.getWorkerWallet())
                .enclaveChallenge(contributionAuth.getEnclaveChallenge())
                .coreSignature(contributionAuth.getSignature())
                .workerSignature(new Signature(workerSignature))
                .build();

        Optional<SmsSecretResponse> oSmsSecretResponse = smsClientWrapper.getTaskSecrets(smsSecretRequest);

        if (!oSmsSecretResponse.isPresent()) {
            return false;
        }

        String chainTaskId = smsSecretRequest.getChainTaskId();
        String datasetFolderPath = getDatasetFolderPath(chainTaskId);
        SmsSecretResponse smsSecretResponse = oSmsSecretResponse.get();
        if (smsSecretResponse.getData() == null) {
            log.info("no secret was found, no need to decrypt data [chainTaskId:{}]", chainTaskId);
            return true;
        }

        TaskSecrets taskSecrets = smsSecretResponse.getData();
        String datasetSecretFilePath = datasetFolderPath + File.separator + DATASET_SECRET_FILENAME;
        String datasetFilePath = datasetFolderPath + File.separator + FileHelper.getDatasetFileName(datasetFolderPath);

        if (datasetFilePath.isEmpty()) {
            log.error("could not get dataset filename, file not found [chainTaskId:{}, ]");
            return false;
        }

        FileHelper.createFileWithContent(datasetSecretFilePath, taskSecrets.getDatasetSecret().getSecret());

        try {
            return decryptFile(datasetFilePath, datasetSecretFilePath);
        } catch (Exception e) {
            log.error("error while trying to decrypt data [chainTaskId:{}, dataFile{}, secretFile:{}]",
                    chainTaskId, datasetFilePath, datasetSecretFilePath);
            e.printStackTrace();
            return false;
        }
    }

    public boolean decryptFile(String dataFilePath, String secretFilePath) throws IOException, InterruptedException {
        String cmd = String.format("./decrypt-dataset.sh %s %s", dataFilePath, secretFilePath);

        ProcessBuilder pb = new ProcessBuilder(cmd.split(" "));
        pb.directory(new File("./src/main/resources/"));
        Process pr = pb.start();

        BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String line;
        boolean isDecrypted = false;

        while ((line = in.readLine()) != null) {
            log.info(line);
            if (line.contains("decrypted file with success")) {
                isDecrypted = true;
            }
        }

        pr.waitFor();
        in.close();
        return isDecrypted;
    }

}
