package com.gqh.sftpupload;

/**
 * 一条路径映射：项目里的某个本地目录 → 服务器上的某个远端目录。
 * 上传时按「远端目录 + (文件路径 - 本地目录)」算目标位置，所以本地子结构会原样保留。
 */
public class PathMapping {

    private String name = "网页";
    private String localRoot = "src/main/webapp";
    private String remoteRoot = "";

    public PathMapping() {
    }

    public PathMapping(String name, String localRoot, String remoteRoot) {
        this.name = name;
        this.localRoot = localRoot;
        this.remoteRoot = remoteRoot;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalRoot() {
        return localRoot == null ? "" : localRoot;
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

    public PathMapping copy() {
        return new PathMapping(name, localRoot, remoteRoot);
    }

    /** 列表里显示的文本。 */
    public String listText() {
        return getName() + "   " + getLocalRoot() + "  →  " + getRemoteRoot();
    }
}
