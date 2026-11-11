package com.gqh.sftpupload;

import com.intellij.util.xmlb.annotations.Tag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 上传成功后调用项目接口的配置（典型场景：上传 XML 后清服务器端的 SQL 配置缓存）。
 * <p>
 * 规则语法：每行一条，{@code *} 不跨目录、{@code **} 跨目录、{@code ?} 匹配单个字符；
 * 不含 {@code /} 的规则只匹配文件名，含 {@code /} 的规则匹配相对项目根的整条路径；
 * 规则留空表示任何文件都命中。以 {@code #} 开头的行是注释。
 */
public class PostUploadConfig {

    private boolean enabled = false;
    private String url = "";
    private String method = "GET";

    /**
     * 多行文本必须用 @Tag 存成子元素。如果按属性存，XML 会把换行规范成空格，
     * 规则和 JSON 请求体都会被压成一行。
     */
    private String headers = "";
    private String body = "";
    private String rules = "";

    /** 调用前先弹窗询问；取消勾选则上传成功后直接调用。 */
    private boolean askBeforeCall = true;

    /** 编译过的 glob 缓存，只用于运行时，不参与持久化。 */
    private final Map<String, Pattern> globCache = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return "POST".equalsIgnoreCase(method) ? "POST" : "GET";
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @Tag("headers")
    public String getHeaders() {
        return headers == null ? "" : headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    @Tag("body")
    public String getBody() {
        return body == null ? "" : body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Tag("rules")
    public String getRules() {
        return rules == null ? "" : rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }

    public boolean isAskBeforeCall() {
        return askBeforeCall;
    }

    public void setAskBeforeCall(boolean askBeforeCall) {
        this.askBeforeCall = askBeforeCall;
    }

    /** 拆成非空、非注释的规则行。 */
    public List<String> patterns() {
        List<String> result = new ArrayList<>();
        for (String line : getRules().split("\r?\n")) {
            String p = line.trim().replace('\\', '/');
            if (p.isEmpty() || p.startsWith("#")) {
                continue;
            }
            result.add(p);
        }
        return result;
    }

    /** 这个已上传的文件是否命中调用规则。 */
    public boolean matches(Path projectBase, Path file) {
        List<String> patterns = patterns();
        if (patterns.isEmpty()) {
            return true;
        }
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        String relative = relativePath(projectBase, file);
        for (String pattern : patterns) {
            String target = pattern.indexOf('/') >= 0 ? relative : name;
            if (glob(pattern).matcher(target).matches()) {
                return true;
            }
        }
        return false;
    }

    public static String relativePath(Path projectBase, Path file) {
        try {
            return projectBase.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            // 文件不在项目根下面（理论上不会出现），退回文件名
            return file.getFileName() == null ? file.toString() : file.getFileName().toString();
        }
    }

    private Pattern glob(String glob) {
        Pattern cached = globCache.get(glob);
        if (cached != null) {
            return cached;
        }
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    i++;
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                        i++;
                        regex.append("(?:.*/)?");
                    } else {
                        regex.append(".*");
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        Pattern pattern = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        globCache.put(glob, pattern);
        return pattern;
    }

    public PostUploadConfig copy() {
        PostUploadConfig c = new PostUploadConfig();
        c.enabled = enabled;
        c.url = url;
        c.method = method;
        c.headers = headers;
        c.body = body;
        c.rules = rules;
        c.askBeforeCall = askBeforeCall;
        return c;
    }
}
