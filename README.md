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
