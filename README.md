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


#### With Docker

```
docker run --name my-iexec-worker IEXEC_WORKER_NAME=my-iexec-worker -e IEXEC_CORE_HOST=52.X.X.X -e IEXEC_CORE_PORT=18090 -v /var/run/docker.sock:/var/run/docker.sock -v /tmp/iexec-worker1:/tmp/iexec-worker1 -e IEXEC_WORKER_RESULT_BASE_DIR=/tmp/iexec-worker1 -e -v ~/my-wallet-folder/wallet.json:/wallet.json -e IEXEC_WORKER_WALLET_PASSWORD=mywalletpassword iexechub/iexec-worker:3.0.0-alpha3
```
