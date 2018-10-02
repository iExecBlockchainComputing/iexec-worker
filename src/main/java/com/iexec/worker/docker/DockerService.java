package com.iexec.worker.docker;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Volume;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class DockerService {

    private final String REMOTE_PATH = "/iexec";
    private final String LOCAL_PATH = "/home/james/iexec2";
    private final String LOCAL_BASE_VOLUME = "iexec-worker-volume";

    private DefaultDockerClient docker;

    @PostConstruct
    public void onPostConstruct() throws DockerCertificateException {
        docker = DefaultDockerClient.fromEnv().build();
    }

    public ContainerResult dockerRun(String image, String cmd) {
        //TODO: check image equals image:tag

        final Volume volume = createVolume(LOCAL_BASE_VOLUME);
        final HostConfig hostConfig = createHostConfig(LOCAL_BASE_VOLUME, REMOTE_PATH);
        final ContainerConfig containerConfig = createContainerConfig(image, cmd, hostConfig);

        ContainerResult containerResult = null;
        try {
            pullImage(image);
            String id = startContainer(containerConfig);
            waitContainerForExitStatus(id);

            containerResult = ContainerResult.builder()
                    .image(image)
                    .cmd(cmd)
                    .containerId(id)
                    .stdout(getDockerLogs(id))
                    .build();

            copyFolderFromContainerToHost(id, REMOTE_PATH, LOCAL_PATH);

            docker.removeContainer(id);
        } catch (DockerException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return containerResult;
    }

    private Volume createVolume(String volumeName) {
        final Volume toCreate = Volume.builder()
                .name(volumeName)
                .driver("local")
                .build();
        try {
            docker.removeVolume(volumeName);
            return docker.createVolume(toCreate);
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }
        return toCreate;
    }

    private ContainerConfig createContainerConfig(String imageWithTag, String cmd, HostConfig hostConfig) {
        ContainerConfig.Builder builder = ContainerConfig.builder()
                .image(imageWithTag)
                .hostConfig(hostConfig);
        ContainerConfig containerConfig;
        if (cmd == null || cmd.isEmpty()) {
            containerConfig = builder.build();
        } else {
            containerConfig = builder.cmd(cmd).build();
        }
        return containerConfig;
    }

    private HostConfig createHostConfig(String from, String to) {
        return HostConfig.builder()
                .appendBinds(HostConfig.Bind.from(from)
                        .to(to)
                        .readOnly(false)
                        .build())
                .build();
    }

    private void pullImage(String image) throws DockerException, InterruptedException {
        docker.pull(image);
    }

    private String startContainer(ContainerConfig containerConfig) throws DockerException, InterruptedException {
        ContainerCreation creation = docker.createContainer(containerConfig);
        String id = creation.id();
        docker.startContainer(id);
        return id;
    }

    private void waitContainerForExitStatus(String id) throws DockerException, InterruptedException {
        //TODO: add category timeout
        while (!docker.inspectContainer(id).state().status().equals("exited")) {
            Thread.sleep(1000);
            log.info("Container just exited [containerId={}, status={}]", id, docker.inspectContainer(id).state().status());
        }
    }

    private String getDockerLogs(String id) throws DockerException, InterruptedException {
        return docker.logs(id, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr()).readFully();
    }

    private void copyFolderFromContainerToHost(String id, String remotePath, String localPath) throws DockerException, InterruptedException, IOException {
        final TarArchiveInputStream tarStream = new TarArchiveInputStream(docker.archiveContainer(id, remotePath));
        TarArchiveEntry entry;
        while ((entry = tarStream.getNextTarEntry()) != null) {
            log.info(entry.getName());
            if (entry.isDirectory()) {
                continue;
            }
            File curfile = new File(localPath, entry.getName());
            File parent = curfile.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            IOUtils.copy(tarStream, new FileOutputStream(curfile));
        }
    }

    @PreDestroy
    public void onPreDestroy() {
        docker.close();
    }

}
