package com.gqh.sftpupload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * 调用「上传后接口」。用 HttpURLConnection 实现，避免依赖 JDK 11 里没有的 java.net.http。
 */
final class PostUploadCaller {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    /** 通知里只放响应片段，避免接口返回一大段 HTML 把弹窗撑爆。 */
    private static final int MAX_RESPONSE_BYTES = 600;

    private PostUploadCaller() {
    }

    /** 调用成功返回响应片段（可能为空）；非 2xx 或连接失败抛 IOException，消息可直接展示给用户。 */
    static String call(PostUploadConfig cfg) throws IOException {
        String address = cfg.getUrl().trim();
        if (address.isEmpty()) {
            throw new IOException("接口地址为空");
        }
        String method = cfg.getMethod();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(true);
            applyHeaders(conn, cfg.getHeaders());

            if ("POST".equals(method)) {
                conn.setDoOutput(true);
                if (conn.getRequestProperty("Content-Type") == null) {
                    conn.setRequestProperty("Content-Type",
                            "application/x-www-form-urlencoded; charset=UTF-8");
                }
                byte[] payload = cfg.getBody().getBytes(StandardCharsets.UTF_8);
                if (payload.length > 0) {
                    try (OutputStream out = conn.getOutputStream()) {
                        out.write(payload);
                    }
                }
            }

            int code = conn.getResponseCode();
            String response = readBody(conn, code);
            if (code < 200 || code >= 300) {
                throw new IOException("接口返回 HTTP " + code
                        + (response.isEmpty() ? "" : "：" + response));
            }
            return response;
        } catch (UnknownHostException e) {
            throw new IOException("找不到接口主机，检查地址：" + address, e);
        } catch (ConnectException e) {
            throw new IOException("连不上接口服务器，检查地址和端口：" + address, e);
        } catch (SocketTimeoutException e) {
            throw new IOException("调用接口超时（" + (READ_TIMEOUT_MS / 1000) + " 秒）：" + address, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 每行一个 {@code Name: value}。 */
    private static void applyHeaders(HttpURLConnection conn, String raw) {
        for (String line : raw.split("\r?\n")) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if (!name.isEmpty()) {
                conn.setRequestProperty(name, value);
            }
        }
    }

    private static String readBody(HttpURLConnection conn, int code) {
        InputStream in = null;
        try {
            in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) != -1) {
                int remain = MAX_RESPONSE_BYTES - buf.size();
                if (remain <= 0) {
                    break;
                }
                buf.write(tmp, 0, Math.min(n, remain));
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }
}
