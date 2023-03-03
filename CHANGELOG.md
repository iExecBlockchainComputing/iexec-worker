# Changelog

All notable changes to this project will be documented in this file.

## [[8.0.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v8.0.0) 2023

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
### Quality
* Improve code quality.
* Extract STOMP client configuration to its own bean.
* Refactor STOMP client service to simplify its implementation.
* Remove unchecked casts.
### Dependency Upgrades
* Replace the deprecated `openjdk` Docker base image with `eclipse-temurin` and upgrade to Java 11.0.18 patch.
* Upgrade to Spring Boot 2.6.14.
* Upgrade to Gradle 7.6.
* Upgrade OkHttp to 4.9.0.
* Upgrade to `iexec-common` 7.0.0.

## [[7.3.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v7.3.0) 2022-12-18

* Add endpoint to allow health checks.

## [[7.2.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/v7.2.0) 2022-12-09

* To conform to Task Feedback API, return a `PostComputeResponse` at the end of the post-compute stage of a standard task.
* `iexec_developper_logger` property defined in Deal parameters is now ignored.
  The requester must use the Task Feedback API to retrieve the logs of an execution they requested.
* Miscellaneous code quality improvements.
* Never return `null` when fetching pre-compute or post-compute exit cause on failure.
  On unknown failure, exit cause will be `PRE_COMPUTE_FAILED_UNKNOWN_ISSUE` or `POST_COMPUTE_FAILED_UNKNOWN_ISSUE`.
* Create first STOMP session before the application is ready and begins to execute replicates. The implementation has been improved to avoid fast polling in the session request listener.
* Remove unnecessary lock on `ReplicateDemandService`.
* Increments of jenkins-library up to version 2.2.3. Enable SonarCloud analyses on branches and Pull Requests.
* Update `iexec-common` version to [6.1.0](https://github.com/iExecBlockchainComputing/iexec-common/releases/tag/v6.1.0).

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
  * Default timeouts can be bounded with IEXEC_WORKER_DOCKER_IMAGE_MIN_PULL_TIMEOUT and IEXEC_WORKER_DOCKER_IMAGE_MAX_PULL_TIMEOUT environment variables.
  * This solves some cases where a task fails with APP_DOWNLOAD_FAILED status due to an optimistic timeout of 1 minute.

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
  After any replicate demand or next action notification, the worker checks (and waits for) consistent on-chain task information before going further.
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
  After any replicate demand or next action notification, the worker checks (and waits for) consistent on-chain task information before going further.

## [[6.0.3]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/6.0.3) 2021-10-05

* Bump iexec-common dependency (iexec-common@5.5.1) featuring socket reuse when sending multiple requests to a blockchain node.

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
* The docker network environment variable has been changed from IEXEC_LAS_DOCKER_NETWORK_NAME to IEXEC_WORKER_DOCKER_NETWORK_NAME.

## [[5.1.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/5.1.0) 2021-03-26

What's new?
Fix WebSockets issue
* Fix STOMP disconnection problem by correctly handling network issues.
* Use a queue to refresh STOMP connection.
* One refresh attempt at a time.

Use the common docker library
* Use the common docker library.

Enhance the execution workflow
* The worker restarts gracefully by waiting for currently running  tasks to be finished.
* The worker always asks for replicates.
  The core is the one which decides if the worker still has space or not.
* The worker tries to contribute even when some errors occur.
* Stop workflow after post-compute if computed file not found.
* Resolve 'Cannot login' issue on startup.

## [[5.0.0]](https://github.com/iExecBlockchainComputing/iexec-worker/releases/tag/5.0.0) 2020-07-15

What's new?
* Computing logs are pushed to iexec-core API.
* Results in TEE mode are pushed using [TEE-worker-post-compute](https://github.com/iExecBlockchainComputing/tee-worker-post-compute) component.
  Results can be pushed to IPFS or Dropbox.
* Results are pushed to IPFS with Standard & Tee mode to a dedicated [iExec Result Proxy](https://github.com/iExecBlockchainComputing/iexec-result-proxy).
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
* Whitelisting: Workers can now be whitelisted on the core side, if the worker is not in the whitelist it will not be able to join the pool.
* Https: Workers can now connect to the core using https.

What is patched?
* The project has been updated to java 11 and web3j 4.3.0.
* Internal refactoring to handle replicates update better.
* Workers that ask for replicates too often will not get replicate.
* Update workers configuration when they disconnect/reconnect.
* If a worker's wallet is (almost) empty, it stops asking for replicates to the core.

Misc:
* The environment variable DATASET_FILENAME has been renamed to IEXEC_DATASET_FILENAME. Applications that use it should be updated.

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
* Failover mechanisms: in case some workers are lost or restarted when working on a specific task, internal mechanisms will redistribute the task or use as much as possible the work performed by the lost / restarted workers.
* iExec End-To-End Encryption with Secret Management Service (SMS): from this version, inputs and outputs of the job can be fully encrypted using the Secret Management Service.
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
