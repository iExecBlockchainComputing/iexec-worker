package com.iexec.worker;


import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ExecCreation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @Value("${worker.name}")
    private String workerName;
    private CoreClient coreClient;

    public Controller(CoreClient coreClient) {
        this.coreClient = coreClient;
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

    @GetMapping("/docker/run")
    public ContainerResult dockerRun(@RequestParam(name = "image", required = false, defaultValue = "ubuntu") String image,
                            @RequestParam(name = "tag", required = false, defaultValue = "latest") String tag,
                            @RequestParam(name = "cmd", required = false, defaultValue = "ls") String cmd) {
        DockerClient docker = null;
        try {
            docker = DefaultDockerClient.fromEnv().build();
        } catch (DockerCertificateException e) {
            e.printStackTrace();
        }
        if (docker == null) {
            return null;
        }

        String imageWithTag = image.concat(":").concat(tag);
        final ContainerConfig containerConfig = ContainerConfig.builder()
                .image(imageWithTag)
                .cmd(cmd)
                .build();

        ContainerResult containerResult = null;
        try {
            docker.pull(imageWithTag);
            ContainerCreation creation = docker.createContainer(containerConfig);
            String id = creation.id();
            docker.startContainer(id);
            String execOutput = docker.logs(id, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr()).readFully();
            containerResult = new ContainerResult(image, tag, cmd, id, execOutput);
            docker.killContainer(id);
            docker.removeContainer(id);
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }
        docker.close();

        return containerResult;
    }
}