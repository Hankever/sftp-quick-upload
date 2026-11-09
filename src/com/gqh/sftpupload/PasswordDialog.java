package com.gqh.sftpupload;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * 首次上传（或密码没保存）时向用户索要密码。
 */
public class PasswordDialog extends DialogWrapper {

    private final ServerConfig cfg;
    private final JBPasswordField passwordField = new JBPasswordField();
    private final JBCheckBox rememberBox = new JBCheckBox("记住密码（保存到系统钥匙串）", true);

    public PasswordDialog(@Nullable Project project, ServerConfig cfg) {
        super(project, true);
        this.cfg = cfg;
        setTitle("上传到 " + cfg.getName() + " · 需要密码");
        setOKButtonText("上传");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        passwordField.setColumns(22);
        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("<html>服务器：<b>" + cfg.getUsername() + "@" + cfg.getHost()
                        + ":" + cfg.getPort() + "</b></html>"))
                .addComponent(new JBLabel("<html>目标目录：<b>" + cfg.getMappings().size() + " 个路径映射</b>"
                        + pre(cfg.mappingsSummary()) + "</html>"))
                .addVerticalGap(8)
                .addLabeledComponent("密码：", passwordField)
                .addComponent(rememberBox)
                .getPanel();
        passwordField.requestFocusInWindow();
        return panel;
    }

    private static String pre(String text) {
        return text.isEmpty() ? "" : "<pre>" + text + "</pre>";
    }

    public @NotNull String getPassword() {
        return new String(passwordField.getPassword());
    }

    public boolean isRemember() {
        return rememberBox.isSelected();
    }

    @Override
    protected void doOKAction() {
        if (getPassword().isEmpty()) {
            setErrorText("密码不能为空");
            return;
        }
        super.doOKAction();
    }
}
