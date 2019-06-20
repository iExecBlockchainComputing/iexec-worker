package com.iexec.worker.executor;


public class TaskExecutorHelperServiceTests {

    
    @Test
    public void shouldComputeWithoutDecryptingDataset() {
        TaskDescription task = getStubTaskDescription();

        // when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        // when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
        //         .thenReturn(Optional.of(getStubTaskDescription()));
        // when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        // when(taskExecutorHelperService.checkAppType(any(), any())).thenReturn("");
        // when(taskExecutorHelperService.tryToDownloadApp(any())).thenReturn("");
        // when(taskExecutorHelperService.tryToDownloadData(any(), any())).thenReturn("");
        // when(taskExecutorHelperService.checkContributionAbility(any())).thenReturn("");
        

        when(computationService.downloadApp(CHAIN_TASK_ID, task.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, task.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(TEE_ENCLAVE_CHALLENGE));
        // CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, never()).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());

        Mockito.verify(computationService, Mockito.times(1))
                .runNonTeeComputation(any(), any());
    }

    @Test
    public void shouldEncryptDatasetAndCompute() {
        TaskDescription modelStub = getStubTaskDescription();

        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        when(computationService.downloadApp(CHAIN_TASK_ID, modelStub.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(datasetService.decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription()));

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(NO_TEE_ENCLAVE_CHALLENGE), false);
        // CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, Mockito.times(1))
                .decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri());

        Mockito.verify(computationService, Mockito.times(1))
                .runNonTeeComputation(any(), any());
    }

    @Test
    public void shouldNotComputeSinceCouldnotDecryptDataset() {
        TaskDescription modelStub = getStubTaskDescription();

        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        when(computationService.downloadApp(CHAIN_TASK_ID, modelStub.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(datasetService.decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(false);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription()));

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(NO_TEE_ENCLAVE_CHALLENGE), false);
        // CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, Mockito.times(1))
                .decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri());

        Mockito.verify(computationService, Mockito.times(0))
                .runNonTeeComputation(any(), any());
    }



}