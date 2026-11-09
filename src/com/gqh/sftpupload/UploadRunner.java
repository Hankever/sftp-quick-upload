package com.gqh.sftpupload;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把选中的文件/目录按路径映射分发到各自的远端目录并上传。
 */
final class UploadRunner {

    private UploadRunner() {
    }

    static void run(@NotNull Project project, @NotNull ServerConfig cfg, VirtualFile[] targets) {
        if (targets == null || targets.length == 0) {
            return;
        }
        String basePath = project.getBasePath();
        if (basePath == null) {
            showNotification(project, "上传失败", "拿不到项目根目录", NotificationType.ERROR);
            return;
        }
        if (cfg.getMappings().isEmpty()) {
            showNotification(project, "上传失败",
                    "服务器「" + cfg.getName() + "」还没有配置路径映射", NotificationType.ERROR);
            return;
        }

        final Path projectBase = Paths.get(basePath);
        final List<Path> paths = new ArrayList<>();
        for (VirtualFile vf : targets) {
            paths.add(Paths.get(vf.getPath()));
        }

        final String password = resolvePassword(project, cfg);
        if (password == null) {
            return;
        }

        new Task.Backgroundable(project, "上传到 " + cfg.menuText(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                UploadPlan plan = UploadPlan.build(projectBase, cfg.getMappings(), paths);
                if (plan.totalFiles() == 0) {
                    showNotification(project, "没有可上传的文件", emptyMessage(cfg), NotificationType.WARNING);
                    return;
                }

                indicator.setIndeterminate(false);
                int total = plan.totalFiles();
                int done = 0;
                StringBuilder summary = new StringBuilder();
                try (SftpUploader uploader = SftpUploader.connect(cfg, password)) {
                    for (UploadPlan.Group group : plan.groups.values()) {
                        PathMapping mapping = group.mapping;
                        String remoteRoot = trimTrailingSlash(mapping.getRemoteRoot());
                        int count = 0;
                        for (Path file : group.files) {
                            if (indicator.isCanceled()) {
                                return;
                            }
                            indicator.setFraction((double) done / total);
                            indicator.setText2(mapping.getName() + " · " + file.getFileName());

                            Path rel = group.localRoot.relativize(file);
                            String remote = remoteRoot + "/" + rel.toString().replace('\\', '/');
                            uploader.upload(file, remote);
                            done++;
                            count++;
                        }
                        summary.append("\n  ").append(mapping.getName()).append(" ").append(count)
                                .append(" 个 → ").append(remoteRoot);
                    }
                    indicator.setFraction(1.0);

                    StringBuilder msg = new StringBuilder();
                    msg.append("已上传 ").append(done).append(" 个文件到 ").append(cfg.menuText())
                            .append("\n").append(cfg.getUsername()).append("@").append(cfg.getHost())
                            .append(":").append(cfg.getPort())
                            .append(summary);
                    String extra = extraNotes(plan);
                    if (!extra.isEmpty()) {
                        msg.append("\n\n").append(extra);
                    }
                    showNotification(project, "上传完成", msg.toString(),
                            plan.unmatched.isEmpty() && plan.walkErrors.isEmpty()
                                    ? NotificationType.INFORMATION : NotificationType.WARNING);
                } catch (Exception e) {
                    showNotification(project, "上传失败", describe(e), NotificationType.ERROR);
                }
            }
        }.queue();
    }

    /** 按映射预览这次会传到哪几个远端目录，用于菜单文案和生产环境确认弹窗。 */
    static String previewText(@NotNull Project project, @NotNull ServerConfig cfg, VirtualFile[] targets) {
        String basePath = project.getBasePath();
        if (basePath == null || cfg.getMappings().isEmpty() || targets == null || targets.length == 0) {
            return "";
        }
        List<Path> paths = new ArrayList<>();
        for (VirtualFile vf : targets) {
            paths.add(Paths.get(vf.getPath()));
        }
        Map<String, Integer> preview = UploadPlan.preview(Paths.get(basePath), cfg.getMappings(), paths);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : preview.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(e.getKey()).append(" ").append(e.getValue());
        }
        return sb.toString();
    }

    private static String emptyMessage(ServerConfig cfg) {
        StringBuilder sb = new StringBuilder("选中的内容不在任何映射目录下。当前映射：");
        for (PathMapping m : cfg.getMappings()) {
            sb.append("\n  ").append(m.getName()).append("：").append(m.getLocalRoot());
        }
        return sb.toString();
    }

    private static String extraNotes(UploadPlan plan) {
        StringBuilder sb = new StringBuilder();
        if (!plan.unmatched.isEmpty()) {
            sb.append("忽略 ").append(plan.unmatched.size()).append(" 个不在任何映射目录下的路径：\n");
            int limit = Math.min(5, plan.unmatched.size());
            for (int i = 0; i < limit; i++) {
                sb.append("  ").append(plan.unmatched.get(i)).append('\n');
            }
        }
        if (!plan.walkErrors.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("有 ").append(plan.walkErrors.size()).append(" 个目录读取失败：\n");
            for (int i = 0; i < Math.min(3, plan.walkErrors.size()); i++) {
                sb.append("  ").append(plan.walkErrors.get(i)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static String resolvePassword(Project project, ServerConfig cfg) {
        String saved = PasswordUtil.get(cfg);
        if (saved != null && !saved.isEmpty()) {
            return saved;
        }
        PasswordDialog dialog = new PasswordDialog(project, cfg);
        if (!dialog.showAndGet()) {
            return null;
        }
        String pwd = dialog.getPassword();
        if (dialog.isRemember()) {
            PasswordUtil.store(cfg, pwd);
        }
        return pwd;
    }

    static String describe(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = e.getClass().getSimpleName();
        }
        if (msg.contains("Auth fail")) {
            return "认证失败：用户名或密码不对\n" + msg;
        }
        return msg;
    }

    static void showNotification(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("SftpQuickUpload")
                .createNotification(title, content, type)
                .notify(project);
    }

    static String trimTrailingSlash(String s) {
        String r = s.trim();
        while (r.endsWith("/") && r.length() > 1) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }
}
