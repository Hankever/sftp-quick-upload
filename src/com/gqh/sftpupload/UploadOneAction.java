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
 * 每台服务器对应一个菜单项，点它就把选中的文件按路径映射分发上传。
 */
public class UploadOneAction extends AnAction {

    private final ServerConfig server;

    public UploadOneAction(ServerConfig server, String preview) {
        super(menuText(server, preview), description(server), null);
        this.server = server;
    }

    private static String menuText(ServerConfig server, String preview) {
        String base = server.menuText() + "   (" + server.getHost() + ":" + server.getPort() + ")";
        return preview.isEmpty() ? base : base + "   " + preview;
    }

    private static String description(ServerConfig server) {
        String head = "上传到 " + server.getUsername() + "@" + server.getHost() + ":" + server.getPort();
        String mappings = server.mappingsSummary();
        return mappings.isEmpty() ? head : head + "\n" + mappings;
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
        String preview = UploadRunner.previewText(project, server, files);
        String message = "即将把 " + files.length + " 个选中项上传到【生产环境】\n\n"
                + "服务器：" + server.getName() + "\n"
                + "地址：" + server.getUsername() + "@" + server.getHost() + ":" + server.getPort() + "\n"
                + "将分发到：" + (preview.isEmpty() ? "（选中项不在任何映射目录下）" : preview)
                + "\n" + server.mappingsSummary()
                + "\n\n确认目标是生产服务器并且要上传吗？";
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
