package com.iexec.worker;

import com.iexec.worker.task.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private TaskService taskService;

    @Autowired
    public Controller(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/askForReplicate")
    public String getTask() {
        return taskService.askForReplicate();
    }

}