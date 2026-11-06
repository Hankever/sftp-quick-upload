package com.gqh.sftpupload;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 子菜单第一项：打开服务器配置窗口。
 */
public class SettingsAction extends AnAction {

    public SettingsAction() {
        super("设置服务器信息…", "添加、修改、删除服务器", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        SettingsDialog dialog = new SettingsDialog(e.getProject());
        if (dialog.showAndGet()) {
            dialog.save();
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
