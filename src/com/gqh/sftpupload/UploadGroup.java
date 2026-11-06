package com.gqh.sftpupload;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 右键菜单里的一级项，展开后是「设置服务器信息」+ 每台已配置的服务器。
 */
public class UploadGroup extends ActionGroup {

    public UploadGroup() {
        super("上传到服务器", true);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        List<AnAction> items = new ArrayList<>();
        items.add(new SettingsAction());
        items.add(Separator.getInstance());

        List<ServerConfig> servers = ServersState.getInstance().getServers();
        if (servers.isEmpty()) {
            items.add(new AnAction("还没有配置服务器", "点上面一项添加", null) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    // 占位条目，不可点击
                }

                @Override
                public void update(@NotNull AnActionEvent event) {
                    event.getPresentation().setEnabled(false);
                }

                @Override
                public @NotNull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.EDT;
                }
            });
        } else {
            for (ServerConfig server : servers) {
                items.add(new UploadOneAction(server));
            }
        }
        return items.toArray(AnAction.EMPTY_ARRAY);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
