package com.gqh.sftpupload;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.0.x 的应用级配置：<IDE 配置目录>/options/sftpQuickUpload.xml，所有项目共用一份。
 * <p>
 * 1.1.0 起配置改为按项目保存，这个文件只作为迁移来源被读取，插件不再往里写。
 * 直接在文件层面读取（而不是注册成 state 组件），是为了不和应用级/项目级两个同名组件纠缠。
 */
final class LegacyConfig {

    private static final Logger LOG = Logger.getInstance(LegacyConfig.class);
    private static final String FILE_NAME = "sftpQuickUpload.xml";

    private static List<ServerConfig> cache;

    private LegacyConfig() {
    }

    static synchronized List<ServerConfig> servers() {
        if (cache == null) {
            cache = readFile();
        }
        return new ArrayList<>(cache);
    }

    static boolean isEmpty() {
        return servers().isEmpty();
    }

    static int size() {
        return servers().size();
    }

    /** 用户确认后删除旧版全局配置，避免以后误导入。 */
    static void clear() {
        cache = new ArrayList<>();
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            LOG.warn("删除旧版全局配置失败：" + file(), e);
        }
    }

    private static Path file() {
        return Paths.get(PathManager.getOptionsPath(), FILE_NAME);
    }

    private static List<ServerConfig> readFile() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        try (InputStream in = Files.newInputStream(path)) {
            Element root = JDOMUtil.load(in);
            Element component = root.getChild("component");
            if (component == null) {
                return new ArrayList<>();
            }
            ServersState.State state = XmlSerializer.deserialize(component, ServersState.State.class);
            return state.servers == null ? new ArrayList<>() : new ArrayList<>(state.servers);
        } catch (Exception e) {
            LOG.warn("读取旧版全局配置失败，已忽略：" + path, e);
            return new ArrayList<>();
        }
    }
}
