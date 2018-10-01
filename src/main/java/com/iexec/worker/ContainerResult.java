package com.iexec.worker;

public class ContainerResult {

    private String image;
    private String tag;
    private String cmd;
    private String containerId;
    private String stdout;


    public ContainerResult() {
    }

    public ContainerResult(String image, String tag, String cmd, String containerId, String stdout) {
        this.image = image;
        this.tag = tag;
        this.cmd = cmd;
        this.containerId = containerId;
        this.stdout = stdout;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }
}
