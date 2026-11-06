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
    /** 生产环境：菜单里带【生产】前缀，上传前会二次确认。 */
    private boolean production = false;

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

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
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
        c.production = production;
        return c;
    }

    /** 对话框里「复制」用：内容一样，但换一个新 id。 */
    public ServerConfig duplicate() {
        ServerConfig c = copy();
        c.id = UUID.randomUUID().toString();
        c.name = getName() + " 副本";
        return c;
    }

    /** 右键菜单里显示的名字，生产环境加前缀，避免点错。 */
    public String menuText() {
        return production ? "【生产】" + getName() : getName();
    }

    /** 配置窗口左侧列表的显示文本。 */
    public String listText() {
        return menuText() + "   (" + getHost() + ")";
    }

    public String displayText() {
        String remote = getRemoteRoot();
        String tail = remote.isEmpty() ? "" : "  →  " + remote;
        return getHost() + ":" + getPort() + tail;
    }
}
