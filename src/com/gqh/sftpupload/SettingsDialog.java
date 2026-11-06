package com.gqh.sftpupload;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务器管理窗口：左边列表，右边表单。
 */
public class SettingsDialog extends DialogWrapper {

    private final Project project;
    private final List<ServerConfig> servers = new ArrayList<>();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JBList<>(listModel);

    private final JBTextField nameField = new JBTextField();
    private final JBTextField hostField = new JBTextField();
    private final JBTextField portField = new JBTextField();
    private final JBTextField userField = new JBTextField();
    private final JBTextField localRootField = new JBTextField();
    private final JBTextField remoteRootField = new JBTextField();

    private int currentIndex = -1;
    private boolean loading;

    public SettingsDialog(@Nullable Project project) {
        super(project, true);
        this.project = project;
        setTitle("上传服务器配置");
        setOKButtonText("保存");
        setCancelButtonText("取消");

        for (ServerConfig s : ServersState.getInstance().getServers()) {
            servers.add(s.copy());
        }
        rebuildList();
        init();
        if (!servers.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void rebuildList() {
        listModel.clear();
        for (ServerConfig s : servers) {
            listModel.addElement(s.getName() + "   (" + s.getHost() + ")");
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (loading || e.getValueIsAdjusting()) {
                return;
            }
            int idx = list.getSelectedIndex();
            if (idx == currentIndex) {
                return;
            }
            flushForm();
            currentIndex = idx;
            loadForm();
        });

        JBScrollPane scroll = new JBScrollPane(list);
        scroll.setPreferredSize(new Dimension(200, 240));

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(new JBLabel("已配置的服务器"), BorderLayout.NORTH);
        left.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton add = new JButton("新增");
        add.addActionListener(e -> addServer());
        JButton remove = new JButton("删除");
        remove.addActionListener(e -> removeServer());
        JButton test = new JButton("测试连接");
        test.addActionListener(e -> testConnection());
        JButton clearPwd = new JButton("清除密码");
        clearPwd.addActionListener(e -> clearPassword());
        buttons.add(add);
        buttons.add(remove);
        buttons.add(test);
        buttons.add(clearPwd);
        left.add(buttons, BorderLayout.SOUTH);

        portField.setColumns(6);
        JPanel portRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        portRow.add(portField);
        portRow.add(new JBLabel("   用户名："));
        userField.setColumns(14);
        portRow.add(userField);

        JPanel right = FormBuilder.createFormBuilder()
                .addLabeledComponent("名称：", nameField)
                .addLabeledComponent("主机：", hostField)
                .addLabeledComponent("端口 / 用户：", portRow)
                .addLabeledComponent("本地目录：", localRootField)
                .addLabeledComponent("远端目录：", remoteRootField)
                .addComponent(new JBLabel("<html><i>本地目录相对项目根，例如 src/main/webapp；"
                        + "远端目录填绝对路径，例如 /var/www/app</i></html>"))
                .getPanel();

        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.add(left, BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        return root;
    }

    private void addServer() {
        flushForm();
        ServerConfig s = new ServerConfig();
        s.setName("服务器" + (servers.size() + 1));
        servers.add(s);
        rebuildList();
        list.setSelectedIndex(servers.size() - 1);
        nameField.requestFocusInWindow();
    }

    private void removeServer() {
        int idx = list.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        ServerConfig removed = servers.remove(idx);
        PasswordUtil.clear(removed);
        currentIndex = -1;
        rebuildList();
        if (!servers.isEmpty()) {
            list.setSelectedIndex(Math.min(idx, servers.size() - 1));
        } else {
            clearForm();
        }
    }

    private void clearPassword() {
        flushForm();
        if (currentIndex < 0) {
            return;
        }
        PasswordUtil.clear(servers.get(currentIndex));
        Messages.showInfoMessage(project, "已清除「" + servers.get(currentIndex).getName() + "」保存的密码", "提示");
    }

    private void testConnection() {
        flushForm();
        if (currentIndex < 0) {
            return;
        }
        ServerConfig cfg = servers.get(currentIndex);
        if (cfg.getHost().isEmpty() || cfg.getUsername().isEmpty()) {
            Messages.showWarningDialog(project, "主机和用户名不能为空", "配置不完整");
            return;
        }
        String pwd = PasswordUtil.get(cfg);
        if (pwd == null || pwd.isEmpty()) {
            PasswordDialog dialog = new PasswordDialog(project, cfg);
            if (!dialog.showAndGet()) {
                return;
            }
            pwd = dialog.getPassword();
            if (dialog.isRemember()) {
                PasswordUtil.store(cfg, pwd);
            }
        }
        final String password = pwd;
        final Exception[] error = new Exception[1];
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            try {
                SftpUploader.test(cfg, password);
            } catch (Exception ex) {
                error[0] = ex;
            }
        }, "正在连接 " + cfg.getHost() + "…", true, project);

        if (error[0] == null) {
            Messages.showInfoMessage(project,
                    "连接成功，目标目录可访问：\n" + cfg.getRemoteRoot(), "测试连接");
        } else {
            Messages.showErrorDialog(project, UploadRunner.describe(error[0]), "连接失败");
        }
    }

    private void loadForm() {
        loading = true;
        try {
            if (currentIndex < 0 || currentIndex >= servers.size()) {
                clearForm();
                return;
            }
            ServerConfig s = servers.get(currentIndex);
            nameField.setText(s.getName());
            hostField.setText(s.getHost());
            portField.setText(String.valueOf(s.getPort()));
            userField.setText(s.getUsername());
            localRootField.setText(s.getLocalRoot());
            remoteRootField.setText(s.getRemoteRoot());
        } finally {
            loading = false;
        }
    }

    private void clearForm() {
        loading = true;
        try {
            nameField.setText("");
            hostField.setText("");
            portField.setText("22");
            userField.setText("");
            localRootField.setText("src/main/webapp");
            remoteRootField.setText("");
        } finally {
            loading = false;
        }
    }

    private void flushForm() {
        if (currentIndex < 0 || currentIndex >= servers.size()) {
            return;
        }
        ServerConfig s = servers.get(currentIndex);
        s.setName(nameField.getText().trim());
        s.setHost(hostField.getText().trim());
        try {
            s.setPort(Integer.parseInt(portField.getText().trim()));
        } catch (NumberFormatException ignore) {
            s.setPort(22);
        }
        s.setUsername(userField.getText().trim());
        s.setLocalRoot(localRootField.getText().trim());
        s.setRemoteRoot(remoteRootField.getText().trim());
        if (currentIndex < listModel.size()) {
            listModel.set(currentIndex, s.getName() + "   (" + s.getHost() + ")");
        }
    }

    @Override
    protected void doOKAction() {
        flushForm();
        for (ServerConfig s : servers) {
            if (s.getHost().isEmpty() || s.getUsername().isEmpty() || s.getRemoteRoot().isEmpty()) {
                setErrorText("「" + s.getName() + "」的主机、用户名、远端目录都不能为空");
                return;
            }
            if (s.getPort() <= 0 || s.getPort() > 65535) {
                setErrorText("「" + s.getName() + "」的端口不合法");
                return;
            }
        }
        super.doOKAction();
    }

    public void save() {
        List<ServerConfig> target = ServersState.getInstance().getServers();
        target.clear();
        for (ServerConfig s : servers) {
            target.add(s.copy());
        }
    }
}
