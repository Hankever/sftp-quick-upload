package com.gqh.sftpupload;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 子菜单第一项：打开当前项目的服务器配置窗口。
 */
public class SettingsAction extends AnAction {

    public SettingsAction() {
        super("设置服务器信息…", "添加、修改、删除当前项目的服务器", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        openSettings(e.getProject(), false);
    }

    /**
     * @param importLegacyFirst 先把旧版全局配置导入本项目，再打开窗口让人确认
     */
    static void openSettings(@Nullable Project project, boolean importLegacyFirst) {
        if (project == null) {
            return;
        }
        if (importLegacyFirst) {
            int added = ServersState.importLegacy(project);
            if (added > 0) {
                UploadRunner.showNotification(project, "已导入旧版配置",
                        "已把 " + added + " 台服务器导入到项目「" + project.getName() + "」，请检查目标是否正确，改完记得保存",
                        NotificationType.INFORMATION);
            } else {
                UploadRunner.showNotification(project, "没有可导入的配置",
                        "旧版全局配置已为空，或者本项目已有相同的服务器", NotificationType.WARNING);
            }
        }
        SettingsDialog dialog = new SettingsDialog(project);
        if (dialog.showAndGet()) {
            dialog.save();
        }
    }

}
