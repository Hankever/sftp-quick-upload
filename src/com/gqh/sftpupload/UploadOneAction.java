package com.gqh.sftpupload;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 每台服务器对应一个菜单项，点它就把选中的文件传上去。
 */
public class UploadOneAction extends AnAction {

    private final ServerConfig server;

    public UploadOneAction(ServerConfig server) {
        super(server.menuText() + "   (" + server.getHost() + ":" + server.getPort() + ")",
                "上传到 " + server.getUsername() + "@" + server.getHost() + ":" + server.getPort()
                        + "  " + server.getRemoteRoot(), null);
        this.server = server;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || files == null || files.length == 0) {
            return;
        }
        if (server.isProduction() && !confirmProduction(project, files)) {
            return;
        }
        UploadRunner.run(project, server, files);
    }

    /** 生产环境多问一句，默认按钮是「取消」，回车不会误传。 */
    private boolean confirmProduction(Project project, VirtualFile[] files) {
        String message = "即将上传 " + files.length + " 个文件/目录到【生产环境】\n\n"
                + "服务器：" + server.getName() + "\n"
                + "地址：" + server.getUsername() + "@" + server.getHost() + ":" + server.getPort() + "\n"
                + "远端目录：" + server.getRemoteRoot() + "\n\n"
                + "确认目标是生产服务器并且要上传吗？";
        int result = Messages.showDialog(project, message, "生产环境 · 二次确认",
                new String[]{"取消", "确认上传"}, 0, Messages.getWarningIcon());
        return result == 1;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        e.getPresentation().setEnabled(files != null && files.length > 0);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
