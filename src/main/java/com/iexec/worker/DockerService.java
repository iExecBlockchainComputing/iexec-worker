package com.iexec.worker;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Service
public class DockerService {

    private DefaultDockerClient docker;

    @PostConstruct
    public void onPostConstruct() throws DockerCertificateException {
        docker = DefaultDockerClient.fromEnv().build();
    }

    public ContainerResult dockerRun(String image, String tag, String cmd) {
        String imageWithTag = image.concat(":").concat(tag);
        ContainerConfig.Builder builder = ContainerConfig.builder()
                .image(imageWithTag);
        ContainerConfig containerConfig;
        System.out.println("aaaa"+cmd);
        if (cmd == null || cmd.isEmpty()) {
            containerConfig = builder.build();
        } else {
            containerConfig = builder.cmd(cmd).build();
        }

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
        return containerResult;
    }

    @PreDestroy
    public void onPreDestroy() {
        docker.close();
    }

}
