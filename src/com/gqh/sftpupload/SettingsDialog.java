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

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务器管理窗口：左边列表，右边表单。配置只作用于当前项目。
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
    private final JCheckBox productionBox = new JCheckBox("生产环境（上传前二次确认）");

    private JBLabel legacyLabel;
    private JButton legacyImportButton;
    private JButton legacyClearButton;

    private int currentIndex = -1;
    private boolean loading;

    public SettingsDialog(@Nullable Project project) {
        super(project, true);
        this.project = project;
        String projectName = project == null ? "当前项目" : project.getName();
        setTitle("上传服务器配置 — " + projectName);
        setOKButtonText("保存");
        setCancelButtonText("取消");

        for (ServerConfig s : ServersState.serversOf(project)) {
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
            listModel.addElement(s.listText());
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
        scroll.setPreferredSize(new Dimension(340, 200));

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(new JBLabel("本项目的服务器"), BorderLayout.NORTH);
        left.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.add(buttonRow(newButton("新增", this::addServer),
                newButton("复制", this::duplicateServer),
                newButton("删除", this::removeServer)));
        buttons.add(buttonRow(newButton("测试连接", this::testConnection),
                newButton("清除密码", this::clearPassword)));
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
                .addComponent(productionBox)
                .addComponent(new JBLabel("<html><i>本地目录相对项目根，例如 src/main/webapp；"
                        + "远端目录填绝对路径，例如 /var/www/app</i></html>"))
                .addComponent(new JBLabel("<html><i>这些配置只对当前项目生效，保存在项目的 .idea 目录，"
                        + "不会和其他项目共用。</i></html>"))
                .getPanel();

        JPanel root = new JPanel(new BorderLayout(0, 10));
        JComponent banner = createLegacyBanner();
        if (banner != null) {
            root.add(banner, BorderLayout.NORTH);
        }
        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.add(left, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    /** 旧版是全局配置，这里给一个显式入口把它搬到当前项目，不做静默迁移免得搬错项目。 */
    private @Nullable JComponent createLegacyBanner() {
        if (LegacyConfig.isEmpty()) {
            return null;
        }
        legacyLabel = new JBLabel();
        legacyImportButton = newButton("导入到本项目", this::importLegacy);
        legacyClearButton = newButton("清除旧配置", this::clearLegacy);
        updateLegacyBanner();

        JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        banner.add(legacyLabel);
        banner.add(legacyImportButton);
        banner.add(legacyClearButton);
        return banner;
    }

    private void updateLegacyBanner() {
        if (legacyLabel == null) {
            return;
        }
        int count = LegacyConfig.size();
        legacyLabel.setText("<html>检测到旧版全局配置（" + count + " 台服务器，以前所有项目共用）</html>");
        boolean has = count > 0;
        if (legacyImportButton != null) {
            legacyImportButton.setEnabled(has);
        }
        if (legacyClearButton != null) {
            legacyClearButton.setEnabled(has);
        }
    }

    private void importLegacy() {
        flushForm();
        List<ServerConfig> pool = new ArrayList<>(servers);
        List<ServerConfig> toAdd = new ArrayList<>();
        for (ServerConfig legacyConfig : LegacyConfig.servers()) {
            boolean exists = pool.stream().anyMatch(c -> ServersState.sameTarget(c, legacyConfig));
            if (!exists) {
                ServerConfig copy = legacyConfig.copy();
                toAdd.add(copy);
                pool.add(copy);
            }
        }
        if (toAdd.isEmpty()) {
            Messages.showInfoMessage(project, "旧版配置里的服务器本项目都已经有了", "无需导入");
            return;
        }
        servers.addAll(toAdd);
        currentIndex = -1;
        rebuildList();
        list.setSelectedIndex(servers.size() - toAdd.size());
        if (legacyLabel != null) {
            legacyLabel.setText("<html>已导入 " + toAdd.size() + " 台服务器，<b>记得点「保存」生效</b>。"
                    + "旧配置仍保留，其他项目也可以导入。</html>");
        }
    }

    private void clearLegacy() {
        int answer = Messages.showYesNoDialog(project,
                "清除后，其他项目就再也无法从旧版全局配置导入了。\n确定所有项目都配置好了吗？",
                "清除旧版全局配置", Messages.getWarningIcon());
        if (answer != Messages.YES) {
            return;
        }
        LegacyConfig.clear();
        if (legacyLabel != null) {
            legacyLabel.setText("<html>旧版全局配置已清除</html>");
            updateButtonsEnabled();
        }
    }

    private void updateButtonsEnabled() {
        boolean has = !LegacyConfig.isEmpty();
        if (legacyImportButton != null) {
            legacyImportButton.setEnabled(has);
        }
        if (legacyClearButton != null) {
            legacyClearButton.setEnabled(has);
        }
    }

    private static JButton newButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JPanel buttonRow(JButton... items) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (JButton b : items) {
            row.add(b);
        }
        return row;
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

    private void duplicateServer() {
        flushForm();
        if (currentIndex < 0 || currentIndex >= servers.size()) {
            return;
        }
        int idx = currentIndex;
        servers.add(idx + 1, servers.get(idx).duplicate());
        // 重建列表会先触发一次「取消选中」，把 currentIndex 清掉，所以先记下目标位置
        currentIndex = -1;
        rebuildList();
        list.setSelectedIndex(idx + 1);
    }

    private void removeServer() {
        int idx = list.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        int answer = Messages.showYesNoDialog(project,
                "确定删除服务器「" + servers.get(idx).getName() + "」吗？\n（如果它只是本项目的临时配置，删除不影响其他项目）",
                "删除服务器", Messages.getQuestionIcon());
        if (answer != Messages.YES) {
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
        if (pwd == null) {
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
            productionBox.setSelected(s.isProduction());
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
            productionBox.setSelected(false);
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
        s.setProduction(productionBox.isSelected());
        if (currentIndex < listModel.size()) {
            listModel.set(currentIndex, s.listText());
        }
    }

    @Override
    protected void doOKAction() {
        flushForm();
        for (ServerConfig s : servers) {
            if (s.getName().isEmpty()) {
                setErrorText("服务器名称不能为空");
                return;
            }
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
        ServersState service = ServersState.getInstance(project);
        if (service == null) {
            return;
        }
        List<ServerConfig> target = service.getServers();
        target.clear();
        for (ServerConfig s : servers) {
            target.add(s.copy());
        }
    }
}
