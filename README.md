# Littletiles Model Share

在**游戏内**登录 inception.work、浏览并下载 LittleTiles 模型的客户端模组（Forge 1.12.2 / Java 8）。

当前状态：**开发环境已跑通 + 一个能加载的空模组骨架**（能编译、能进游戏、有日志）。功能还没写：

1. 站内登录（拿 token，存本地配置）
2. 模型列表浏览（列表 + 缩略图 + 搜索/标签）
3. 下载模型文件到本地目录，并导入成 LittleTiles 结构
4. （后续）上传本地结构到站点

---

## 工具链（为什么是这套）

| 项 | 值 | 备注 |
|---|---|---|
| 目标运行时 | **Forge 1.12.2 + Java 8** | 玩家现有的客户端就是这个 |
| 构建 | **ForgeGradle 2.3-SNAPSHOT** + Gradle **4.10.3** | 1.12.2 的经典组合 |
| JDK（跑 Gradle） | **JDK 8**（`C:\Users\gaksy\.jdks\temurin-8`，Temurin 1.8.0_504） | 机器原来只有 JRE 1.8，已补装 JDK |
| Forge 版本 | **1.12.2-14.23.5.2847** | **2854+ 不再发布 FG2.3 需要的 userdev.jar**，所以钉在 2847；产出的 mod 在 2847+ 的客户端都能加载 |
| 映射 | `stable_39` | |
| 字节码 | **major 52（Java 8）** | 已核对 jar 内的 class 文件 |

> 早期试过你给的 **CleanroomGradle**（Cleanroom + JDK 25 + LWJGL3）：那套能跑，但产物是 Java 25 字节码（major 69），
> **原版 Forge 1.12.2 + Java 8 加载不了**，所以按你的选择改回 ForgeGradle 2.3。
> 另外 **ForgeGradle 3** 在这台机器上 `runClient` 会在 FML 注册网络通道时 NPE（原版 MDK 一样崩），故不用。

## 常用命令

```powershell
$env:JAVA_HOME = 'C:\Users\gaksy\.jdks\temurin-8'   # 也可以靠 gradle.properties 里的 org.gradle.java.home

.\gradlew.bat setupDecompWorkspace   # 首次：反编译 MC + 建 workspace（约 7 分钟）
.\gradlew.bat build                  # 产出 build/libs/LittletilesModelShare-<version>.jar（已 reobf，可直接进 mods）
.\gradlew.bat runClient              # 起开发客户端
```

实测耗时：`setupDecompWorkspace` 7m21s、`build` 1m07s、`runClient` 约 1 分钟进主菜单。

产物：`build/libs/LittletilesModelShare-0.1.0.jar`（`-sources.jar` 是源码包，不用发）。

## 目录

```
build.gradle            # FG2.3 构建脚本（Forge 版本 / 映射 / 代理 / mcmod.info 变量替换）
gradle.properties       # JDK 8 / 代理 / JVM 参数
src/main/java/work/inception/ltsmodelshare/LtsModelShare.java   # 模组主类（骨架）
src/main/resources/mcmod.info                                   # 模组元信息
run/                    # 开发客户端数据目录（生成的，别提交）
.ref/                   # 参考物：你给的 CleanroomGradle 包 + 早期那套 Scaffold（不参与构建）
```

## 环境坑（都已处理）

1. **Gradle 发行版**：官方 `services.gradle.org` 会重定向到 `downloads.gradle.org`，这里连不上 → wrapper 用**腾讯云镜像**（`gradle-4.10.3-bin.zip`）。
2. **Gradle 4.10.3 只认 Java 8~11** → `gradle.properties` 里 `org.gradle.java.home` 钉死 JDK 8。
3. **代理**：Gradle 用 `gradle.properties` 的 `systemProp.*`（7890）；**游戏 JVM 另给一份**（`build.gradle` 的 `clientJvmArgs`），否则客户端联网/资源会卡。
4. 中文日志在 PowerShell 控制台显示成 `?????`（编码问题，不影响游戏），要好看可以在 `clientJvmArgs` 里加 `-Dfile.encoding=UTF-8`。

## 下一步（待定）

1. 加 **LittleTiles** 依赖（`provided 'curse.maven:littletiles-…'` 或丢 jar 进 `libs/`），摸清它的结构 API（`.ltmodel` / LTStructure 导入导出）。
2. 站内接口：`/api/work/public/*`（列表/详情/下载）。登录用 `/api/account/login/password-login`。
3. GUI：一个 `GuiScreen` 列表 + 搜索 + 下载按钮，下载到 `.minecraft/ltsmodels/`。
4. 下载后"导入成 LittleTiles 结构"（放置/粘贴到世界）。

---

## 模组现在有什么（v0.1.0）

| 功能 | 说明 |
|---|---|
| 打开面板 | 游戏内按 **M**，或聊天栏 `/ltsmodels`（只在有世界的客户端里响应） |
| 登录 | 面板里输站点账号密码 → `POST /api/account/login/password-login`，令牌存本地配置（**不存密码**） |
| 浏览 | `POST /api/public/work/cards`，搜索框 + 分页（8 条/页），游客也能看 |
| 详情 | `POST /api/public/work/detail?workId=`，列出这条作品里的模型（MC 版本 / box 数 / grid） |
| 下载 | `POST /api/work/download-snbt?itemId=&version=1.12` 拿地址 → GET 落盘到 `ltsmodels/`；1.19 的模型由**服务端现场转成 1.12** |

配置文件：`run/config/ltsmodelshare.json`（发布后是 `.minecraft/config/ltsmodelshare.json`）

```json
{
  "baseUrl": "https://www.inception.work",   // 换测试服就改这里
  "token": "",                                // 登录后自动写入
  "refreshToken": "",
  "username": "",                             // 只回显，不存密码
  "downloadDir": ""                           // 空 = .minecraft/ltsmodels
}
```

下载目录：`.minecraft/ltsmodels/`（界面上会显示完整路径）。

### 接口契约（已用真实站点验证过一轮）

| 步骤 | 请求 | 返回关键字段 |
|---|---|---|
| 登录 | `POST /api/account/login/password-login` `{username,password}` | `payload.token` |
| 列表 | `POST /api/public/work/cards` `{q,sort,locale,current,size}` | `payload.records[].{kind,workId,title,author,itemId}` / `payload.total` |
| 详情 | `POST /api/public/work/detail?workId=N` | `payload.items[].{itemId,title,mcVersion,grid,boxCount,preview.sizeBytes}` |
| 下载 | `POST /api/work/download-snbt?itemId=N&version=1.12`（Bearer） | `payload.{assetUrl,fileName,sizeBytes,converted}` |
| 取文件 | `GET <assetUrl>` | SNBT 文本（`version=1.12` 时服务端会把 1.19 的结构转过来，`converted=true`） |

### 还没做

1. **导入 LittleTiles**：现在只是把 SNBT 存到本地，还没解析成 LTStructure 贴到世界里（要加 LittleTiles 依赖 + 摸它的结构 API）。
2. **上传**（按你说的后续再开发）。
3. 密码框是明文输入（只是画面上打了 ●），要更安全可以改成"网页登录拿一次性 code，游戏内粘贴"。
4. 令牌过期没做自动续期（`refreshToken` 已经存下来了，续期接口 `/api/account/auth/refresh` 还没接）。