package com.gqh.sftpupload;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 把选中的文件/目录映射到远端路径并上传。
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

        Path localRoot = Paths.get(basePath).resolve(cfg.getLocalRoot()).normalize();
        final String remoteRoot = trimTrailingSlash(cfg.getRemoteRoot());
        if (remoteRoot.isEmpty()) {
            showNotification(project, "上传失败", "服务器「" + cfg.getName() + "」没有配置远端目录", NotificationType.ERROR);
            return;
        }

        final List<Path> files = new ArrayList<>();
        for (VirtualFile vf : targets) {
            Path p = Paths.get(vf.getPath());
            if (Files.isDirectory(p)) {
                try (Stream<Path> walk = Files.walk(p)) {
                    walk.filter(Files::isRegularFile).forEach(files::add);
                } catch (IOException e) {
                    showNotification(project, "上传失败", "读取目录失败：" + p, NotificationType.ERROR);
                    return;
                }
            } else {
                files.add(p);
            }
        }
        if (files.isEmpty()) {
            showNotification(project, "没有可上传的文件", "选中的都是空目录", NotificationType.WARNING);
            return;
        }

        final String password = resolvePassword(project, cfg);
        if (password == null) {
            return;
        }

        new Task.Backgroundable(project, "上传到 " + cfg.menuText(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                int done = 0;
                List<String> skipped = new ArrayList<>();
                try (SftpUploader uploader = SftpUploader.connect(cfg, password)) {
                    for (Path file : files) {
                        if (indicator.isCanceled()) {
                            return;
                        }
                        indicator.setFraction((double) done / files.size());
                        indicator.setText2(file.getFileName().toString());

                        Path rel;
                        try {
                            rel = localRoot.relativize(file);
                        } catch (IllegalArgumentException e) {
                            skipped.add(file.toString());
                            continue;
                        }
                        if (rel.startsWith("..")) {
                            skipped.add(file.toString());
                            continue;
                        }
                        String remote = remoteRoot + "/" + rel.toString().replace('\\', '/');
                        uploader.upload(file, remote);
                        done++;
                    }
                    indicator.setFraction(1.0);

                    String msg = "已上传 " + done + " 个文件到 " + cfg.menuText()
                            + "\n" + cfg.getUsername() + "@" + cfg.getHost() + ":" + remoteRoot;
                    if (!skipped.isEmpty()) {
                        msg += "\n\n跳过 " + skipped.size() + " 个不在 " + cfg.getLocalRoot() + " 下的文件：\n"
                                + String.join("\n", skipped.subList(0, Math.min(5, skipped.size())));
                    }
                    showNotification(project, "上传完成", msg, skipped.isEmpty() ? NotificationType.INFORMATION : NotificationType.WARNING);
                } catch (Exception e) {
                    showNotification(project, "上传失败", describe(e), NotificationType.ERROR);
                }
            }
        }.queue();
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

    private static String trimTrailingSlash(String s) {
        String r = s.trim();
        while (r.endsWith("/") && r.length() > 1) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }
}
