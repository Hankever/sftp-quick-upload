package com.gqh.sftpupload;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 项目级持久化：每个项目一份服务器配置，存在 &lt;项目&gt;/.idea/sftpQuickUpload.xml。
 * <p>
 * 1.1.0 之前是应用级（所有项目共用一份），容易把 A 项目的文件传到 B 项目的服务器上；
 * 旧配置改为由 {@link LegacyConfig} 只读保留，需要时可在配置窗口里一键导入本项目。
 */
@State(name = "SftpQuickUpload", storages = @Storage("sftpQuickUpload.xml"))
public class ServersState implements PersistentStateComponent<ServersState.State> {

    public static class State {
        public List<ServerConfig> servers = new ArrayList<>();
    }

    private final Project project;
    private State state = new State();

    public ServersState(Project project) {
        this.project = project;
    }

    public static @Nullable ServersState getInstance(@Nullable Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        return project.getService(ServersState.class);
    }

    /** 当前项目的服务器列表；没有项目时返回空列表，调用方不用判空。 */
    public static @NotNull List<ServerConfig> serversOf(@Nullable Project project) {
        ServersState service = getInstance(project);
        return service == null ? Collections.emptyList() : service.getServers();
    }

    /** 把旧版全局配置导入当前项目，返回新增条数（同一目标已存在则跳过）。 */
    public static int importLegacy(@Nullable Project project) {
        ServersState service = getInstance(project);
        if (service == null) {
            return 0;
        }
        List<ServerConfig> target = service.getServers();
        int added = 0;
        for (ServerConfig legacy : LegacyConfig.servers()) {
            boolean exists = target.stream().anyMatch(c -> sameTarget(c, legacy));
            if (!exists) {
                target.add(legacy.copy());
                added++;
            }
        }
        return added;
    }

    static boolean sameTarget(ServerConfig a, ServerConfig b) {
        return a.getHost().equals(b.getHost())
                && a.getPort() == b.getPort()
                && a.getUsername().equals(b.getUsername())
                && a.getRemoteRoot().equals(b.getRemoteRoot());
    }

    @Override
    public @NotNull State getState() {
        if (state == null) {
            state = new State();
        }
        if (state.servers == null) {
            state.servers = new ArrayList<>();
        }
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public List<ServerConfig> getServers() {
        return getState().servers;
    }

    public void setServers(List<ServerConfig> servers) {
        getState().servers = servers;
    }
}
