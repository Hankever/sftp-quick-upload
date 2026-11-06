package com.gqh.sftpupload;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.Nullable;

/**
 * 密码存在系统钥匙串（macOS Keychain）里，不写进任何配置文件。
 * <p>
 * key 用「用户名@主机:端口」而不是配置 id：配置复制到别的项目、或者删掉重建同一条服务器，
 * 密码都还认得出，不会因为换了 id 就丢密码、或者把旧密码套到新主机上。
 */
final class PasswordUtil {

    private static final String SERVICE_PREFIX = "SftpQuickUpload:";

    private PasswordUtil() {
    }

    private static String accountKey(ServerConfig cfg) {
        return cfg.getUsername() + "@" + cfg.getHost() + ":" + cfg.getPort();
    }

    private static CredentialAttributes attributes(ServerConfig cfg) {
        return new CredentialAttributes(SERVICE_PREFIX + accountKey(cfg),
                cfg.getHost() + " / " + cfg.getUsername());
    }

    /** 1.0.x 用的是配置 id 作 key，读到之后顺手迁到新 key 上。 */
    private static CredentialAttributes legacyAttributes(ServerConfig cfg) {
        return new CredentialAttributes(SERVICE_PREFIX + cfg.getId(),
                cfg.getHost() + " / " + cfg.getUsername());
    }

    static @Nullable String get(ServerConfig cfg) {
        String password = read(attributes(cfg));
        if (password != null) {
            return password;
        }
        String legacy = read(legacyAttributes(cfg));
        if (legacy != null) {
            store(cfg, legacy);
            PasswordSafe.getInstance().set(legacyAttributes(cfg), null);
        }
        return legacy;
    }

    static void store(ServerConfig cfg, String password) {
        PasswordSafe.getInstance().set(attributes(cfg), new Credentials(cfg.getUsername(), password));
    }

    static void clear(ServerConfig cfg) {
        PasswordSafe.getInstance().set(attributes(cfg), null);
        PasswordSafe.getInstance().set(legacyAttributes(cfg), null);
    }

    private static @Nullable String read(CredentialAttributes attributes) {
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        if (credentials == null) {
            return null;
        }
        String password = credentials.getPasswordAsString();
        return password == null || password.isEmpty() ? null : password;
    }
}
