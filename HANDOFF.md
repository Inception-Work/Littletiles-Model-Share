# 1.0.0 稳定版发布（2026-11-10）

- 用户确认六平台验收无问题并授权上架。
- 新增 Grid 独立筛选、混合合集过滤、真实网格兼容校验、关注动态和多语言更新提示；包含永不提醒设置。
- 1.20.1 图片 CDN 缩略图与并发优化；1.20.2/1.21.1 修复更新弹窗后置背景模糊。
- 默认构建渠道为 stable，发行说明见 releases/1.0.0.i18n.json。
- 正式包、源码提交、标签及网站上架结果须以最终发布清单为准。

---

# 0.1.9 最新状态（2026-11-09）

- 用户已逐个启动并确认六个平台的实机验收全部通过：Forge 1.12.2 / 1.19.4 / 1.20.1 / 1.20.2，NeoForge 1.20.1 / 1.21.1。
- 本次修复“我的空间”私有作品、私有合集与凭码作品缺失，包含翻页及版本筛选；修复登录失效自动续期和回到登录界面；接入浏览/下载统计及重试去重；改善私有图片与 SNBT 的 CDN 加载体验；Forge 1.12.2 修复密码遮罩偏移。
- 0.1.9 完整构建和协议回归检查通过。现代版各 Session 20 项、V2 23 项；1.12.2 Session 20 项、API 83 项，并通过七语言和 OBJ 检查。最终发行产物及哈希见 build/releases/ 对应 manifest。
- 源码提交、标签及网站发布由主 agent 完成中；本段以下内容为历史交接记录，其中“未验收”等结论不代表本次状态。

---

> 2026-11-07 V2 最新修复：[修复记录与验证边界](design/integration-audit/NeoForge1.21.1-V2修复记录-2026-11-07.md)。候选包已生成；真实账号闭环和游戏实机待验收，未发布。

# LittleTiles Model Share 开发交接

> 更新：2026-10-28。此文档针对 `D:\DevelopmentProject\littletiles-model-share` 的**当前工作树**。0.1.3 测试版已发布，详见第 9 节；工作树尚未提交。项目原有工具链/历史排障详见 [README.md](README.md)。

## 1. 项目与边界

这是 Minecraft **1.12.2 / Forge 14.23.5.2847+ / Java 8** 的游戏内模型共享模组，模组 ID `ltsmodelshare`，当前源码版本 **0.1.3**。依赖玩家已安装 LittleTiles **1.5.66** 与 CreativeCore **1.10.70**。入口为游戏内按 **I** 或执行 `/ltsmodels`；服务端参与「获取蓝图」，预览与界面只在客户端运行。

站点/接口另在 `D:\DevelopmentProject\inception-work`：Spring Boot 后端 `Sources/server`，Vue 前端 `Sources/web`。**网站的 Three.js 白模 OBJ 预览与游戏内的 LittleTiles 原生预览是两套功能**，不要混用 OBJ 文件大小与 SNBT 文件大小。

## 2. 当前实现导航

| 功能 | 入口及要点 |
|---|---|
| 模组生命周期 | `src/main/java/work/inception/ltsmodelshare/LtsModelShare.java`：`@Mod`、客户端/服务端代理 |
| 客户端入口 | `proxy/ClientProxy.java`、`gui/GuiModels.java`：按键、列表、高级搜索、进入详情 |
| 通信与 DTO | `api/ApiClient.java`：单线程 HTTP、401 续期、`post` 切回客户端线程；`api/Dtos.java` 只映射模组需要的字段 |
| 本地登录配置 | `ModConfig.java`：`<gameDir>/config/ltsmodelshare.json`；含 token，**不可提交实际配置或在日志中打印令牌** |
| 作品详情 | `gui/GuiWorkDetail.java`：图片、作者头像、模型条目、并排「3D预览／获取蓝图」 |
| 作者主页 | `gui/GuiAuthorHome.java`：头像进入、搜索/翻页/作者作品列表；从这里进入详情，ESC 返回作者页 |
| 原生模型预览 | `gui/ModelPreviewPanel.java`：按需取 SNBT、版本转换、LittleTiles FakeWorld/实体渲染、拖动/缩放/光照及 VBO 清理 |
| 蓝图获取 | `gui/GuiWorkDetail.java`、`client/BlueprintClient.java`、`server/BlueprintServer.java`、`network/`：服务端生成并发回结果；不可仅在客户端修改背包 |
| 上传 | `gui/GuiUpload.java`：手持蓝图导出 SNBT，上传截图/标签，保存草稿或提交审核 |
| 网格兼容 | `client/GridLimits.java`：读取 LittleTiles 当前配置的最大 grid，超限时给修改 `config/littletiles.json` 的提示 |

### 关键 HTTP 接口

- `POST /api/public/work/cards`：列表；`POST /api/public/work/detail?workId=...`：详情（邀请作品需要访问码）。
- `POST /api/public/work/author`：作者主页。对应 `ApiClient.authorHome(...)`。
- `POST /api/work/download-snbt?itemId=...&version=1.12[&code=...]`：需要登录；服务端按**文件实际方言**把非 1.12 结构转换到 1.12，返回 `assetUrl`、`sizeBytes`、`version`、`converted`；随后 `GET assetUrl` 取得 SNBT。
- `POST /api/studio/work/upload-snbt`、`upload-image`、`save`、`submit-review`：上传与审核。上传前的 `consent/agree` 返回的是 `{consentId: N}`，**不是裸数字**。

接口返回壳为 `{success,payload,error_code,error_message}`；更完整的路径和业务限制见模组 [README.md](README.md) 及站点 `D:\DevelopmentProject\inception-work\docs\API.md`。

## 3. 最新的原生预览设计

1. 详情 `items[]` 的 `boxCount`、`grid` 和 `snbtSizeBytes` 是上传 SNBT 的元数据。`ModelPreviewPanel.available(item)` 只在 `boxCount` 已知且为 **1～6000**、`grid` 已知且不超过当前 LittleTiles 配置时显示按钮；**收到** `snbtSizeBytes` 时还要求原件不超过 **2 MiB**。`preview.available` 是**白模 OBJ** 的状态，不能用于判断游戏内预览。
2. 站点后端 `Sources/server/src/main/java/work/inception/server/modules/work/service/WorkPublicService.java` 已为实时条目和审核快照补充 `snbtSizeBytes`。**必须部署新版后端，入口才能在点击前按文件大小隐藏**；老后端缺字段时保持 box/grid 判定，并在下载响应中再检查大小。
3. 点击后请求 `version=1.12`，检查响应 `version == "1.12"`，且再次验证转换后的大小；转码失败或超限只显示错误，不向 LittleTiles 传其他版本文本。源码中的 `mcVersion` 仅供提示，**实际转换以服务端对文件的检测为准**。
4. `StructureStringUtils.importStructure(text)` → `LittlePreview.getPreview(stack,false)` → `new AnimationPreview(previews)` → `LittleAnimationHandlerClient.render.doRender(...)`：使用 LittleTiles 自身的 FakeWorld、方块贴图和实体渲染。网络线程只取文本；Minecraft/LittleTiles 解析、FakeWorld 构建和 GL 绘制必须在客户端线程。
5. 预览画布为较亮灰蓝背景；绘制期间临时全亮 lightmap，`finally` 恢复原游戏 lightmap。关闭、切页或绘制失败时调用 `animation.markRemoved()` 卸载 LT 渲染缓存/VBO。拖动旋转、滚轮缩放、ESC/右上角关闭。

**注意**：原生预览仍须用实际模型做视觉验证，尤其是透明方块、动态结构、额外模组方块、转换后的大模型、开关预览多次后的显存/性能。Forge GUI 渲染通道对透明层的覆盖可能与放进世界后的结果不同。仅构建通过**不等于**这些效果已通过实机验收。

## 4. 构建、安装与验证

**版本发布约定（2026-10-29 用户要求）**：每次发布，包括测试版，都必须将对应源码提交，并创建、推送带说明的 Git tag（沿用 `v<版本号>`，如 `v0.1.3`）。tag 必须指向实际打包的源码提交；发布前核对源码版本、包内版本、发布渠道和 tag 一致，发布后核对远端 tag 存在。不得把新版本 tag 打在旧源码提交上。玩家更新说明只写功能与使用体验，不暴露内部实现或配置细节。

在模组根目录运行：

```powershell
.\gradlew.bat build --offline --no-daemon
# 开发客户端：.\gradlew.bat runClient
# 专用服务端冒烟：.\gradlew.bat runServer
```

- Gradle **4.10.3 / ForgeGradle 2.3**；`gradle.properties` 指向本机 JDK 8。若移机，需要调整该 JDK 路径，不能用 JDK 21/25 直接运行这套 Gradle。
- `build.gradle` 将 `deps/LittleTiles_v1.5.66_mc1.12.2.jar` 和 `deps/CreativeCore_v1.10.70_mc1.12.2.jar` 设置为 **compileOnly**。运行时在游戏 `mods/` 装相应依赖；**不要**将这两个 JAR 放 `libs/`、改成 `provided`，或打进本模组，避免 DuplicateModsFoundException。
- 发布用 `build/libs/LittletilesModelShare-0.1.2-<时间戳>.jar`，**不要**用 `-sources.jar`。每次构建生成新时间戳，安装前按修改时间选最新包，旧包要从 `mods/` 移走，避免同时加载两个版本。已核对 2026-10-28 14:40 的构建产物包含 `GuiAuthorHome` 与 `ModelPreviewPanel`；再次交接或部署时仍应重新构建确认。
- 2026-10-28 已通过：模组 `gradlew build --offline --no-daemon`，站点后端 `mvnw.cmd -q -DskipTests package`；后端这次跳过测试，游戏内最终效果未做完整回归。日志在开发客户端 `run/logs/latest.log`，玩家端 `.minecraft/logs/latest.log`。

**建议实机验收清单**：

1. 用已登录账号打开公开作品与带访问码的邀请作品；头像进入作者主页，作品卡返回详情，ESC 逐级正确。
2. `1.12` 与 `1.19+` 各预览一个小模型；确认后者服务端确实返回 `version=1.12, converted=true`，材质、视角、亮度正常，拖拽、滚轮、关闭可用，获取蓝图按钮互不遮挡。
3. `boxCount=0/空/>6000`、grid 超客户端上限、SNBT >2 MiB：按钮隐藏。大文件条件要用**部署了 `snbtSizeBytes` 的后端**验证。
4. 连续开关多个预览，退出游戏世界、断网、令牌过期、模组方块缺失时不崩溃；查看 `latest.log` 的 `LittleTiles 原生预览` 报错。
5. 单人与装了本模组的专用服务器分别试获取蓝图；服务端没装本模组时确认剪贴板 + `/lt-import` 回退提示。

## 5. 当前工作树与交接注意

截至本次交接，模组仓库有**未提交改动**：作者主页、邀请访问、原生预览相关代码与 `build.gradle` 均在工作树中。站点仓库也有其他正在开发的未提交改动（邀请访问等），**不要整体重置、覆盖、自动提交或顺手部署**。先运行 `git status --short`、逐个查看差异，再只处理本任务涉及的部分。

现阶段不应声称线上后端已经部署 `snbtSizeBytes`，也不应把某个时间戳 JAR 视作永久唯一的发布版本。后续若改预览阈值，要同步 `ModelPreviewPanel.available`、下载响应二次校验、实际 LT `tiles.totalSize()` 校验和文档；若改接口契约，要同时更新模组 DTO、站点后端与网站类型定义。

## 6. 多语言（2026-10-28）

- 界面使用 Minecraft 1.12.2 原生 `.lang`，与网站对应的简体中文、繁体中文、英文、日语、韩语、德语和法语共 7 种语言，各 191 个键；其他界面语言按 Minecraft 回退到英文。入口为 `client/Translations.java`，服务端不引用客户端 I18n。
- 网站内容统一走 `Translations.siteLocale()`：列表、作者主页、公开/邀请详情均传网站支持的 locale。作品及模型标题/简介使用服务端已有译文与回退。
- `Dtos.Tag.nameI18n` + `client/SiteTags.java` 遵循网站 `utils/workTag.ts` 的优先级：数据库译文 → 网站内置译文 → 原名，保持 tag code 不变。内置回退源自网站的 7 种语言，各 9 个标签；`tools/sync_site_tags.py` 可同步/核对，数据库译文不需要同步脚本。
- 蓝图服务端回执在原 string 字段内发送原版翻译组件 JSON，接收端按玩家语言解析，兼容旧服务端纯文本；旧客户端不能解析新格式，联机建议两端升级同一 JAR。
- 新增语言包为 `zh_tw.lang`、`ja_jp.lang`、`ko_kr.lang`、`de_de.lang`、`fr_fr.lang`；`LocalizedButton` 与 `TextUtil.drawCenteredFit` 让较长的按钮译文在原点击区域内缩放显示。
- 已通过离线构建及 `localizationChecks`（语言映射、标签优先级和回退、全部 7 种语言的格式化/换行、翻译组件 JSON）；语言键和参数另由 `python tools/check_translations.py` 校验。未做游戏内语言切换与界面视觉验收；未修改或部署网站仓库。

## 7. 页面导航（2026-10-28）

- 完整页面的返回/首页关闭按钮统一使用 `GuiNavigation.backButton`：右下角、右/下边距各 10、尺寸 80×20（GUI 像素）。首页翻页另起一行，其他底部操作位于左侧，并为返回按钮预留空间。提示弹窗的确认按钮仍属于弹窗本身。
- 详情与登录页 ESC 已修正为返回各自上一页；预览先关闭再返回详情。邀请详情的 parent 现为邀请页，返回保留 ID 和访问码；登录和邀请请求完成时不会把已经离开的页面重新打开。
- 上传结果弹窗先关闭到上传页，下一次 ESC 才回上一页；成功提交后停用提交操作，避免重复上传。处理中的上传仍等待完成。
- 已通过离线构建、现有 7 语言行为校验和翻译键校验。尚待实机检查右下角位置、多级返回路径与最小 GUI 窗口下的布局。

## 8. 列表、作者区域与 CDN（2026-10-28）

- `gui/ModelGridLayout` 统一计算卡片布局，取消三列/三行上限，每页不超过后端的 48 项限制。320×240 GUI 为 4×2，427×240 为 5×2，640×360 为 7×3，854×480 为 10×4。状态消息移至卡片区域，翻页和固定返回按钮各有独立空间；这里的尺寸是 Minecraft 缩放后的 GUI 像素。
- 首页卡片标题缩放为原来的 0.8，按缩放后的宽度截断。详情作者头像、显示名和用户名共用整行背景与点击区域；只响应正文可见范围内的点击。
- 从作者主页进入详情后，点击同一作者时按 `userId` 判断并返回原 `GuiAuthorHome` 实例，保留搜索、精确匹配和页码；其他来源仍正常创建作者页，避免作者页/详情页循环堆叠。
- `api/AssetHeaders` 从配置 `baseUrl` 提取站点根来源，移除用户信息、路径、查询和片段。`ImageCache` 的远程图片和 `ApiClient.fetchText` 的 SNBT 下载发送 Referer，默认 `https://www.inception.work/`；本地截图不变。失败响应也释放 HTTP 连接。
- 多吉云域名防盗链白名单加入 `www.inception.work` 后可禁止空来源。未修改 CDN 控制台；Referer 可由客户端指定，仅作防盗链兼容，不作身份验证。更新 JAR 并重启游戏可清除图片失败缓存。
- 已通过离线构建、语言/标签脚本检查、本地 HTTP 来源头与重定向测试、上述 GUI 尺寸的布局边界及 48 项上限检查。未进行游戏内视觉或多吉云线上验收。

## 9. 0.1.3 测试版发布（2026-10-28）

- 用户明确授权打包并发布新测试版。构建版本与模组版本均升为 `0.1.3`，网站新增 `releaseId=4`、`channel=beta`，保留 0.1.0～0.1.2 的历史记录。未部署网站前端或后端程序，未提交代码。
- 玩家更新说明维护于 `releases/0.1.3.md` 与 `releases/0.1.3.i18n.json`，只介绍加入的体验，不写问题修复、来源配置或实现细节；七种语言已发布并逐项核对。
- 发布文件 `build/releases/0.1.3-beta/LittletilesModelShare-0.1.3.jar`，222174 字节，SHA256 `6e98a043c9d9a423f9dcc2cc0e697b9a831c2746ce41c417b8ddc3821e076efa`。网站下载入口 `/api/public/mod/download?releaseId=4`；已实际下载并校验文件名、大小和哈希一致。
- 服务器按已有发布文件目录规范存放 JAR；发布前版本表备份 `/www/backup/ltsmodelshare-0.1.3-beta/mod_release-before.sql`。本地发布元数据及验证记录在 `build/releases/0.1.3-beta/`。发布检查确认旧版仍启用。
- 离线 Gradle 构建及现有行为检查通过；核对发布包版本与七份语言资源。尚未实机验收游戏画面。

## 10. 0.1.4 测试版发布（2026-11-02）

- 版本升至 `0.1.4`，网站新增 `releaseId=5`、`channel=beta`、`loader=forge`、`mc_version=1.12.2`，旧版本保持启用。
- 更新说明位于 [releases/0.1.4.md](releases/0.1.4.md) 与 [releases/0.1.4.i18n.json](releases/0.1.4.i18n.json)，七种语言已随版本表写入并通过 `/api/public/mod/latest` 校验。
- 发布文件 `build/releases/0.1.4-beta/LittletilesModelShare-0.1.4.jar`，283158 字节，SHA256 `3af1bd760c9e4971547ce0eb01fe65bb37038f22b769541a3661148ec838d02a`；已从 `/api/public/mod/download?releaseId=5` 实际下载并核对哈希与大小一致。
- 源码提交 `a2d6fe6`（`release: 0.1.4 beta`），本地与远端分支 `forge/1.12.2`、tag `v0.1.4` 均已推送；发布前数据库表备份在 `/www/backup/ltsmodelshare-0.1.4-beta/mod_release-before.sql`。
- 构建、七语言键检查、OBJ 解析检查通过；游戏内界面与联机流程尚未作最终实机验收。
