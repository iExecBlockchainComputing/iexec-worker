package com.iexec.worker;

import com.iexec.worker.replicate.ReplicateDemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private ReplicateDemandService replicateDemandService;

    @Autowired
    public Controller(ReplicateDemandService replicateDemandService) {
        this.replicateDemandService = replicateDemandService;
    }

    @GetMapping("/askForReplicate")
    public String getTask() {
        return replicateDemandService.askForReplicate();
    }

}