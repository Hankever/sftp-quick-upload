package com.gqh.sftpupload;

import java.util.UUID;

/**
 * 一台服务器的配置。字段全部按 JavaBean 规范暴露，IDEA 的 XmlSerializer 依赖 getter/setter 持久化。
 */
public class ServerConfig {

    private String id = UUID.randomUUID().toString();
    private String name = "新服务器";
    private String host = "";
    private int port = 22;
    private String username = "";
    private String localRoot = "src/main/webapp";
    private String remoteRoot = "";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host == null ? "" : host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLocalRoot() {
        return localRoot == null || localRoot.isEmpty() ? "src/main/webapp" : localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getRemoteRoot() {
        return remoteRoot == null ? "" : remoteRoot;
    }

    public void setRemoteRoot(String remoteRoot) {
        this.remoteRoot = remoteRoot;
    }

    public ServerConfig copy() {
        ServerConfig c = new ServerConfig();
        c.id = id;
        c.name = name;
        c.host = host;
        c.port = port;
        c.username = username;
        c.localRoot = localRoot;
        c.remoteRoot = remoteRoot;
        return c;
    }

    public String displayText() {
        String remote = getRemoteRoot();
        String tail = remote.isEmpty() ? "" : "  →  " + remote;
        return getHost() + ":" + getPort() + tail;
    }
}
