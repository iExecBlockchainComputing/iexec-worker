package com.iexec.worker.tee;

import java.io.File;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SgxService {

    public static final String SGX_DEVICE_PATH = "/dev/isgx";
    public static final String SGX_DRIVER_PATH = "/sys/module/isgx/version";

    public boolean isSgxEnabled() {
        boolean isSgxDriverFound = new File(SGX_DRIVER_PATH).exists();
        boolean isSgxDeviceFound = new File(SGX_DEVICE_PATH).exists();

        if (!isSgxDriverFound) {
            log.debug("SGX driver not found");
            return false;
        }

        if (isSgxDriverFound && !isSgxDeviceFound) {
            log.warn("SGX driver is installed but no SGX device found. Please check if SGX is enabled");
            return false;
        }

        return true;
    }
}