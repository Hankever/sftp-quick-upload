package com.gqh.sftpupload;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;

/**
 * 一次连接内的 SFTP 上传会话。用法：connect(...) 之后反复 upload(...)，最后 close()。
 */
public final class SftpUploader implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final SshClient client;
    private final ClientSession session;
    private final SftpClient sftp;

    private SftpUploader(SshClient client, ClientSession session, SftpClient sftp) {
        this.client = client;
        this.session = session;
        this.sftp = sftp;
    }

    public static SftpUploader connect(ServerConfig cfg, String password) throws IOException {
        if (cfg.getHost().isEmpty()) {
            throw new IOException("服务器地址为空");
        }
        if (cfg.getUsername().isEmpty()) {
            throw new IOException("用户名为空");
        }
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.start();
        ClientSession session = null;
        try {
            session = client.connect(cfg.getUsername(), cfg.getHost(), cfg.getPort(), null, null)
                    .verify(TIMEOUT)
                    .getSession();
            session.addPasswordIdentity(password);
            session.auth().verify(TIMEOUT);
            SftpClient sftp = SftpClientFactory.instance().createSftpClient(session);
            return new SftpUploader(client, session, sftp);
        } catch (IOException e) {
            closeQuietly(session);
            closeQuietly(client);
            throw e;
        }
    }

    /** 只用于“测试连接”，连上立刻断开；逐条检查所有映射的远端目录是否可访问。 */
    public static void test(ServerConfig cfg, String password) throws IOException {
        try (SftpUploader u = connect(cfg, password)) {
            for (PathMapping mapping : cfg.getMappings()) {
                try {
                    u.sftp.stat(u.normalizeRemote(mapping.getRemoteRoot()));
                } catch (IOException e) {
                    throw new IOException("映射「" + mapping.getName() + "」的远端目录不存在或不可访问："
                            + mapping.getRemoteRoot() + "\n" + e.getMessage(), e);
                }
            }
        }
    }

    public void upload(Path localFile, String remotePath) throws IOException {
        String normalized = normalizeRemote(remotePath);
        int idx = normalized.lastIndexOf('/');
        if (idx > 0) {
            ensureDirectory(normalized.substring(0, idx));
        }
        try (InputStream in = Files.newInputStream(localFile);
             OutputStream out = sftp.write(normalized, 32 * 1024,
                     EnumSet.of(SftpClient.OpenMode.Write,
                             SftpClient.OpenMode.Create,
                             SftpClient.OpenMode.Truncate))) {
            in.transferTo(out);
        }
    }

    private String normalizeRemote(String path) {
        String p = path.replace('\\', '/');
        while (p.contains("//")) {
            p = p.replace("//", "/");
        }
        return p;
    }

    private void ensureDirectory(String dir) {
        if (dir.isEmpty() || "/".equals(dir)) {
            return;
        }
        StringBuilder cur = new StringBuilder();
        for (String part : dir.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            cur.append('/').append(part);
            String p = cur.toString();
            try {
                sftp.stat(p);
            } catch (IOException notExists) {
                try {
                    sftp.mkdir(p);
                } catch (IOException ignore) {
                    // 并发创建或权限不足都在这里吞掉，后续写入会给出真正的错误
                }
            }
        }
    }

    @Override
    public void close() {
        closeQuietly(sftp);
        closeQuietly(session);
        closeQuietly(client);
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }
}
