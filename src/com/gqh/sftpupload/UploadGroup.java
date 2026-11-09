package com.gqh.sftpupload;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 右键菜单里的一级项，展开后是「设置服务器信息」+ 当前项目配置的每台服务器。
 */
public class UploadGroup extends ActionGroup {

    public UploadGroup() {
        super("上传到服务器", true);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        Project project = e == null ? null : e.getProject();
        List<AnAction> items = new ArrayList<>();
        items.add(new SettingsAction());
        items.add(Separator.getInstance());

        List<ServerConfig> servers = ServersState.serversOf(project);
        if (!servers.isEmpty()) {
            VirtualFile[] selected = e == null ? null : e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
            for (ServerConfig server : servers) {
                items.add(new UploadOneAction(server, previewOf(project, server, selected)));
            }
            return items.toArray(AnAction.EMPTY_ARRAY);
        }

        if (project != null && !LegacyConfig.isEmpty()) {
            items.add(new AnAction("导入旧版全局配置…", "把旧版所有项目共用的服务器配置导入到本项目", null) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    SettingsAction.openSettings(event.getProject(), true);
                }

                @Override
                public @NotNull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.BGT;
                }
            });
        }
        items.add(new AnAction("当前项目还没有配置服务器", "点上面「设置服务器信息」添加", null) {
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
        return items.toArray(AnAction.EMPTY_ARRAY);
    }

    /**
     * 只有当选中的东西会分到两条以上映射时，才在菜单里提示分发结果，
     * 例如「网页 3 · 资源 2」；单一映射时不加噪。
     */
    private static String previewOf(Project project, ServerConfig server, VirtualFile[] selected) {
        if (project == null || selected == null || selected.length == 0) {
            return "";
        }
        String preview = UploadRunner.previewText(project, server, selected);
        return preview.contains("·") ? preview : "";
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
