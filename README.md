# Quick SFTP Upload

IntelliJ IDEA 插件：右键把选中的文件或目录上传到预先配置好的服务器，纯手动、不自动同步，支持配置多台（测试 / 生产）。

**服务器配置按项目隔离**：每个项目一份，存在项目的 `.idea/sftpQuickUpload.xml`，A 项目的配置不会出现在 B 项目的菜单里。

**一台服务器可以有多条路径映射**：网页目录传 Web 根目录、resources 目录传 `WEB-INF/classes`，多选混着两类文件也能一次传完，按路径自动分发。

## 菜单结构

```
项目树右键 → 上传到服务器
              ├── 设置服务器信息…                ← 配置当前项目的服务器和路径映射
              ├── ───────────────
              ├── 测试   (1.2.3.4:22)      ← 每台已配置的服务器一项
              └── 【生产】prod   (5.6.7.8:22)  网页 3 · 资源 2
```

选中内容会分到两条以上映射时，菜单项后面会提示分发结果（`网页 3 · 资源 2`），单一映射时不显示。编辑器内右键也有同一个入口，作用于当前文件。

## 功能

- **配置按项目隔离**：测试 / 生产各自一条，不同项目互不影响，不会出现"在 A 项目选到 B 项目服务器"的情况
- **多路径映射**：一台服务器挂多条「本地目录 → 远端目录」，按选中文件所在目录自动分发；嵌套目录按最长前缀匹配，不会重复上传
- **生产环境二次确认**：勾选「生产环境」的服务器，上传前弹窗列出会传到哪几个远端目录，默认按钮是「取消」
- **菜单直显主机**：菜单项后面带 `host:port`，点之前就能确认目标；生产服务器带【生产】前缀
- **保留目录结构**：`src/main/webapp/pages/a.html` → `<网页映射的远端目录>/pages/a.html`
- **多选与目录**：可以一次选多个文件，或直接选一个目录递归上传；选中映射目录的上级（如 `src/main`）也能正确分发
- **自动建目录**：远端父目录不存在时逐级创建
- **密码存系统钥匙串**：macOS Keychain，不写进任何明文配置文件
- **零自动同步**：不点菜单就不会有任何上传行为

## 安装

1. `Settings → Plugins → ⚙️ → Install Plugin from Disk…`
2. 选择 `build/sftp-quick-upload-1.2.1.zip`
3. 重启 IDE

## 配置

在某个项目中右键 → 上传到服务器 → 设置服务器信息…

| 字段 | 说明 |
|---|---|
| 名称 | 菜单里显示的名字，如 `prod` |
| 主机 | IP 或域名 |
| 端口 / 用户 | SSH 端口（默认 22）与登录用户 |
| 生产环境 | 勾上后菜单加【生产】前缀，上传前二次确认 |

每台服务器还要配一条或多条**路径映射**：

| 映射字段 | 说明 |
|---|---|
| 备注 | 如 `网页`、`资源`，菜单和通知里用它区分 |
| 本地目录 | **相对项目根**的路径，如 `src/main/webapp`、`src/main/resources` |
| 远端目录 | 服务器上的绝对路径，如 `/var/www/app` |

一个典型的 Java Web 项目：

| 备注 | 本地目录 | 远端目录 |
|---|---|---|
| 网页 | src/main/webapp | /var/www/app |
| 资源 | src/main/resources | /var/www/app/WEB-INF/classes |

上传时按「远端目录 + (文件路径 − 本地目录)」算目标，所以本地子结构原样保留。填好后点 **测试连接** 验证（会逐条检查所有远端目录，会弹密码框），确认无误再保存。

### 多项目

配置只属于当前项目。换项目要各自配一遍，两种省事的方式：

- 在配置窗口里选中一台服务器点 **复制**，改成另一套测试/生产
- 右键菜单里的 **导入旧版全局配置…**（仅当旧配置还存在时出现）

配置存在项目的 `.idea/sftpQuickUpload.xml`（不含密码）。如果不想让服务器地址进版本库，把这一行加进 `.gitignore`。

### 升级

- **从 1.1.0**：旧的一条 `本地目录 → 远端目录` 会自动变成一条名为「网页」的映射，不用重配。
- **从 1.0.x**：那个版本的配置是所有项目共用的，升级后不会静默迁移（避免搬错项目）：
  - 配置窗口顶部会提示「检测到旧版全局配置（N 台）」，点 **导入到本项目** 拿到当前项目
  - 其他项目同样可以再导入一次；都配好后点 **清除旧配置** 删掉旧的全局文件
- 密码不用重输：按 `用户名@主机:端口` 存钥匙串，旧版按配置 id 存的密码会被自动迁移。

## 构建

需要本机装有 IntelliJ IDEA（用它的 JBR 21 做编译），无需 Gradle、无需联网下载依赖。

```bash
bash build.sh
# 产物：build/sftp-quick-upload-<plugin.xml 里的版本号>.zip
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
    ServerConfig.java              一台服务器的数据模型（含多条路径映射）
    PathMapping.java               一条路径映射：本地目录 → 远端目录
    ServersState.java              项目级持久化（.idea/sftpQuickUpload.xml）
    LegacyConfig.java              1.0.x 的应用级配置，只作迁移来源
    UploadGroup.java               右键动态子菜单
    UploadOneAction.java           单台服务器的上传动作（含生产环境确认）
    UploadPlan.java                按映射分组、遍历文件、算出目标路径
    UploadRunner.java              后台任务、SFTP 上传、结果通知
    SettingsAction.java            打开配置窗口
    SettingsDialog.java            配置窗口 UI（服务器 + 路径映射）
    PasswordDialog.java            首次上传时索要密码
    PasswordUtil.java              密码读写（PasswordSafe / 钥匙串）
    SftpUploader.java              SFTP 连接与上传（MINA SSHD）
lib/                               第三方 jar
build.sh                           编译打包脚本
```

## 兼容性

- 编译目标：Java 21（IDEA 2024.1+ 运行时）
- `since-build 233`，`until-build 253.*`
- 与 IDE 版本强绑定的是 `plugin.xml` 里的 `idea-version`，升级 IDEA 后如提示不兼容，改这一行重新打包即可

## 说明

插件不依赖 IDEA 内置的 Deployment 模块，用的是自带的 SSHD 客户端，因此不受 Community / Ultimate 版本限制影响（只要平台 API 可用）。服务器主机密钥采用 accept-all 策略，仅适用于内部可信网络。
