# Changelog

All notable changes to this project will be documented in this file.

## [[NEXT]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/vNEXT) 2024

### Bug fixes

- Fix `LoginServiceTests#shouldLoginOnceOnSimultaneousCalls` test. (#587)
- Alwyas use `WorkerpoolAuhorization` to retrieve JWT on Result Proxy. (#588)

### Quality

- Configure Gradle JVM Test Suite Plugin. (#589)
- Remove `ResponseEntity` wrapper in feign client. (#593)
- Use constructor injection in `Application` class. (#594)

### Dependency Upgrades

- Upgrade to Gradle 8.7. (#590)
- Upgrade to `eclipse-temurin:11.0.22_7-jre-focal`. (#591)
- Upgrade to Spring Boot 2.7.18. (#592)

## [[8.4.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.4.0) 2024-02-29

### New Features

- Retrieve Result Proxy JWT with `WorkerpoolAuthorization`. (#581)
- Push Result Proxy JWT for IPFS to SMS. (#582)

### Quality

- `SconeConfiguration` class becomes immutable. (#577)
- Fix `onTaskNotification` visibility and lower its complexity. (#578)
- Throw a dedicated exception on empty parameters when authenticating to a docker registry. (#579)
- Check replicate recoverability wih a dedicated check in `ReplicateRecoveryService`. (#580)

### Dependency Upgrades

- Upgrade to `iexec-common` 8.4.0. (#583)
- Upgrade to `iexec-blockchain-adapter` 8.4.0. (#584)
- Upgrade to `iexec-result-proxy` 8.4.0. (#584)
- Upgrade to `iexec-sms` 8.5.0. (#584)

## [[8.3.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.3.0) 2024-01-11

### New Features

- Share code between transaction types in `IexecHubService`. (#556)
- Expose Pre, App & Post-Compute durations. (#559, #560, #561, #562, #563, #565)
- Expose version through prometheus endpoint and through VersionController. (#569 #570)

### Quality

- Expose `workerWalletAddress` to avoid importing `CredentialsService` when not required. (#558)
- Use `docker-java` from `iexec-commons-containers`. (#572)
- Use `@Getter` lombok annotation in `PublicConfigurationService`. (#573)

### Dependency Upgrades

- Upgrade to `eclipse-temurin:11.0.21_9-jre-focal`. (#567)
- Upgrade to Spring Boot 2.7.17. (#566)
- Upgrade to Spring Dependency Management Plugin 1.1.4. (#566)
- Upgrade to `iexec-commons-containers` 1.2.0. (#557)
- Upgrade to `jenkins-library` 2.7.4. (#564)
- Upgrade to `iexec-commons-poco` 3.2.0. (#571)
- Upgrade to `iexec-commons-containers` 1.2.1. (#571)
- Upgrade to `iexec-common` 8.3.1. (#571)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.3.0. (#574)
- Upgrade to `iexec-result-proxy-library` 8.3.0. (#574)
- Upgrade to `iexec-sms-library` 8.4.0. (#574)

## [[8.2.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.2.0) 2023-09-29

### New Features

- Check result files name length before zipping. (#538)

### Bug fixes

- Implement thread-safe login on scheduler. (#541)
- Fix and harmonize `Dockerfile entrypoint` in all Spring Boot applications. (#548)
- Remove potential `NullPointerException` and add `isStatusValidOnChainAfterPendingReceipt` in `IexecHubService`. (#550)

### Quality

- Remove `nexus.intra.iex.ec` repository. (#539)
- Remove `Graylog` support. Fetch logs with a sidecar to push them to your log infrastructure. (#540)
- Rename scontain registry to `registry.scontain.com`. (#542)
- Upgrade to Gradle 8.2.1 with up-to-date plugins. (#545)
- Fix log format in `LasService`. (#546)
- Do not retry calls to fetch replicate from a scheduler, those calls are already scheduled. (#547)
- Remove dead code in `IexecHubService`. (#550)
- Remove `VersionService#isSnapshot`. (#552)

### Dependency Upgrades

- Upgrade to `iexec-common` 8.2.1-NEXT-SNAPSHOT. (#538)
- Remove `logstash-gelf` dependency. (#540)
- Upgrade to `eclipse-temurin` 11.0.20. (#543)
- Upgrade to Spring Boot 2.7.14. (#544)
- Upgrade to Spring Dependency Management Plugin 1.1.3. (#544)
- Upgrade to `jenkins-library` 2.7.3. (#549)
- Upgrade to `iexec-commons-poco` 3.1.0. (#551)
- Upgrade to `iexec-common` 8.3.0. (#551)
- Upgrade to `iexec-commons-containers` 1.1.2. (#551 #552)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.2.0. (#553)
- Upgrade to `iexec-result-proxy-library` 8.2.0. (#553)
- Upgrade to `iexec-sms-library` 8.3.0. (#553)

## [[8.1.4]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.1.4) 2023-06-27

### Dependency Upgrades

- Upgrade to `iexec-commons-poco` 3.0.5. (#536)

## [[8.1.3]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.1.3) 2023-06-23

### Dependency Upgrades

- Upgrade to `iexec-common` 8.2.1. (#534)
- Upgrade to `iexec-commons-containers` 1.0.3. (#534)
- Upgrade to `iexec-commons-poco` 3.0.4. (#534)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.1.1. (#534)
- Upgrade to `iexec-result-proxy-library` 8.1.1. (#534)
- Upgrade to `iexec-sms-library` 8.1.1. (#534)

## [[8.1.2]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.1.2) 2023-06-22

### Features

- Retry dataset download on several IPFS gateways. (#532)

### Bug Fixes

- Improve interruptions handling in `IexecHubService`. (#529)
- Return application execution `exitCause` as computed in `AppComputeService#getExitCauseFromFinalStatus`. (#531)

### Dependency upgrade

- Upgrade to `iexec-commons-poco` 3.0.3 dependency. (#532)

## [[8.1.1]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.1.1) 2023-06-15

### Bug Fixes

- Do not throw exceptions from `LasService#start` method. (#528)
- Add `synchronized` keyword on `LasService#start` method to avoid race conditions. (#528)

## [[8.1.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.1.0) 2023-06-12

### New Features

- Enable Prometheus actuator. (#512)
- Add `contributeAndFinalize` support for TEE tasks when required `trust` is `1`. (#516 #517 #518)
- Add purge cached task descriptions ability. (#521)
- Add `chainReceipt` to ContributeAndFinalize replicate status details. (#522)
- Use DatasetAddress as dataset filename. (#523)

### Bug Fixes

- Filter `contribute` and `reveal` event logs on `chainTaskId` and `workerAddress`. (#516)

### Quality

- Refactor STOMP client service to simplify its implementation. (#492)
- Add `error` log events. (#514)
- Improve `IexecHubService` coverage. (#526)

### Dependency Upgrades

- Add new `iexec-commons-containers` 1.0.2 dependency. (#513 #515)
- Upgrade to `iexec-common` 8.2.0. (#515 #518 #520 #524)
- Add new `iexec-commons-poco` 3.0.2 dependency. (#515 #518 #520 #521 #524)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.1.0. (#524)
- Upgrade to `iexec-result-proxy-library` 8.1.0. (#524)
- Upgrade to `iexec-sms-library` 8.1.0. (#524)

## [[8.0.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.0.0) 2023-03-08

### New Features

* Support Gramine framework for TEE tasks.
* Bind SMS URL to task.
* Show application version on banner.

### Bug Fixes

* Remove dataset decryption non-TEE workflow.
* Purge result files and metadata when task is completed.
* On _iExec Core Scheduler_ REST call failure, only try to log in once to avoid nested retry loops.
* Update iconarchive RLC PNG hash in tests.
* Do not try to send heartbeat more than scheduled on _iExec Core Scheduler_.
* Only try to log in once when the _iExec Core Scheduler_ returns __401 Unauthorized__ HTTP status code.
* Wait for the application to be started before creating STOMP session.
* Wait for the STOMP session to be ready before sending any replicate status update.
* Do not call `isSgxSupported` when `SgxDriverMode.NONE`.
* Remove duplicated calls to `IexecHubService#getTaskDescription` in `TaskManagerService`.
* Refactor update replicate status call.

### Quality

* Improve code quality.
* Extract STOMP client configuration to its own bean.
* Remove unchecked casts.

### Dependency Upgrades

* Replace the deprecated `openjdk` Docker base image with `eclipse-temurin` and upgrade to Java 11.0.18 patch.
* Upgrade to Spring Boot 2.6.14.
* Upgrade to Gradle 7.6.
* Upgrade OkHttp to 4.9.0.
* Upgrade to `iexec-common` 7.0.0.
* Upgrade to `jenkins-library` 2.4.0.

## [[7.3.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v7.3.0) 2022-12-18

* Add endpoint to allow health checks.

## [[7.2.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v7.2.0) 2022-12-09

* To conform to Task Feedback API, return a `PostComputeResponse` at the end of the post-compute stage of a standard
  task.
* `iexec_developper_logger` property defined in Deal parameters is now ignored.
  The requester must use the Task Feedback API to retrieve the logs of an execution they requested.
* Miscellaneous code quality improvements.
* Never return `null` when fetching pre-compute or post-compute exit cause on failure.
  On unknown failure, exit cause will be `PRE_COMPUTE_FAILED_UNKNOWN_ISSUE` or `POST_COMPUTE_FAILED_UNKNOWN_ISSUE`.
* Create first STOMP session before the application is ready and begins to execute replicates. The implementation has
  been improved to avoid fast polling in the session request listener.
* Remove unnecessary lock on `ReplicateDemandService`.
* Increments of jenkins-library up to version 2.2.3. Enable SonarCloud analyses on branches and Pull Requests.
* Update `iexec-common` version
  to [6.1.0](https://github.com/iExecBlockchainComputing/iexec-common/releases/tag/v6.1.0).

## [[7.1.2]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v7.1.2) 2022-11-29

* Retry updating replicate status until the core-scheduler responds successfully or until the final deadline is reached.

## [[7.1.1]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v7.1.1) 2022-11-28

* Update build process.

## [[7.1.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v7.1.0) 2022-07-01

* Add endpoints to collect execution status from pre and post compute stages.
* Handle error during TEE session generation, retrieve and store stdout and stderr on successful application execution.
* Use OpenFeign client libraries.
* Support legacy (/dev/isgx device) and native (/dev/sgx_enclave, /dev/sgx_provision) SGX drivers.
* Use Spring Boot 2.6.2.
* Use Java 11.0.15.

## [[7.0.1]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v7.0.1) 2022-04-06

* Determine Docker image pull timeout based on the category time frame of the deal.
    * Default timeouts can be bounded with IEXEC_WORKER_DOCKER_IMAGE_MIN_PULL_TIMEOUT and
      IEXEC_WORKER_DOCKER_IMAGE_MAX_PULL_TIMEOUT environment variables.
    * This solves some cases where a task fails with APP_DOWNLOAD_FAILED status due to an optimistic timeout of 1
      minute.

## [[7.0.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/7.0.0) 2021-12-14

Highly improved throughput of the iExec protocol.

What has changed since v6.0.0?

* Consume blockchain configuration on iexec-blockchain-adapter-api.
* Improve enclave signature retrieval logs.
* Upgrade artifacts publishing.
* Enable local import of iexec-common.
* Upgrade to JUnit5.
* Remove task-containers safely.
* Merge abort notifications into a single notification with custom abort cause.
* Add EIP-155 replay attack protection on transactions.
* Remove deprecated workflow of data decryption outside TEE.
* Fix WORKER_LOST issue.
* Fix undesirable STAKE_TOO_LOW side effect (even happening when stake is not required for a task).
  After any replicate demand or next action notification, the worker checks (and waits for) consistent on-chain task
  information before going further.
* Reuse socket when sending multiple requests to a blockchain node.

## [[6.3.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/6.3.0) 2021-11-25

* Consume blockchain configuration on iexec-blockchain-adapter-api.
* Improve enclave signature retrieval logs.
* Upgrade artifacts publishing.
* Enable local import of iexec-common.
* Upgrade to JUnit5.
* Remove task-containers safely.

## [[6.2.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/6.2.0) 2021-11-10

* Merge abort notifications into a single notification with custom abort cause.

## [[6.1.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/6.1.0) 2021-10-26

* Added EIP-155 replay attack protection on transactions.
* Removed deprecated workflow of data decryption outside TEE.

## [[6.0.5]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/6.0.5) 2021-10-19

* Fixed WORKER_LOST issue.

## [[6.0.4]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/6.0.4) 2021-10-13

* Fixed undesirable STAKE_TOO_LOW side effect (even happening when stake is not required for a task).
  After any replicate demand or next action notification, the worker checks (and waits for) consistent on-chain task
  information before going further.

## [[6.0.3]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/6.0.3) 2021-10-05

* Bump iexec-common dependency (iexec-common@5.5.1) featuring socket reuse when sending multiple requests to a
  blockchain node.

## [[6.0.2]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/6.0.2) 2021-08-31

## [[6.0.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v6.0.0) 2021-06-16

What's new?

* Check input dataset checksum.
* Add pre-compute enclave to download & decrypt input datasets.
* Upgrade to Scone v5.
* Add support of input files in TEE mode.
* Support latest MrEnclave format for TEE applications.
* Fetch pre-compute & post-compute config from SMS.
* Download private docker images.

Breaking changes

* The docker network environment variable has been changed from IEXEC_LAS_DOCKER_NETWORK_NAME to
  IEXEC_WORKER_DOCKER_NETWORK_NAME.

## [[5.1.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/5.1.0) 2021-03-26

What's new?
Fix WebSockets issue

* Fix STOMP disconnection problem by correctly handling network issues.
* Use a queue to refresh STOMP connection.
* One refresh attempt at a time.

Use the common docker library

* Use the common docker library.

Enhance the execution workflow

* The worker restarts gracefully by waiting for currently running tasks to be finished.
* The worker always asks for replicates.
  The core is the one which decides if the worker still has space or not.
* The worker tries to contribute even when some errors occur.
* Stop workflow after post-compute if computed file not found.
* Resolve 'Cannot login' issue on startup.

## [[5.0.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/5.0.0) 2020-07-15

What's new?

* Computing logs are pushed to iexec-core API.
* Results in TEE mode are pushed
  using [TEE-worker-post-compute](https://github.com/iExecBlockchainComputing/tee-worker-post-compute) component.
  Results can be pushed to IPFS or Dropbox.
* Results are pushed to IPFS with Standard & Tee mode to a
  dedicated [iExec Result Proxy](https://github.com/iExecBlockchainComputing/iexec-result-proxy).
* Task result link is standardized and generic. It supports Ethereum, IPFS & Dropbox "storage" providers.
* Dapps should produce a computed.json file. Merged determinism.iexec & callback.iexec into it.
* Requester params are now explicitly set (storage, encryption, ..) and properly handled by workers.
* Full compatibility with new [iExec Secret Management Service](https://github.com/iExecBlockchainComputing/iexec-sms).
* Compatibility with latest PoCo 5.1.0 smart contracts.

## [[4.0.1]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/4.0.1) 2020-02-25

What's fixed?

* More resistance to unsync Ethereum nodes.

## [[4.0.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/4.0.0) 2019-12-10

What's new?

* Native-token sidechain compatibility.
* GPU support.
* Optional log aggregation on remote workers for software debugging.
* Task computing logs for iExec buidlers debugging.

What's fixed?

* JWT issues.

## [[3.2.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/3.2.0) 2019-09-17

What is new?

* Whitelisting: Workers can now be whitelisted on the core side, if the worker is not in the whitelist it will not be
  able to join the pool.
* Https: Workers can now connect to the core using https.

What is patched?

* The project has been updated to java 11 and web3j 4.3.0.
* Internal refactoring to handle replicates update better.
* Workers that ask for replicates too often will not get replicate.
* Update workers configuration when they disconnect/reconnect.
* If a worker's wallet is (almost) empty, it stops asking for replicates to the core.

Misc:

* The environment variable DATASET_FILENAME has been renamed to IEXEC_DATASET_FILENAME. Applications that use it should
  be updated.

## [[3.1.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/3.1.0) 2019-07-02

What's new?

* Full end-to-end encryption inside a Trusted Execution Environment (TEE) powered by Intel(R) SGX.
* Implemented the Proof-of-Contribution (PoCo) Sarmenta's formula for a greater task dispatching.

What's patched?

* A single FAILED replicate status when a completion is impossible.
* WORKER_LOST is not set for replicates which are already FAILED.
* Checks before many steps the contribution deadline is not reached.
* Better handling of access token expiration.
* Don't notify when contribute or reveal seems to fail since the tx could have been mined.
* Restart when ethereum node is not available at start-up.

## [[3.0.1]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/3.0.1) 2019-05-22

What's new?

* Retry result upload multiple times with delay if remote result repository is not responding.
* Wait blockchain task status changes if node is not sync.
* Dont update replicate if node is not sync.
* Dont ask for new task if node is not sync.

## [[3.0.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/3.0.0) 2019-05-14

This release contains the worker set of changes in the 3.0.0-alpha-X releases and some other features/fixes.
In summary this version introduces:

* A new architecture: the worker has been completely re-architectured from the version 2.
* Latest PoCo use
* Better management of transaction with the ethereum blockchain.
* Failover mechanisms: in case some workers are lost or restarted when working on a specific task, internal mechanisms
  will redistribute the task or use as much as possible the work performed by the lost / restarted workers.
* iExec End-To-End Encryption with Secret Management Service (SMS): from this version, inputs and outputs of the job can
  be fully encrypted using the Secret Management Service.
* Decentralized oracle: If the result is needed by a smart contract, it is available directly on the blockchain.
* IPFS: data can be retrieved from IPFS and public results can be published on.

For further information on this version, please read our documentation.

## [[3.0.0-alpha3]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/3.0.0-alpha3) 2019-04-12

What's new?

* Enable overriding the node the worker will connect to.
* Return chainReceipt when contribute/reveal fails for on-chain verification.
* The worker now can download data from IPFS.
* Better recovery mechanism when the worker is shut down in the middle of a computation.
* Better management when the transaction receipt from the blockchain is null.
* Additional configuration if a worker is behind a proxy.
* General refactoring.
* Compatible with PoCo@3.0.27.

## [[3.0.0-alpha2]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/3.0.0-alpha2) 2019-02-08

What's new?

* The worker will send to the scheduler the tx hash & the block number of transactions to keep track of what happened.
* The worker will reboot after a scheduler reboot.
* Improved STOMP websocket management.
* Compatible with PoCo@3.0.21.

## [[3.0.0-alpha1]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/3.0.0-alpha1) 2019-01-25

* This is the first alpha release of version 3.0.0.
