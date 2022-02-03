# iexec-worker

### Overview

The iExec-Worker component is in charge of running computing tasks sent by requesters through the iExec Marketplace.


### Run an iexec-worker


#### With Gradle

*Please first update your config located in `./src/main/resources/application.yml`*

* for dev purposes:

```
cd iexec-worker
gradle bootRun --refresh-dependencies
```
* or on a remote instance:
```
cd iexec-worker
./gradlew bootRun --refresh-dependencies
```

#### Configuration

You can configure the worker with the following properties:

| Property                                      | Description                                                                                                                      | Values           | Default value                                             |
|-----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|------------------|-----------------------------------------------------------|
| IEXEC_WORKER_PORT                             | Port the worker's server will use.                                                                                               | Positive integer | 13100                                                     |
| IEXEC_CORE_PROTOCOL                           | Protocol to connect to the Scheduler.                                                                                            | String           | http                                                      |
| IEXEC_CORE_HOST                               | Host to connect to the Scheduler.                                                                                                | String           | localhost                                                 |
| IEXEC_CORE_PORT                               | Port used by the Scheduler.                                                                                                      | Positive integer | 13000                                                     |
| IEXEC_WORKER_NAME                             | Name the worker should be known by.                                                                                              | String           | worker                                                    |
| IEXEC_WORKER_BASE_DIR                         | Path to the folder within which the worker will read and write tasks' inputs and outputs.                                        | String           | /tmp/iexec-worker                                         |
| IEXEC_WORKER_OVERRIDE_AVAILABLE_CPU_COUNT     | Number of CPUs to use. If not set, n-1 CPUs will be used, where n is the number of available processors.                         | Positive integer |                                                           |
| IEXEC_WORKER_GPU_ENABLED                      | Whether to use the GPU instead of the CPU. Note that if it is true, `IEXEC_WORKER_OVERRIDE_AVAILABLE_CPU_COUNT` will be ignored. | Boolean          | false                                                     |
| IEXEC_GAS_PRICE_MULTIPLIER                    | Transactions will be sent with `networkGasPrice * IEXEC_GAS_PRICE_MULTIPLIER`.                                                   | Float            | 1.3                                                       |
| IEXEC_GAS_PRICE_CAP                           | In Wei, will be used for transactions if `networkGasPrice * IEXEC_GAS_PRICE_MULTIPLIER > gasPriceCap`.                           | Integer          | 22000000000                                               |
| IEXEC_WORKER_OVERRIDE_BLOCKCHAIN_NODE_ADDRESS | If set, will be used instead of the address given by the blockchain adapter.                                                     | String           |                                                           |
| IEXEC_DEVELOPER_LOGGER_ENABLED                | Whether to print apps' logs.                                                                                                     | Boolean          | false                                                     |
| IEXEC_WORKER_TEE_COMPUTE_MAX_HEAP_SIZE_GB     | Max heap size for TEE apps.                                                                                                      | Positive integer | 8                                                         |
| IEXEC_WORKER_DOCKER_NETWORK_NAME              | Name of the network the LAS is in. Useless if worker runs only standard tasks.                                                   | String           | iexec-worker-net                                          |
| IEXEC_WORKER_DOCKER_REGISTRY_USERNAME_0       | Username to pull apps from [docker official website](https://hub.docker.com/)                                                    | String           |                                                           |
| IEXEC_WORKER_DOCKER_REGISTRY_PASSWORD_0       | Password to  pull apps from [docker official website](https://hub.docker.com/)                                                   | String           |                                                           |
| IEXEC_WORKER_DOCKER_REGISTRY_ADDRESS_1        | Secondary registry address.                                                                                                      | String           |                                                           |
| IEXEC_WORKER_DOCKER_REGISTRY_USERNAME_1       | Username to pull apps from secondary registry.                                                                                   | String           |                                                           |
| IEXEC_WORKER_DOCKER_REGISTRY_PASSWORD_1       | Password to pull apps from secondary registry.                                                                                   | String           |                                                           |
| IEXEC_WORKER_WALLET_PATH                      | Path to the wallet that should be used by the worker.                                                                            | String           | ./src/main/resources/wallet/encrypted-wallet_worker1.json |
| IEXEC_WORKER_WALLET_PASSWORD                  | Password of the wallet that should be used by the worker.                                                                        | String           | whatever                                                  |
| IEXEC_SCONE_SHOW_VERSION                      | Whether to display version of Scone.                                                                                             | Boolean          | true                                                      |
| IEXEC_SCONE_LOG_LEVEL                         | Log level Scone should use.                                                                                                      | [0,7] or String  | debug                                                     |
| IEXEC_WORKER_SCONTAIN_REGISTRY_NAME           | Name of the registry the LAS should be retrieved from.                                                                           | String           | registry.scontain.com:5050                                |
| IEXEC_WORKER_SCONTAIN_REGISTRY_USERNAME       | Username to connect to the registry the LAS should be retrieved from.                                                            | String           |                                                           |
| IEXEC_WORKER_SCONTAIN_REGISTRY_PASSWORD       | Password to connect to the registry the LAS should be retrieved from.                                                            | String           |                                                           |
| IEXEC_LAS_PORT                                | Port the LAS should be started on.                                                                                               | Positive integer | 18766                                                     |
| IEXEC_CORE_GRAYLOG_HOST                       | Graylog host.                                                                                                                    | String           | localhost                                                 |
| IEXEC_CORE_GRAYLOG_PORT                       | Graylog port.                                                                                                                    | Positive integer | 12201                                                     |
| IEXEC_WORKER_SGX_DRIVER_MODE                  | SGX driver that should be used                                                                                                   | { NONE, LEGACY } | NONE                                                      |
