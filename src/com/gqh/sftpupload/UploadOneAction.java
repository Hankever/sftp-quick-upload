package com.gqh.sftpupload;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 每台服务器对应一个菜单项，点它就把选中的文件传上去。
 */
public class UploadOneAction extends AnAction {

    private final ServerConfig server;

    public UploadOneAction(ServerConfig server) {
        super(server.getName(), "上传到 " + server.getHost() + ":" + server.getPort()
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
        UploadRunner.run(project, server, files);
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
