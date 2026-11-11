# NeoForge 1.21.1 V2 修复记录（2026-11-07）

工作树 D:/DevelopmentProject/lts-1211，分支 neoforge/1.21.1。保留接手时已有修改，未提交、未发布。1.12.2、其他平台和网站共享代码未修改。没有操作人工验收库。

## 已确认并修复

1. V2Api.exchange 把数组 payload 丢成空对象：标签、作者搜索永远为空。现在将数组保留到 items。
2. 全站列表是扁平 authorId/authorName/authorUsername/coverResourceId；原映射只认嵌套 author 和 coverUrl。现在正确映射。
3. fetchText 取源 SNBT 时没带认证；新增 V2Transport，只在同源 /api/v2/ 携带 Bearer，显式重定向不向 CDN 传令牌，有界读取。
4. /auth/refresh 401 会再次 refreshBlocking，存在递归。login/refresh 不携带旧 access，不触发再次刷新；其他 API 只重试一次。
5. logout 在后台读取已被 GUI 清空的配置，无法撤销原会话；现在入队前捕获 base/token。
6. 图片上传后从 URL 猜 resourceId，CDN URL 不含该 ID 时保存会悄悄丢图。改用真实上传响应 URL -> resourceId 关联，缺关联明确报错；条目 dependencies 也传给保存接口。
7. SNBT 上传文件名补 .snbt，清理换行/引号等 multipart 文件名字符；上传大小读取 V2 的 size。上传 401 可刷新后重试一次。
8. 带码详情记录 work/item/code/grant 关联；下载前重新换短期 grant，传入 download-info；收藏带当前 grant。显式传入的下载 code 不再忽略。
9. ImageCache 改用同一有界传输，每跳重带来源；开发站跨源资源 Referer 为 https://www.inception.work/。移除图片日志的签名 URL，失败集合回客户端线程更新。
10. ImageUrls 对 /api/ 路径保持原样，避免错误追加 CDN /thumb。
11. 作者主页保留 kind/sort，账号语言读取 locale，详情传 locale，标签保留 nameI18n；标签显示名不再冒充 code。
12. GuiWorkDetail 的版本判断原本只接纳 1.19/1.20，补接纳 1.21；没有调整布局或游戏服务端/蓝图网络流程。

主要源码：
- src/main/java/work/inception/ltsmodelshare/api/V2Api.java
- src/main/java/work/inception/ltsmodelshare/api/V2Transport.java（新增）
- src/main/java/work/inception/ltsmodelshare/api/ApiClient.java
- src/main/java/work/inception/ltsmodelshare/client/ImageCache.java
- src/main/java/work/inception/ltsmodelshare/client/ImageUrls.java
- src/main/java/work/inception/ltsmodelshare/gui/GuiWorkDetail.java

网站当前源码已支持免回执送审和新增筛选；本轮按实际契约保留，不照搬旧报告。不把“HTTP/1.1 所有 POST 都被拒绝”当作已独立证实的原因；沿用 Java 21 HttpClient，优先协商 HTTP/2。

## 验证

Java 21，Gradle 8.14.3：
- gradlew.bat neoV2Checks -PpublicV2Read jar --offline --no-daemon：通过 13 项。
- 最后补显式 download code 参数后，gradlew.bat neoV2Checks jar --offline --no-daemon：通过 9 项。
- git diff --check 通过，仅 CRLF 提示。
- 测试源码 src/test/java/work/inception/ltsmodelshare/api/NeoV2Checks.java。

离线 JSON：数组保留、封面/作者映射、API 图片URL及错误码。
真实回环 HTTP：重定向后内容、同源 API Bearer、跨源剥离、CDN Referer、有界读取。
真实线上匿名 GET：https://www.inception.work/api/v2/works?page=1&size=2&locale=en 和 /api/v2/tags，通过列表结构、作者映射及数组检查。未登录、未写入生产。
日志 build/neo-v2-checks-20261107.log、build/neo-v2-final-20261107.log。

候选包：build/libs/LittletilesModelShare-0.1.8.jar
SHA-256：7e795c7967a36a4310895235818fb3cbcc61fbf72b28bda6aef5123dcae47d57
它是待游戏验收包，未部署到 /lt-share-mod。

## 未完成与下一步

- 没有真实普通账号登录/刷新/撤销、上传、送审业务联调；本轮不索取密码、不读玩家配置或浏览器令牌。需要独立测试库/实例，不能在人工库制造数据。
- 未启动 Minecraft 客户端或服务端；原生导入、蓝图物品、游戏内图片、预览和资源释放待实机。
- 带码授权新增接线已编译，但真实权限矩阵、授权过期/撤销反例还待联调；源资源401刷新目前没有与JSON路径同样的恢复策略。
- 会话完整原子性、多个异步回调跨账户、缓存清理/PAT切换仍需加强。不能把本轮局部检查说成完整闭环。
- 上传 URL->ID 关联目前仅内存，重启后不支持恢复旧上传任务；草稿编辑/重试、版本冲突也待真实业务验证。
- 当前响应错误以 canonical code 为主，七语言模块错误/参数尚未完整接入；请求语言也应继续统一。
- 源 SNBT 仍需针对 1.21 真实方言字节、大小和原生导入验收；不能仅靠 version 声称兼容。
- 作者搜索、私密列表 hidden 语义及完整关注动态还需进一步覆盖；现存关注动态仍有扫公开页再过滤的旧逻辑。
- build.gradle 原有 build 依赖 neoForge1211Release，目标仍是 0.1.6 的旧目录；本轮只调用 jar，不触发这个历史 Copy task。发布前修正，但不擅自发布。


## 收藏、关注、我的空间封面及头像补修

收藏和作者作品列表使用 cover 字段，已补兼容；全站卡片和收藏作者缺 avatarUrl 时，按作者 ID 获取公开资料，使用有上限且五分钟过期的缓存。作者作品卡缺作者对象时复用该主页作者资料，不制造头像。

Java21 neoV2Checks -PpublicV2Read jar 通过，共19项；包含收藏/作者卡片封面别名及线上公开封面、头像实际下载和 ImageIO 解码。日志 build/neo-v2-images-20261107.log。最新包 SHA-256：7e795c7967a36a4310895235818fb3cbcc61fbf72b28bda6aef5123dcae47d57。

当前已运行的 Minecraft 窗口不会热更新 Java 类；保存退出并重新启动后再验收封面和头像。未读取玩家登录配置或浏览器令牌。
