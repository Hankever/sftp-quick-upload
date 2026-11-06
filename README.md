# Quick SFTP Upload

IntelliJ IDEA 插件：右键把选中的文件或目录上传到预先配置好的服务器，纯手动、不自动同步，支持配置多台（测试 / 生产）。

## 菜单结构

```
项目树右键 → 上传到服务器
              ├── 设置服务器信息…     ← 添加、修改、删除服务器
              ├── ───────────────
              ├── dev26   (1.2.3.4)   ← 每台已配置的服务器一项
              └── prod    (5.6.7.8)
```

编辑器内右键也有同一个入口，作用于当前文件。

## 功能

- **多服务器**：测试、生产各自一条，点哪条传哪条
- **保留目录结构**：`src/main/webapp/pages/a.html` → `<远端目录>/pages/a.html`
- **多选与目录**：可以一次选多个文件，或直接选一个目录递归上传
- **自动建目录**：远端父目录不存在时逐级创建
- **密码存系统钥匙串**：macOS Keychain，不写进任何明文配置文件
- **零自动同步**：不点菜单就不会有任何上传行为

## 安装

1. `Settings → Plugins → ⚙️ → Install Plugin from Disk…`
2. 选择 `build/sftp-quick-upload-1.0.0.zip`
3. 重启 IDE

## 配置

右键 → 上传到服务器 → 设置服务器信息…

| 字段 | 说明 |
|---|---|
| 名称 | 菜单里显示的名字，如 `prod` |
| 主机 | IP 或域名 |
| 端口 / 用户 | SSH 端口（默认 22）与登录用户 |
| 本地目录 | **相对项目根**的路径，如 `src/main/webapp` |
| 远端目录 | 服务器上的绝对路径，如 `/var/www/html` |

填好后点 **测试连接** 验证（会弹密码框），确认无误再保存。

选中项目树中的文件 → 右键 → 上传到服务器 → 点服务器名即可上传，完成后右下角有气泡提示。

## 构建

需要本机装有 IntelliJ IDEA（用它的 JBR 21 做编译），无需 Gradle、无需联网下载依赖。

```bash
bash build.sh
# 产物：build/sftp-quick-upload-1.0.0.zip
```

依赖 `lib/` 下的 Apache MINA SSHD（已随仓库提交）：

- `sshd-core-2.13.2.jar`
- `sshd-common-2.13.2.jar`
- `sshd-sftp-2.13.2.jar`

## 目录结构

```
src/
  META-INF/plugin.xml              插件声明：菜单注册、服务、通知组
  com/gqh/sftpupload/
    ServerConfig.java              单个服务器的数据模型
    ServersState.java              应用级持久化（sftpQuickUpload.xml）
    UploadGroup.java               右键动态子菜单
    UploadOneAction.java           单台服务器的上传动作
    SettingsAction.java            打开配置窗口
    SettingsDialog.java            配置窗口 UI
    PasswordDialog.java            首次上传时索要密码
    PasswordUtil.java              密码读写（PasswordSafe / 钥匙串）
    SftpUploader.java              SFTP 连接与上传（MINA SSHD）
    UploadRunner.java              路径映射、后台任务、结果通知
lib/                               第三方 jar
build.sh                           编译打包脚本
```

## 兼容性

- 编译目标：Java 21（IDEA 2024.1+ 运行时）
- `since-build 233`，`until-build 253.*`
- 与 IDE 版本强绑定的是 `plugin.xml` 里的 `idea-version`，升级 IDEA 后如提示不兼容，改这一行重新打包即可

## 说明

插件不依赖 IDEA 内置的 Deployment 模块，用的是自带的 SSHD 客户端，因此不受 Community / Ultimate 版本限制影响（只要平台 API 可用）。服务器主机密钥采用 accept-all 策略，仅适用于内部可信网络。
