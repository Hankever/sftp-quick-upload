package com.gqh.sftpupload;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.Nullable;

/**
 * 密码存在系统钥匙串（macOS Keychain）里，不写进任何配置文件。
 */
final class PasswordUtil {

    private PasswordUtil() {
    }

    private static CredentialAttributes attributes(ServerConfig cfg) {
        return new CredentialAttributes("SftpQuickUpload:" + cfg.getId(), cfg.getHost() + " / " + cfg.getUsername());
    }

    static @Nullable String get(ServerConfig cfg) {
        Credentials credentials = PasswordSafe.getInstance().get(attributes(cfg));
        if (credentials == null) {
            return null;
        }
        return credentials.getPasswordAsString();
    }

    static void store(ServerConfig cfg, String password) {
        PasswordSafe.getInstance().set(attributes(cfg), new Credentials(cfg.getUsername(), password));
    }

    static void clear(ServerConfig cfg) {
        PasswordSafe.getInstance().set(attributes(cfg), null);
    }
}
