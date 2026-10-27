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


## 模组现在有什么

| 功能 | 说明 |
|---|---|
| 打开面板 | 游戏内按 **I**（和 LittleTiles 的 M 冲突，所以不用 M），或 `/ltsmodels`；主菜单也能开 |
| 登录 | 账号密码登录，或点「使用访问令牌」粘贴站点 PAT（长期有效、可吊销）；令牌存本地，**不存密码** |
| 保持登录 | 打开面板时静默续期（站点是 2 小时滑动窗口），请求失败自动续期重试一次 |
| 浏览 | 卡片流：封面（等比裁剪）+ 标题 + 作者头像 + 下载/浏览数；3 列 × 最多 3 行，按窗口自适应 |
| 高级搜索 | 搜索框旁「高级搜索」：**左侧作者候选**（带头像、可滚动）、**右侧标签**（可多选、可滚动）、最新/最热排序 |
| 详情 | 左封面 4:3 + 多图缩略图；右栏标题/标签/作者/浏览·下载·收藏·评论/合集依赖；描述窗口多图 7 行、单图 4 行 |
| 获取蓝图 | 服务端直接把结构生成 **Little Blueprint 放进背包**（满了掉脚下），不写文件；grid 超出客户端上限时弹窗告诉你怎么改 `config/littletiles.json` |
| 上传 | 手持蓝图 → 填标题/多行简介/选标签/选截图（游戏 `screenshots/` 目录，一行三张、绿框选中、懒加载）→ **提交草稿** 或 **提交审核**；过程有阻塞式进度弹窗 |
| 上传审核 | 提交审核会自动 `consent/agree(upload_rights)` 拿新的 consentId 再 `submit-review`，失败会保留已存的草稿 |
| 退出 | 任意界面按 **ESC** 关闭 |

配置文件：`run/config/ltsmodelshare.json`（发布后是 `.minecraft/config/ltsmodelshare.json`）

```json
{
  "baseUrl": "https://www.inception.work",   // 换测试服就改这里
  "token": "",                                // 登录后自动写入（或粘贴 lts_ 开头的访问令牌）
  "refreshToken": "",                         // 滑动续期用；PAT 模式下为空
  "username": "",
  "downloadDir": ""                           // 目前下载不落盘，保留字段
}
```

## 接口契约（都用真实站点验证过）

| 步骤 | 请求 | 返回关键字段 |
|---|---|---|
| 登录 | `POST /api/account/login/password-login` `{username,password}` | `payload.{token,refreshToken}` |
| 续期 | `POST /api/account/auth/refresh` `{refreshToken}` | `payload.{token,refreshToken}`（滑动轮换） |
| 访问令牌 | `POST /api/account/token/{create,list,revoke}` | create 返回明文 `lts_…`（只此一次） |
| 列表 | `POST /api/public/work/cards` `{q,sort,tagCodes[],authorId,locale,current,size}` | `payload.records[]` / `payload.total` |
| 标签 | `POST /api/public/work/tags` | `[{code,name}]` |
| 作者 | `POST /api/public/work/authors?q=` | `[{userId,username,displayName,avatarUrl}]` |
| 详情 | `POST /api/public/work/detail?workId=N` | `payload.items[].{itemId,title,mcVersion,grid,boxCount,modNamespaces}` |
| 取结构 | `POST /api/work/download-snbt?itemId=N&version=1.12`（Bearer） | `payload.{assetUrl,fileName,sizeBytes,converted}` |
| 取文本 | `GET <assetUrl>` | SNBT 文本（服务端把 1.19 转成 1.12，`converted=true`） |
| 上传结构 | `POST /api/studio/work/upload-snbt`（multipart，`file`） | `payload.{blobId,grid,boxCount,dedup}` |
| 上传截图 | `POST /api/studio/work/upload-image`（multipart，`file`） | `payload` = 图片地址 |
| 保存作品 | `POST /api/studio/work/save` | `payload` = workId（草稿：visibility=private, status=draft） |
| 上传授权 | `POST /api/account/notification/consent/agree` `{consentType:"upload_rights",agreed:true}` | `payload.consentId`（**注意是对象**，不是裸数字） |
| 提交审核 | `POST /api/studio/work/submit-review?workId=&keepOnline=&consentId=` | `payload.{submitted,reviewId,round}` |

## 还没做 / 已知限制

1. **联机一键获取蓝图**：目前单人/局域网主机在服务端生成道具；专用服务端没有我们的服务端代码，会退回"剪贴板 + `/lt-import`"。
2. **PAT 需要部署**：站点侧代码已完成，但要建表 + 重启后端 + 发布前端才能用。
3. **上传只支持单个作品**（`kind=single`），合集/多条目还没做。
4. 密码框是明文输入（画面上打了 ●），要更安全就只用访问令牌。
5. 截图只从 `<gameDir>/screenshots` 读，不支持从剪贴板/外部路径选。

---

## IDEA / 运行客户端常见问题

### 一堆 `HTTP response code: 400`（或 `Error downloading asset`）

**症状**：启动客户端时刷几千条 `Error downloading asset (try 1..5)`、`Server returned HTTP response code: 400`，游戏启动很慢。

**原因**（实测确认，和模组无关）：

1. Minecraft 1.12.2 判断"缺资源"时用的是 **`http://`**resources.download.minecraft.net；
2. Mojang 的 CDN 现在**对 http 直接回 400**（同一路径走 https 是 404 = 域名活着）；
3. 而 ForgeGradle 的 `makeStart` 把 `GradleStart` 的默认资源目录写成了
   `~/.gradle/caches/minecraft/assets`，可资源实际下在 `~/.gradle/caches/forge_gradle/assets`
   —— 客户端看到的是**空目录**，于是把 1305 个资源全当成缺失，逐个去 http 拉 → 400 刷屏。

**修法**（已经做进 `build.gradle`，`runClient` / `runServer` 会自动带上）：

```powershell
.\gradlew.bat fixAssetsDir   # 在两个 cache 目录之间建 junction（Windows 目录软链）
```

它等价于手动执行：

```powershell
$h = "$env:USERPROFILE\.gradle\caches"
cmd /c mklink /J "$h\minecraft\assets" "$h\forge_gradle\assets"
```

修完再启动，400 归零（实测 6484 → 0）。

### 在 IDEA 里跑

两种都行：

1. **推荐**：直接用 Gradle 面板里的 `runClient` 任务（这样会走 `fixAssetsDir` + `--assetsDir` 参数）；
2. 或者 `.\gradlew.bat genIntellijRuns` 生成 IDEA 运行配置（`run/client`），再在 IDEA 里跑那个配置。
   注意 IDEA 里自己手建的 Application 配置**不会**带 FG 生成的启动参数（资源目录、natives、tweakClass），十有八九起不来。

### 中文日志变成 `???`

1.12.2 的控制台用系统默认编码（GBK），log4j 按 UTF-8 写，所以中文会显示成问号。
不影响游戏，只是难看；要看中文日志就去 `run/logs/latest.log`。

---

## LittleTiles 依赖（1.12.2）

模组的「获取蓝图」要用 LittleTiles 自己的导入器，所以**开发和运行都需要**：

| 放哪 | 用途 | 说明 |
|---|---|---|
| `deps/` | 只给编译（`compileOnly`） | `LittleTiles_v1.5.66_mc1.12.2.jar` |
| `run/mods/` | 开发客户端加载 | LittleTiles + **CreativeCore**（LittleTiles 的前置） |
| 玩家的 `mods/` | 运行时 | 玩家自己的整合包本来就带这两个 |

**两个坑（都踩过）：**

1. **不要放 `libs/`** —— ForgeGradle 2.3 会把 `libs/` 整个加到开发运行期 classpath，
   于是同一个 jar 既在 classpath 又在 `run/mods` → `DuplicateModsFoundException`。
2. **不要用 `provided`** —— 它同样会进运行期 classpath，结果一样。

另外注意：当 `libs/` 里有重复且都声明了 coremod 时，LittleTiles 的 transformer
会对同一批类**处理两次**，报 `VarInsnNode cannot be cast to LabelNode`（看起来像 ASM 版本问题，
其实是重复加载）。挪到 `deps/` 后原版 jar 在开发环境正常：`Forge Mod Loader has successfully loaded 9 mods`。

## 「获取蓝图」的流程

1. `POST /api/work/download-snbt?itemId=&version=1.12`
   —— 结构不是 1.12 的话**由服务端先转成 1.12**（响应里 `converted=true`）；
2. `GET assetUrl` 取 SNBT 文本（不落盘）；
3. 用 LittleTiles 的 API **在服务端**生成蓝图道具：
   `StructureStringUtils.importStructure(snbt)` → `ItemStack`；
4. 塞进玩家背包（`EntityPlayerMP.inventory.addItemStackToInventory`），
   **放不下就掉在脚下**（`dropItem`）。

> 为什么强调"服务端"：客户端只有 `EntityPlayerSP`，往它背包里塞东西会被服务端同步覆盖，
> 联机无效、单人也不可靠。单人游戏走**集成服务端**（`Minecraft.getIntegratedServer()`）
> 的玩家实体，背包变化才是服务端权威、重进游戏还在。
>
> **联机（专用服务端）限制**：我们没有服务端代码，此时自动退回 LittleTiles 的导入界面
> ——结构写入剪贴板并执行 `/lt-import`，在弹框里 Paste + Import 即可。

## 上传流程

1. 手里拿着一张 LittleTiles 蓝图打开「上传蓝图」；
2. 用 `StructureStringUtils.exportStructure(stack)` 导出 SNBT；
3. `POST /api/studio/work/upload-snbt` → `blobId`（服务端算哈希、去重、生成白模）；
4. 选中的截图逐张 `POST /api/studio/work/upload-image` → 图片地址；
5. `POST /api/studio/work/save`（`kind=single` + `items=[{snbtBlobId}]` + `images` + `tagCodes`）→ `workId`；
6. 若要提交审核：`POST /api/account/notification/consent/agree`（`upload_rights`）拿 **consentId**
   → `POST /api/studio/work/submit-review?workId=&consentId=`。

> 坑：`consent/agree` 返回的是对象 `{consentId: 79}`，按 `Resp<Long>` 解析会得到 null，
> 表现为"申请上传授权失败"，但其实是解析问题（不是权限/协议）。