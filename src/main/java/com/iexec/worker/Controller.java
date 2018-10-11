package com.iexec.worker;

import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.task.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private TaskService taskService;
    private DockerComputationService dockerComputationService;

    @Autowired
    public Controller(DockerComputationService dockerComputationService,
                      TaskService taskService) {
        this.dockerComputationService = dockerComputationService;
        this.taskService = taskService;

    }

    @GetMapping("/getTask")
    public String getTask() {
        return taskService.getTask();
    }

}