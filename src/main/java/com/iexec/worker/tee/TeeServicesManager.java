package com.iexec.worker.tee;

import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.worker.tee.gramine.TeeGramineService;
import com.iexec.worker.tee.scone.TeeSconeService;
import org.springframework.stereotype.Service;

@Service
public class TeeServicesManager {

    private final TeeSconeService teeSconeService;
    private final TeeGramineService teeGramineService;

    public TeeServicesManager(TeeSconeService teeSconeService, TeeGramineService teeGramineService) {
        this.teeSconeService = teeSconeService;
        this.teeGramineService = teeGramineService;
    }

    public TeeService getTeeService(TeeEnclaveProvider teeEnclaveProvider) {
        if (teeEnclaveProvider == null) {
            throw new IllegalArgumentException("TEE enclave provider can't be null.");
        }

        switch (teeEnclaveProvider) {
            case SCONE:
                return teeSconeService;
            case GRAMINE:
                return teeGramineService;
            default:
                throw new IllegalArgumentException("No TEE service defined for this enclave provider.");
        }
    }
}
