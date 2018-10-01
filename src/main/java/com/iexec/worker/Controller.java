package com.iexec.worker;


import com.iexec.worker.docker.ContainerResult;
import com.iexec.worker.docker.DockerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @Value("${worker.name}")
    private String workerName;
    private CoreClient coreClient;
    private DockerService dockerService;

    @Autowired
    public Controller(CoreClient coreClient, DockerService dockerService) {
        this.coreClient = coreClient;
        this.dockerService = dockerService;
    }

    @GetMapping("/michel")
    public String hello(@RequestParam(name = "name", required = false, defaultValue = "Stranger") String name) {
        return coreClient.hello(name);
    }

    @GetMapping("/getTask")
    public String getTask() {
        Replicate replicate = coreClient.getReplicate(workerName);
        if (replicate.getTaskId() == null) {
            return "NO TASK AVAILABLE";
        }

        coreClient.updateReplicateStatus(replicate.getTaskId(), ReplicateStatus.RUNNING, workerName);

        // simulate some work on the task
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        coreClient.updateReplicateStatus(replicate.getTaskId(), ReplicateStatus.COMPLETED, workerName);
        return ReplicateStatus.COMPLETED.toString();
    }

    //http://localhost:18091/docker/run?image=ubuntu&tag=latest&cmd=ls
    @GetMapping("/docker/run")
    public ContainerResult dockerRun(@RequestParam(name = "image", required = false, defaultValue = "ubuntu") String image,
                                     @RequestParam(name = "tag", required = false, defaultValue = "latest") String tag,
                                     @RequestParam(name = "cmd", required = false, defaultValue = "") String cmd) {
        return dockerService.dockerRun(image, tag, cmd);
    }
}