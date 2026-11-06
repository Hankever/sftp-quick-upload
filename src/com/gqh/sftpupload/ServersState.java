package com.gqh.sftpupload;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用级持久化，配置存在 IDE 配置目录的 sftpQuickUpload.xml 里，所有项目共用。
 */
@State(name = "SftpQuickUpload", storages = @Storage("sftpQuickUpload.xml"))
public class ServersState implements PersistentStateComponent<ServersState.State> {

    public static class State {
        public List<ServerConfig> servers = new ArrayList<>();
    }

    private State state = new State();

    public static ServersState getInstance() {
        return ApplicationManager.getApplication().getService(ServersState.class);
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
