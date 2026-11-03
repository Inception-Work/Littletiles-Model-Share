# LittleTiles Model Share

> 在游戏内浏览、获取和分享 LittleTiles 模型。
> An in-game browser, downloader and uploader for LittleTiles models shared on [inception.work](https://www.inception.work/models).

**一句话 / One-liner**

- 中文：在游戏里按 `I` 打开模型面板，挑一个模型点「获取蓝图」，它就变成背包里的 Little 蓝图；手里拿着的蓝图也能连同截图和标签一起上传。
- English: Press `I` in game, pick a model, hit "Get blueprint" and it lands in your inventory as a Little Blueprint. The blueprint in your hand can be uploaded back with title, description, tags and screenshots.

**特点 / Features**

| 中文 | English |
|---|---|
| 游戏内登录（账号密码或访问令牌），不存密码 | Sign in from inside the game (password or personal access token); passwords are never stored |
| 卡片流浏览 + 高级搜索（作者 / 标签 / 排序） | Card grid plus advanced search by author, tags and sort order |
| 详情页：封面、多图、标签、数据、合集依赖 | Detail page with cover, gallery, tags, stats and collection dependencies |
| 一键获取：模型**在服务端**变成蓝图进背包，单人 / 局域网 / 专用服务器都可用 | One-click download: the blueprint is created **server-side** and put into your inventory — works in single-player, LAN and on dedicated servers |
| 上传：标题、多行简介、标签、游戏内截图，可存草稿或直接提交审核 | Upload with title, multi-line description, tags and in-game screenshots; save as draft or submit for review |
| grid 超限时告诉你怎么改 `config/littletiles.json` | Tells you exactly what to change in `config/littletiles.json` when a model needs a bigger grid |

**依赖 / Requires**

- Minecraft 1.12.2 + Forge 14.23.5.2847 或更新
- [LittleTiles](https://www.curseforge.com/minecraft/mc-mods/littletiles) 1.5.66（服务端也要装）
- 客户端与服务端装同一个本模组版本

**Credits**

LittleTiles by [CreativeMD](https://github.com/CreativeMD)

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
| 浏览 | 卡片流：封面（等比裁剪）+ 0.8 倍标题 + 作者头像 + 下载/浏览数；行列随 GUI 尺寸变化，每页最多 48 项，320×240 GUI 下为 4 列 × 2 行 |
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

1. ~~联机一键获取蓝图~~ —— 已实现（网络包 + 服务端生成，见下）。
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

## 放置上限（`placeBlockLimit`）

**现象**：想一次放下吊塔之类的高结构，聊天栏出现
`You are not allowed to place more than 10 blocks`，模型没放下来。

**这不是本模组的限制，是 LittleTiles 自己的建造权限**：

| 项 | 说明 |
|---|---|
| 配置项 | `build` → 用户组 → `placeBlockLimit`（`{ "enabled": bool, "content": int }`，默认 `10`） |
| 判定口径 | `preview.previews.getVolumeIncludingChildren()` —— **一次放置预览的整体体积**（含子结构，单位是方块格，不是 LittleTiles 的细分 tile） |
| 默认分组 | `creative`（按 gamemode 判定）三条上限全关；`survival` 与 `default` 都是 10 |
| 同类另两条 | `editBlockLimit`（默认 10，一次能改多少方块）、`affectedBlockLimit`（默认 2，一次影响几个相邻方块） |

**改法**：退出游戏 → 打开 `config/littletiles.json` → `build` → `survival`（或 `default`）→ `placeBlockLimit`：

```json
"build": {
  "survival": {
    "placeBlockLimit": { "enabled": false, "content": 10 }
  }
}
```

- 想彻底放开就 `"enabled": false`；只想放宽就把 `"content"` 调大（例如 512）。
- 改的是**服务端**那份配置（单人存档用同一个文件），改完重启游戏/服务器。
- 不想改配置时：分几次放（每次体积 ≤ 10），或者切创造模式放完再切回来。

> 注：LittleTiles 把这条拒绝信息当纯文本发到聊天栏（`Component.literal(e.getLocalizedMessage())`），没有翻译 key；
> 本模组**不拦截、不改写**这条消息，只在这里留一份改配置的说明。

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
> **联机（专用服务端）**：服务端装了本模组时，客户端把 SNBT 发给服务端
> （`MsgBlueprintRequest`，自己用 int 长度而不是 `writeUTF8String`，否则上万字符的结构会炸），
> 由服务端 `importStructure` 并塞进玩家背包，再把结果（`MsgBlueprintResult`）回执给客户端。
> 服务端没装本模组时，6 秒收不到回执会自动退回「剪贴板 + `/lt-import`」。
>
> 模组因此**不再是 `clientSideOnly`**：按键 / GUI / 客户端命令都挪进了
> `proxy.ClientProxy`，专用服务端只加载 `CommonProxy` + 网络通道。
> 已用 `gradlew runServer` 实测：服务端正常加载（`successfully loaded 9 mods`，`Done (9.074s)`）。

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

## 多语言与网站译文

模组界面跟随 Minecraft「选项 → 语言」自动切换，不另存语言配置。Minecraft 1.12.2 使用 UTF-8 的 `assets/ltsmodelshare/lang/<语言代码>.lang`。当前提供与网站对应的 7 种界面语言：简体中文 `zh_cn.lang`、繁体中文 `zh_tw.lang`、英文 `en_us.lang`、日语 `ja_jp.lang`、韩语 `ko_kr.lang`、德语 `de_de.lang`、法语 `fr_fr.lang`，每种 191 个翻译键；其他游戏语言使用 Minecraft 的英文回退。新增语言时复制英文文件、保留翻译键及 `%s` 参数，在对应的小写 `.lang` 文件中翻译文案即可。

作品标题、简介和模型条目由网站提供译文：列表、作者主页、公开和邀请详情请求都传 `locale`。`zh_cn/zh_sg` 映射 `zh-Hans`，`zh_tw/zh_hk/zh_mo` 映射 `zh-Hant`，英语、日语、韩语、德语和法语的地区代码分别映射 `en/ja/ko/de/fr`，网站不支持的游戏语言请求 `en`。作品缺译时沿用网站后端的原文回退，不在模组内生成译文。

标签与网站 `Sources/web/src/utils/workTag.ts` 保持相同优先级：接口中的 `nameI18n` → 网站内置标签译文 → 标签原名。界面显示译名，搜索和上传仍发送原始 `code`。内置回退包含网站现有 7 种语言、每种 9 个标签；数据库译文随接口实时读取，新增标签无需发版。网站修改内置标签语言包后运行：

```powershell
python tools/sync_site_tags.py
# 网站位于其他路径时：python tools/sync_site_tags.py --web D:/path/to/Sources/web
python tools/sync_site_tags.py --check
python tools/check_translations.py
.\gradlew.bat build --offline --no-daemon
```

`build` 的 `localizationChecks` 会验证语言代码映射、数据库标签译文优先级及缺译回退、全部 7 种语言的格式化和多行提示、服务端翻译组件的 JSON 往返。原版风格按钮以及详情页的预览/蓝图按钮会按可用宽度缩放过长的译文，保留完整文字和原有点击区域。服务端蓝图回执使用原版 `TextComponentTranslation`，在接收玩家的客户端翻译；客户端也兼容旧服务端的纯文本回执。客户端与服务端建议使用同一新版 JAR，旧客户端不会解析新回执中的翻译组件。

实际界面仍需在游戏中分别切换全部 7 种语言，检查搜索/上传标签、作品列表/作者主页/邀请详情的译文，以及窄窗口按钮显示。第三方异常信息和网站返回的错误原文保持原样。

## 页面返回与 ESC

所有完整页面的返回按钮统一放在屏幕右下角，距右侧和底部各 10 个 GUI 像素，尺寸为 80×20；浏览器首页同一位置显示「关闭」。位置由 `gui/GuiNavigation.java` 统一计算，随窗口与 GUI 缩放更新。其他底部操作放在左侧，首页翻页按钮单独一行，为返回按钮留出空间。

ESC 每次只退一级：模型预览 → 作品详情 → 打开详情的页面，作者主页 → 打开作者页的作品详情，邀请详情 → 邀请页 → 浏览器，登录/高级搜索/上传页 → 上一页，最后在浏览器首页退出。提示弹窗先返回背后的页面；上传完成后先关闭结果弹窗，再次按 ESC 才离开上传页，成功提交后停用提交按钮以防重复上传。上传尚在处理时维持原有等待行为。

作品详情页的作者头像、显示名和用户名组成一个共同区域，点击其中任意位置进入作者主页，滚动后的点击范围与可见区域保持一致。从作者主页打开作品后，再点击同一作者区域会直接返回原作者页，保留搜索条件和页码，避免重复叠加页面。

## CDN 来源白名单

远程封面、头像及 SNBT 下载会携带网站来源头 `Referer: https://www.inception.work/`，随 `config/ltsmodelshare.json` 的 `baseUrl` 自动变化，只发送该地址的协议、主机、端口及根路径，不包含登录信息。本地截图不发送 HTTP 请求。

多吉云控制台：融合 CDN → 域名管理 → 对应 CDN 域名配置 → 访问控制 → 域名防盗链。选择白名单，按域名格式添加 `www.inception.work`（不带协议和路径）；使用其他站点地址时添加对应域名。此时可以禁用「空来源」。参考[多吉云域名防盗链文档](https://docs.dogecloud.com/cdn/manual-config-referer-rules)。来源头用于匹配防盗链规则，不能证明请求者身份。

安装新 JAR 后重启游戏，以清除旧版本已缓存的图片失败状态。已用本地 HTTP 服务验证来源头和重定向后的传递；多吉云实际配置与游戏内显示仍需实测。
