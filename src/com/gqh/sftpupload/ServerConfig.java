package com.gqh.sftpupload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一台服务器的配置。字段全部按 JavaBean 规范暴露，IDEA 的 XmlSerializer 依赖 getter/setter 持久化。
 * <p>
 * 1.2.0 起一台服务器可以有多条路径映射（网页目录、资源目录…），选中的文件按所在目录自动分发。
 */
public class ServerConfig {

    private String id = UUID.randomUUID().toString();
    private String name = "新服务器";
    private String host = "";
    private int port = 22;
    private String username = "";
    /** 生产环境：菜单里带【生产】前缀，上传前会二次确认。 */
    private boolean production = false;

    private List<PathMapping> mappings = new ArrayList<>();

    /** 上传成功后按规则调用接口（如清 XML 配置缓存），没有配置时默认关闭。 */
    private PostUploadConfig postUpload = new PostUploadConfig();

    /** 1.1.x 及更早版本只有一条映射，这两个字段仅用于读取旧配置，迁移后会被清空。 */
    private String localRoot;
    private String remoteRoot;

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

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }

    public List<PathMapping> getMappings() {
        if (mappings == null) {
            mappings = new ArrayList<>();
        }
        return mappings;
    }

    public void setMappings(List<PathMapping> mappings) {
        this.mappings = mappings;
    }

    public void addMapping(PathMapping mapping) {
        getMappings().add(mapping);
    }

    public PostUploadConfig getPostUpload() {
        if (postUpload == null) {
            postUpload = new PostUploadConfig();
        }
        return postUpload;
    }

    public void setPostUpload(PostUploadConfig postUpload) {
        this.postUpload = postUpload;
    }

    // ---- 旧版单映射字段，仅供 XmlSerializer 读取 1.1.x 及更早的配置 ----

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getRemoteRoot() {
        return remoteRoot;
    }

    public void setRemoteRoot(String remoteRoot) {
        this.remoteRoot = remoteRoot;
    }

    /** 配置加载后调用：把旧版的单条映射搬进 mappings，然后清空旧字段（不再写回）。 */
    public void migrateLegacyMapping() {
        String legacyRemote = getRemoteRoot();
        if (getMappings().isEmpty() && legacyRemote != null && !legacyRemote.trim().isEmpty()) {
            String legacyLocal = getLocalRoot();
            if (legacyLocal == null || legacyLocal.trim().isEmpty()) {
                legacyLocal = "src/main/webapp";
            }
            addMapping(new PathMapping("网页", legacyLocal.trim(), legacyRemote.trim()));
        }
        localRoot = null;
        remoteRoot = null;
    }

    public ServerConfig copy() {
        ServerConfig c = new ServerConfig();
        c.id = id;
        c.name = name;
        c.host = host;
        c.port = port;
        c.username = username;
        c.production = production;
        c.mappings = new ArrayList<>();
        for (PathMapping m : getMappings()) {
            c.mappings.add(m.copy());
        }
        c.postUpload = getPostUpload().copy();
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

    /** 每条映射一行，用于菜单描述、确认弹窗。 */
    public String mappingsSummary() {
        StringBuilder sb = new StringBuilder();
        for (PathMapping m : getMappings()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("  ").append(m.getName()).append("：")
                    .append(m.getLocalRoot()).append(" → ").append(m.getRemoteRoot());
        }
        return sb.toString();
    }
}
