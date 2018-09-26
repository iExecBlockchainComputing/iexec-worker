package com.iexec.worker;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private CoreClient coreClient;

    public Controller(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @GetMapping("/michel")
    public String hello(@RequestParam(name="name", required=false, defaultValue="Stranger") String name) {
        return coreClient.hello(name);
    }
}