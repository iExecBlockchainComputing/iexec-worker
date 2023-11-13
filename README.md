# iExec Worker

## Overview

The _iExec Worker_ participates in a workerpool by computing tasks purchased by requesters on the iExec marketplace.
The _iExec Worker_ must connect to the _iExec Core Scheduler_ of the workerpool to actively participate in the computation.

After registration, the _iExec Worker_ sends a signal to the _iExec Core Scheduler_ at a fixed rate.
This signal is used to let the _iExec Core Scheduler_ be aware of the _iExec Worker_ liveness.
At the moment, the rate is fixed and this signal is sent every 10 seconds.
The _iExec Worker_ will be considered as lost after several failures.

The _iExec Worker_ is available as an OCI image on [Docker Hub](https://hub.docker.com/r/iexechub/iexec-worker/tags).

## Configuration

You can configure the _iExec Worker_ with the following properties:

| Environment variable                            | Description                                                                                                                                                        | Type                     | Default value                                               |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|-------------------------------------------------------------|
| `IEXEC_WORKER_PORT`                             | Server HTTP port of the _iExec Worker_.                                                                                                                            | Positive integer         | `13100`                                                     |
| `IEXEC_CORE_PROTOCOL`                           | Protocol to connect to the _iExec Core Scheduler_.                                                                                                                 | String                   | `http`                                                      |
| `IEXEC_CORE_HOST`                               | Host to connect to the _iExec Core Scheduler_.                                                                                                                     | String                   | `localhost`                                                 |
| `IEXEC_CORE_PORT`                               | Port to connect to the _iExec Core Scheduler_.                                                                                                                     | Positive integer         | `13000`                                                     |
| `IEXEC_WORKER_NAME`                             | Public name of the _iExec Worker_.                                                                                                                                 | String                   | `worker`                                                    |
| `IEXEC_WORKER_BASE_DIR`                         | Path to the folder within which the _iExec Worker_ will read-and-write inputs and outputs of tasks.                                                                | String                   | `/tmp/iexec-worker`                                         |
| `IEXEC_WORKER_OVERRIDE_AVAILABLE_CPU_COUNT`     | Number of CPUs available for computing distinct tasks. If not set, n-1 CPUs will be used, where n is the number of available processors.                           | Positive integer         |                                                             |
| `IEXEC_WORKER_GPU_ENABLED`                      | Declares if the _iExec Worker_ is able to compute tasks requesting GPU mode. Note that if it is true, `IEXEC_WORKER_OVERRIDE_AVAILABLE_CPU_COUNT` will be ignored. | Boolean                  | `false`                                                     |
| `IEXEC_GAS_PRICE_MULTIPLIER`                    | Transactions will be sent with `networkGasPrice * IEXEC_GAS_PRICE_MULTIPLIER`.                                                                                     | Float                    | `1.3`                                                       |
| `IEXEC_GAS_PRICE_CAP`                           | In Wei, will be used for transactions if `networkGasPrice * IEXEC_GAS_PRICE_MULTIPLIER > gasPriceCap`.                                                             | Positive integer         | `22000000000`                                               |
| `IEXEC_WORKER_OVERRIDE_BLOCKCHAIN_NODE_ADDRESS` | `If set, will be used instead of the address given by the blockchain adapter.                                                                                      | String                   |                                                             |
| `IEXEC_DEVELOPER_LOGGER_ENABLED`                | Whether to print application logs of tasks.                                                                                                                        | Boolean                  | `false`                                                     |
| `IEXEC_WORKER_TEE_COMPUTE_MAX_HEAP_SIZE_GB`     | Max heap size for TEE apps.                                                                                                                                        | Positive integer         | `8`                                                         |
| `IEXEC_WORKER_DOCKER_NETWORK_NAME`              | Internal Docker network name of the _iExec Worker_. Required for communication between worker and launched-by-worker containers.                                   | String                   | `iexec-worker-net`                                          |
| `IEXEC_WORKER_DOCKER_REGISTRY_USERNAME_0`       | Username to pull apps from [official Docker registry](https://hub.docker.com/).                                                                                    | String                   |                                                             |
| `IEXEC_WORKER_DOCKER_REGISTRY_PASSWORD_0`       | Password to  pull apps from [official Docker registry](https://hub.docker.com/).                                                                                   | String                   |                                                             |
| `IEXEC_WORKER_DOCKER_REGISTRY_ADDRESS_1`        | Custom Docker registry address.                                                                                                                                    | String                   |                                                             |
| `IEXEC_WORKER_DOCKER_REGISTRY_USERNAME_1`       | Username to pull apps from custom Docker registry.                                                                                                                 | String                   |                                                             |
| `IEXEC_WORKER_DOCKER_REGISTRY_PASSWORD_1`       | Password to pull apps from custom Docker registry.                                                                                                                 | String                   |                                                             |
| `IEXEC_WORKER_DOCKER_IMAGE_MIN_PULL_TIMEOUT`    | Minimum timeout to pull Dapp images.                                                                                                                               | String                   | `PT5M`                                                      |
| `IEXEC_WORKER_DOCKER_IMAGE_MAX_PULL_TIMEOUT`    | Maximum timeout to pull Dapp images.                                                                                                                               | String                   | `PT30M`                                                     |
| `IEXEC_WORKER_WALLET_PATH`                      | Path to the wallet that should be used by the _iExec Worker_.                                                                                                      | String                   | `./src/main/resources/wallet/encrypted-wallet_worker1.json` |
| `IEXEC_WORKER_WALLET_PASSWORD`                  | Password of the wallet that should be used by the _iExec Worker_.                                                                                                  | String                   | `whatever`                                                  |
| `IEXEC_SCONE_SHOW_VERSION`                      | Whether to display version of Scone.                                                                                                                               | Boolean                  | `true`                                                      |
| `IEXEC_SCONE_LOG_LEVEL`                         | Log level Scone should use.                                                                                                                                        | [0,7] or String          | `debug`                                                     |
| `IEXEC_WORKER_SCONTAIN_REGISTRY_NAME`           | Host of the Scontain registry. Currently used to pull LAS image.                                                                                                   | String                   | `registry.scontain.com:5050`                                |
| `IEXEC_WORKER_SCONTAIN_REGISTRY_USERNAME`       | Username to connect to the Scontain registry.                                                                                                                      | String                   |                                                             |
| `IEXEC_WORKER_SCONTAIN_REGISTRY_PASSWORD`       | Password to connect to the Scontain registry.                                                                                                                      | String                   |                                                             |
| `IEXEC_LAS_PORT`                                | Port the LAS should be started on.                                                                                                                                 | Positive integer         | `18766`                                                     |
| `IEXEC_WORKER_SGX_DRIVER_MODE`                  | Intel® SGX driver that should be used.                                                                                                                             | { NONE, LEGACY, NATIVE } | `NONE`                                                      |
| `IEXEC_WORKER_METRICS_WINDOW_SIZE`              | Number of pre/app/post-compute duration used to compute metrics.                                                                                                   | Positive integer         | `1,000`                                                     |

## Health checks

A health endpoint (`/actuator/health`) is enabled by default and can be accessed on the `IEXEC_WORKER_PORT`.
This endpoint allows to define health checks in an orchestrator or a [compose file](https://github.com/compose-spec/compose-spec/blob/master/spec.md#healthcheck).
No default strategy has been implemented in the [Dockerfile](Dockerfile) at the moment.

## Metrics

A metrics endpoint (`/metrics`) is available. It currently exposes data on TEE pre-compute, app-compute and TEE post-compute durations. These metrics are computed on the
latest `IEXEC_WORKER_METRICS_WINDOW_SIZE` executions of each stage.

⚠ As pre-compute is optional, metrics of each stage are not consistent. E.g.: if `IEXEC_WORKER_METRICS_WINDOW_SIZE` replicates are executed, each of those replicates without any dataset nor input
file, then none of the pre-compute durations relates to any of app-compute or post-compute durations. So, treat these metrics with care.

## Build from sources

*Please first update your config located in `./src/main/resources/application.yml`*

```
./gradlew build --refresh-dependencies
```

## License

This repository code is released under the [Apache License 2.0](LICENSE).
