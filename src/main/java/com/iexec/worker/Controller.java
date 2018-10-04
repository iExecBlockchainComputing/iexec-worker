package com.iexec.worker;

import com.iexec.worker.docker.DockerService;
import com.iexec.worker.task.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private TaskService taskService;
    private DockerService dockerService;

    @Autowired
    public Controller(DockerService dockerService,
                      TaskService taskService) {
        this.dockerService = dockerService;
        this.taskService = taskService;

    }

    @GetMapping("/getTask")
    public String getTask() {
        return taskService.getTask();
    }

}