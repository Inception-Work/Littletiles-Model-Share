# LittleTiles Model Share V2 开发交接给 DeepSeek

日期：2026-11-07（Asia/Shanghai）。用户要求转交开发，因此停止继续扩展功能。**尚未完成 V2 全业务闭环，未发布。**

## 1. 工作树与授权边界

- 开发目标：D:/DevelopmentProject/lts-112，分支 forge/1.12.2，HEAD 4a6db95。
- 主目录 D:/DevelopmentProject/littletiles-model-share 是另一工作树，分支 forge/1.20.1，HEAD f582843。不要覆盖、切换主目录分支。
- 当前全部改动未提交。先检查 git status/diff，不得 reset、clean、自动提交、push、打 tag 或发布。
- 阅读 [HANDOFF.md](../../HANDOFF.md)，但历史发布记录不构成本轮发布授权。2026-11-06 关于缺少 locale 的结论已部分过时。
- 本轮没有修改 GUI、Minecraft 服务端、网络包和原生预览源码；没有修改网站共享源码、生产配置或数据库结构。
- 人工验收站 5220/8120 连接远程真实数据库：只做了匿名公开列表 GET，没有在那里登录、建账户、上传、收藏、送审或改配置。未读浏览器令牌或玩家登录配置。

## 2. 当前代码及改动

| 文件 | 当前实现及边界 |
|---|---|
| [ApiClient.java:20](../../src/main/java/work/inception/ltsmodelshare/api/ApiClient.java:20) | 保留单线程 HTTP 队列与现有 GUI 回调。新增包内 Environment 测试入口，真实联调使用内存配置，不读取玩家配置。 |
| [ApiClient.java:41](../../src/main/java/work/inception/ltsmodelshare/api/ApiClient.java:41) | 入队捕获会话 identity；执行及 request/upload/asset 前检查，阻止排队动作转到新账户。保留请求后检查。 |
| [ApiClient.java:196](../../src/main/java/work/inception/ltsmodelshare/api/ApiClient.java:196) | 双令牌刷新、401 单次重试、捕获退出旧会话；失效刷新清空令牌，网络/5xx 不直接清空。尚未完整解决原子写回竞态。 |
| [V2Contract.java:48](../../src/main/java/work/inception/ltsmodelshare/api/V2Contract.java:48) | V2 映射到原 GUI DTO，列表、作者作品、收藏、详情补 locale。服务器不支持的筛选明确报错。 |
| [ApiClient.java:283](../../src/main/java/work/inception/ltsmodelshare/api/ApiClient.java:283) | 原 followingFeed 仅扫描最近公开几页再筛关注作者，会漏数据；现在非空关注列表明确报 V2_FOLLOWING_FEED_UNSUPPORTED，仍可从作者主页浏览。 |
| [V2Http.java](../../src/main/java/work/inception/ltsmodelshare/api/V2Http.java) | 同源 /api/v2/ 才带 Bearer，显式 GET 重定向、有界读取、不重放写请求、不接受 HTTPS 降级，阻止跨源 access/media 重定向。增加 Accept-Language。 |
| [AssetHeaders.java](../../src/main/java/work/inception/ltsmodelshare/api/AssetHeaders.java) | 同源用配置站点根 Referer，跨源固定 https://www.inception.work/，兼容开发站访问线上资源防盗链。Referer 不作权限凭据。 |
| [ImageCache.java](../../src/main/java/work/inception/ltsmodelshare/client/ImageCache.java) | 远程图片走有界 V2Http，不附账户 Bearer；保留签名 URL。移除日志中的 URL/异常全文，失败集合更新回客户端线程。 |
| [LegacySnbt.java](../../src/main/java/work/inception/ltsmodelshare/api/LegacySnbt.java) | 严格 UTF-8 和有界方言门禁，拒绝现代结构、旧 compact、HTML、坏编码、混合 children。只判别，不转换或改写，也不是 LittleTiles 完整语义校验器。 |
| [ApiClient.java:423](../../src/main/java/work/inception/ltsmodelshare/api/ApiClient.java:423) | fetchText 检查实际字节及 legacy 方言，已声明大小不符则拒绝，缺大小时记录实收大小。游戏线程仍执行原生导入。 |
| [V2Errors.java](../../src/main/java/work/inception/ltsmodelshare/api/V2Errors.java) | error_code/error_type 七语言解释及白名单参数替换，不展示任意服务端文字/签名 URL。 |
| [v2-errors.json](../../src/main/resources/assets/ltsmodelshare/v2-errors.json) | 42 个七语言条目，网站核心译文快照加客户端错误；不是全量网站错误目录。 |
| [ModConfig.java](../../src/main/java/work/inception/ltsmodelshare/ModConfig.java) | 会话字段改 volatile，仅改善可见性，不等于原子事务。 |
| [ApiContractChecks.java](../../src/test/java/work/inception/ltsmodelshare/api/ApiContractChecks.java) | 当前 79 项离线契约测试。 |
| [LiveV2Checks.java](../../tools/integration/LiveV2Checks.java) | 独立 Java 21 真实 HTTP 工具，硬编码本轮 18126/13326；使用实际 ApiClient/V2Http/V2Contract，不打包入模组。 |

上一轮已有 resourceId 图片关联、SNBT 上传映射、草稿版本缓存、当前协议回执绑定、分享 grant 缓存，不要当作本轮从零实现。

## 3. 验证事实

### 最终源码离线验证：通过

使用 Java 8、Gradle 4.10.3 / ForgeGradle 2.3：

    Set-Location D:\DevelopmentProject\lts-112
    $env:JAVA_HOME='C:\Users\gaksy\.jdks\temurin-8'
    $env:Path=$env:JAVA_HOME+'\bin;'+$env:Path
    .\gradlew.bat --offline --no-daemon apiContractChecks localizationChecks objModelChecks

最后结果：apiContractChecks 79 项通过；localizationChecks 七语言与布局计算通过；objModelChecks 大 OBJ 和坏索引通过；Java 8 编译通过；git diff --check 通过，仅 CRLF 提示。
日志：[handoff-offline.log](../../build/v2-integration-20261107/handoff-offline.log)。

localizationChecks 含已有本地回环 HTTP 来源头测试，不能当网站业务联调。没有执行本轮完整 build/reobf 打包。

### 真实 HTTP：已执行部分

人工环境仅匿名 GET：

- http://127.0.0.1:5220/api/v2/works?page=1&size=2&locale=en
- http://127.0.0.1:8120/api/v2/works?page=1&size=2&locale=en

均 200，V2 payload/items。首次 total=16，后续一次 15，其他任务仍在使用该环境，数量不作固定断言。**未完成已迁入作品的逐条详情、元数据和下载字节核查。**

独立实例最近已完成证据：[evidence-http.json](../../build/v2-integration-20261107/evidence-http.json)。100 条结果：98 符合预期，2 项真实契约缺口。具体业务证据：

- 密码登录、me、refresh、退出。旧/退出后 refresh 返回 410/M99-006；失效 access 访问 me 返回 401/S00-401。
- refreshToken 轮换。access JWT 同秒、同 claims 可能字符串相同，不能要求每次必然不同。
- **直接 ApiClient 本体**：登录、me、分页；损坏测试 access 签名但保留 sid，服务端真实 401，ApiClient refresh 后重试成功；排队时换账户被拒绝；捕获退出旧会话；服务端撤销后清空双令牌。
- V2Http/V2Contract 链：SNBT 上传、合成 PNG 上传、聚合草稿保存、409/M99-004 版本冲突。
- 未同意协议时 receiptBody 拒绝；无回执送审 428/M99-007；草稿存在。
- 专属测试角色读取实际协议并明确构造 fixture 同意，取得 work/version 绑定回执、送审、经专属审核员批准。
- 七种 locale 详情及 DTO 映射、作者作品/收藏列表可请求；fixture 没有完整七语译文，不代表翻译质量已验收。
- 真实 legacy SNBT 返回字节与上传一致，记录大小和 SHA-256；匿名源文件下载被拒绝。
- 收藏/取消、关注/取消及状态。
- 分享码缺失/错误/正确；在专属库修改 fixture share_version，验证 grant 撤销 410；private 和下架详情/下载 404。
- 全 scope PAT：me/works 可读；tags/authors/agreements/follow-state/favorite/access 被 403 拒绝。未放开权限。
- 账户停用：me 401、refresh 410、登录 403/M01-006。

fixture 的部分状态使用专属库 SQL 构造，这是权限反例准备，不等于对应网站管理页面流程已验收。
HTTP 工具运行于 Java 21（网站 fixture/JDBC 依赖需要），加载 Java 8 编译的模组类；还没有游戏内 Java 8 客户端网络验收。

### 新增但尚未重跑的场景

当前 LiveV2Checks.java **比上述 100 条 evidence 新**。最后补写但未编译/执行：

1. ApiClient 本体上传 SNBT/截图、保存草稿和未同意协议保留草稿。
2. 等待 121 秒验证分享授权自然过期，再换 grant。
3. 发布下一修订，拒绝旧 item 下载入口。
4. 纯 Java SnbtConverter 生成现代 fixture，故意标为 1.12.2，核对服务器是否错误原件直出和模组是否拒绝。

这些不能算通过。此工具不属于 Gradle test source set，79 项离线测试也没编译它。工具即使有 GAP 仍写 JSON、可能 exit=0，必须检查证据内容。重跑前备份旧 evidence 文件。

### Minecraft 客户端/服务端实机：未做

没有启动 runClient/runServer。GL、FakeWorld、透明块、结构、预览反复开关的资源释放、集成服务端和专用服务器蓝图物品都未验收。原生代码未改不等于兼容性已验收。

## 4. 网站侧缺口

详见 [网站接口集成请求](网站接口集成请求-2026-11-07.md)。

- 新上传并发布的 fixture：详情有 sizeBytes，但没有 grid/boxCount；原生预览入口依赖这些字段。不能推断所有迁入作品都缺，应逐条核查用户所说既有能力。
- 原件 download-info 实际只返回 workId/fileName/mcVersion/assetUrl/version，缺 sizeBytes；转换分支另填转换大小。
- 网站按 mcVersion 标签选择转换并回填请求 version：目前是源码证据，现代错标 fixture 新测试尚未执行。
- 全站列表仍无标签/来源版本/全站 exact、sort 完整契约；作者列表支持自己的 exact/sort/locale。
- 完整关注动态缺接口，不能只筛当前几页。
- PAT 作者、标签、互动、协议、带码授权路由仍拒绝，不能绕过或全面放开。
- 当前协议首次/更新未同意：保留草稿，引导网站确认，不伪造回执，不使用 inherited 或其他 text/version 替代。
- Reader 贴图、个人档案、备份不是前置条件；未改 Reader。独立实例禁用外部 Reader，纯 Java fixture 转换不代表线上 Reader 通过。

## 5. 模组侧未完成项

1. **会话原子更新**：volatile/identity 挡住已测排队换账户，但登录/刷新仍逐字段写配置，检查到写回之间有窗口。需原子快照/条件写回和响应返回时退出/换号、匿名 ABA、网络与 5xx 测试。
2. **多阶段回调归属**：完整审查 upload → image → save → consent，不只检查单次 request。Environment 为包内测试入口。
3. **草稿重试**：saveWork 当前总是 POST 创建；GUI 无编辑草稿入口。PUT 版本冲突仅工具测试；重复上传/同意失败后重试尚未绑定同一个草稿。不能直接拿 lastSaved 猜用户意图。
4. **错误规范**：42 条不是全量；未知错误目前落 C0-01，可能语义不准确；继续补模块错误/参数测试，但不能显示任意含凭据响应。
5. **HTTP 审查**：常规跨源 Bearer 剥离已测；编码后的 capability 参数、dot segments/编码路径、跨源后返回 API 链还没有完整覆盖。
6. **SNBT**：轻量门禁不验证全部 tile/box 语义；合法特殊结构需真实样例及原生导入验收，不能为通过而放开所有现代结构。
7. **图片**：签名媒体过期刷新、换号缓存隔离、实际生产 CDN 成功率未验收。
8. **预算**：源 SNBT、转换件、OBJ 分开。现有预览 boxCount 1～6000、grid 不超本地、SNBT 2 MiB；fetchText 最大 10 MiB、OBJ 8 MiB，不能擅自放宽。
9. 先跑最新扩展 HTTP，再匿名核查人工站公开详情；网站问题交主会话，最后游戏实机。

## 6. 独立环境归属和恢复

目录：D:/DevelopmentProject/lts-112/build/v2-integration-20261107

- owner：lts112-v2-integration-20261107。
- Docker 容器：lts112-v2-audit-20261107，label owner=lts112-v2-integration。
- MySQL：127.0.0.1:13326，库 lts112_v2_audit_20261107。
- HTTP：127.0.0.1:18126。
- **交接时已停止两者，容器/测试库保留**；最后网站 PID 28080 经路径/入口核实后停止。
- 5220/8120 保持监听，核对时 PID 34404/34624，没有修改。
- [ownership.json](../../build/v2-integration-20261107/ownership.json) 记录归属；[source-manifest.json](../../build/v2-integration-20261107/source-manifest.json) 记录编译快照 400 个网站 Java 文件 SHA-256。
- 网站只复制到此 build 目录编译，没在共享网站仓库构建。其他 Agent 后续修改不自动包含在本快照。
- 依赖来自 inception-work/server/target/refinement-update-20261107_033925/server-v2-0.1.0-SNAPSHOT.jar，仅抽取 BOOT-INF/lib。该依赖包 SHA-256：b3dbe5a115074cbe98bd7828d6163be3a99741fef800bad0e7322058abe817b1。**不是模组交付包。**
- isolated.properties 是新生成的独立配置，不要打印；不复制用户配置。随机测试密码只存在测试进程内存。禁止读真实密钥、环境变量值和玩家配置。
- 邮件、mesh、自动翻译、维护调度已关闭，未真实发信；fixture 可能生成库内通知任务。
- 恢复前检查端口占用和容器 label；只 docker start 上述容器，不清库。然后用 Java 21 启动 work.inception.v2.InceptionV2Application，classpath 为此目录 classes 和 lib/*，spring.config.location 指向此目录 isolated.properties。后台启动必须隐藏窗口、保存新 PID。
- 最新测试工具主类是 work.inception.ltsmodelshare.api.LiveV2Checks，不能用早期 integration 包名。

工具编译所需 classpath：
1. 此独立目录/classes
2. 此独立目录/lib/*
3. lts-112/build/classes/java/main
4. lts-112/build/resources/main
5. C:/Users/gaksy/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.8.0/c4ba5371a29ac9b2ad6129b1d39ea38750043eff/gson-2.8.0.jar

使用 C:/Program Files/Java/jdk-21/bin/javac.exe -encoding UTF-8 编译 tools/integration/LiveV2Checks.java 到独立目录/classes，再在独立目录用 java.exe 运行主类。不要改成用户验收地址；工具会写入专属 fixture 和 evidence-http.json。

## 7. 构建产物与版本

**本轮没有生成可分发 JAR，因此没有本轮产物路径与 SHA-256。不要拿旧 build/libs 包验收本轮代码。**

旧包：build/libs/LittletilesModelShare-0.1.7-1104-134153.jar，2026-11-04，288186 字节，不是本轮包。

交接发现已有版本不一致：
- build.gradle:24 为 0.1.7；
- LtsModelShare.java:33 VERSION 为 0.1.3；
- 当前 build/sources/main 生成源码仍为 0.1.3。

未擅自改版本。后续打包前统一采用版本的源码常量、mcmod.info 和构建元数据，执行 build/reobf，确认依赖未打进模组，再算 SHA-256。历史 tag 约定不等于现在授权发布。

## 8. 可直接转交的任务摘要

建议继续局部适配，尚不能宣布完成或发布。先核对当前工作树和证据，编译、运行最新 LiveV2Checks；完善会话原子更新、多阶段上传及草稿重试归属；将元数据、下载字节/方言、筛选/关注动态、PAT 最小权限及协议跳转交主会话。网页布局未完成不是阻塞，接口和权限契约才是。最后完成 Java 8 构建、真实 HTTP、Minecraft 客户端/服务端三层验收，出包和 SHA-256；发布另需授权。
