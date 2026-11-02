package work.inception.ltsmodelshare.api;

import static work.inception.ltsmodelshare.client.Translations.tr;
import static work.inception.ltsmodelshare.client.Translations.siteLocale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.ModConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 站点的 HTTP 客户端。
 *
 * <p>只用 JDK 自带的 {@link HttpURLConnection}（1.12.2 自带 gson，不用再引依赖）。
 * 所有请求都跑在**单个后台线程**里 —— Minecraft 的渲染线程不能阻塞；
 * 结果要动界面时用 {@link #post(Runnable)} 切回客户端线程。</p>
 */
public final class ApiClient {

    private static final Gson GSON = new GsonBuilder().create();

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ltsmodelshare-http");
        t.setDaemon(true);
        return t;
    });

    private ApiClient() {
    }

    // ==================== 线程调度 ====================

    /** 切回客户端（渲染）线程 */
    public static void post(Runnable r) {
        net.minecraft.client.Minecraft.getInstance().execute(r);
    }

    /** 后台线程执行 */
    public static void io(Runnable r) {
        POOL.execute(r);
    }

    // ==================== 接口 ====================

    /** 登录：POST /api/account/login/password-login */
    public static void login(final String username, final String password,
                             final Callback<Dtos.LoginPayload> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String body = GSON.toJson(new Dtos.LoginRequest(username, password));
                Dtos.LoginResp resp = post(cfg.url("/api/account/login/password-login"), body,
                        null, Dtos.LoginResp.class);
                if (resp == null || !resp.success || resp.payload == null
                        || resp.payload.token == null || resp.payload.token.isEmpty()
                        || resp.payload.refreshToken == null || resp.payload.refreshToken.isEmpty()) {
                    cb.fail(describe(resp));
                    return;
                }
                cfg.token = resp.payload.token;
                cfg.refreshToken = nullToEmpty(resp.payload.refreshToken);
                cfg.username = username;
                cfg.save();
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.login_failed", e.getMessage()));
            }
        });
    }

    /** 登出时尽力通知网站撤销刷新令牌；本地退出不能依赖网络成功。 */
    public static void revokeRefreshQuietly(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) return;
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                post(cfg.url("/api/account/auth/logout"),
                        GSON.toJson(new Dtos.RefreshRequest(refreshToken)), null, Dtos.Resp.class);
            } catch (Exception error) {
                LtsModelShare.LOG.debug("登出时撤销刷新令牌未成功", error);
            }
        });
    }

    /**
     * 静默续期：用 refreshToken 换新的 access token（滑动窗口，服务端每次轮换）。
     *
     * <p>服务端方案：access 30 分钟、refresh 滑动 2 小时；与网站一致，
     * 只有请求遇到失效 access 才尝试轮换双令牌，打开面板本身不会延长会话。</p>
     *
     * @return true = 续期成功（cfg 里的 token/refreshToken 已更新并落盘）
     */
    public static boolean refreshBlocking() {
        ModConfig cfg = ModConfig.get();
        String previousRefresh = cfg.refreshToken;
        if (previousRefresh == null || previousRefresh.isEmpty()) {
            return false;
        }
        try {
            String body = GSON.toJson(new Dtos.RefreshRequest(previousRefresh));
            Dtos.RefreshResp resp = post(cfg.url("/api/account/auth/refresh"), body, null,
                    Dtos.RefreshResp.class);
            if (resp == null || !resp.success || resp.payload == null
                    || resp.payload.token == null || resp.payload.token.isEmpty()
                    || resp.payload.refreshToken == null || resp.payload.refreshToken.isEmpty()) {
                LtsModelShare.LOG.warn("续期失败：{}", describe(resp));
                return false;
            }
            // 登录或登出可能在网络请求期间发生；不能把旧会话写回新用户的配置。
            if (!previousRefresh.equals(cfg.refreshToken)) return false;
            cfg.token = resp.payload.token;
            cfg.refreshToken = resp.payload.refreshToken;
            cfg.save();
            LtsModelShare.LOG.info("登录已续期（access + refresh 都换了新的）");
            return true;
        } catch (Exception e) {
            LtsModelShare.LOG.warn("续期异常：{}", e.toString());
            return false;
        }
    }

    /**
     * 需要登录的请求：401 / 失败时先续期再重试一次。
     */
    private static <T extends Dtos.Resp<?>> T postAuthed(String url, String body, Class<T> type) throws IOException {
        ModConfig cfg = ModConfig.get();
        String tokenUsed = cfg.token;
        T resp = post(url, body, tokenUsed, type);
        if (!needsRefresh(resp)) return resp;
        if (refreshBlocking()) {
            return post(url, body, cfg.token, type);
        }
        // 网站只在明确的 HTTP 401 且无法续期时清空会话；权限不足与网络错误不能误登出。
        if (resp != null && "401".equals(resp.error_code) && tokenUsed.equals(cfg.token)) {
            cfg.clearSession();
        }
        return resp;
    }

    /** 公共接口也会携带登录令牌：过期时应按网站规则续期后重试。 */
    private static <T extends Dtos.Resp<?>> T postWithSession(String url, String body, Class<T> type) throws IOException {
        ModConfig cfg = ModConfig.get();
        return cfg.loggedIn() ? postAuthed(url, body, type) : post(url, body, null, type);
    }

    /** 响应看起来像「令牌过期/未登录」就给一次续期机会 */
    private static boolean needsRefresh(Dtos.Resp<?> resp) {
        if (resp == null) {
            return false; // 空响应不能证明是认证过期，不为网络/网关异常轮换令牌。
        }
        if (resp.success) {
            return false;
        }
        String code = resp.error_code == null ? "" : resp.error_code.toLowerCase();
        String msg = resp.error_message == null ? "" : resp.error_message;
        return code.contains("a02") || code.contains("401") || code.contains("token")
                || msg.contains("登录") || msg.contains("过期") || msg.contains("token");
    }
    /** 模型列表：POST /api/public/work/cards */
    public static void listCards(final Dtos.CardQuery query, final Callback<Dtos.Page<Dtos.WorkCard>> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (query.sort == null) query.sort = "latest";
                if (query.locale == null || query.locale.isEmpty()) query.locale = siteLocale();
                if (query.size <= 0) query.size = 12;
                if (query.current <= 0) query.current = 1;
                String body = GSON.toJson(query);
                Dtos.CardsResp resp = postWithSession(cfg.url("/api/public/work/cards"), body,
                        Dtos.CardsResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.list_failed", e.getMessage()));
            }
        });
    }

    /** 作者主页：POST /api/public/work/author（需登录，作品固定按 top 排序）。 */
    public static void authorHome(final long userId, final String keyword, final boolean exact,
                                  final String locale, final int current, final int size,
                                  final Callback<Dtos.AuthorHome> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String url = cfg.url("/api/public/work/author?userId=" + userId
                        + "&current=" + current + "&size=" + size + "&exact=" + exact
                        + "&locale=" + encode(locale == null || locale.isEmpty() ? siteLocale() : locale)
                        + (keyword == null || keyword.trim().isEmpty() ? "" : "&q=" + encode(keyword.trim())));
                Dtos.AuthorHomeResp resp = postAuthed(url, "{}", Dtos.AuthorHomeResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.author_failed", e.getMessage()));
            }
        });
    }

    /** 我的收藏：POST /api/studio/work/favorites（kind=collection/single，省略为全部） */
    public static void favorites(final String kind, final int current, final int size,
                                 final Callback<Dtos.Page<Dtos.WorkCard>> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String url = cfg.url("/api/studio/work/favorites?current=" + Math.max(1, current)
                        + "&size=" + Math.max(1, Math.min(size, 48))
                        + (kind == null || kind.isEmpty() ? "" : "&kind=" + encode(kind))
                        + "&locale=" + encode(siteLocale()));
                Dtos.FavoriteResp resp = postAuthed(url, "{}", Dtos.FavoriteResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.list_failed", e.getMessage()));
            }
        });
    }

    /** 关注列表：POST /api/public/work/stats/following（需登录，最多 50 个/页） */
    public static void followingUsers(final long userId, final int current, final int size,
                                      final Callback<Dtos.Page<Dtos.UserCard>> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String url = cfg.url("/api/public/work/stats/following?userId=" + userId
                        + "&current=" + Math.max(1, current)
                        + "&size=" + Math.max(1, Math.min(size, 50)));
                Dtos.FollowingUsersResp resp = postAuthed(url, "{}", Dtos.FollowingUsersResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.list_failed", e.getMessage()));
            }
        });
    }

    /**
     * 关注动态：公开卡片流按更新时间倒序，过滤出已关注作者的作品。
     * 站点目前没有专门的「关注作品流」接口，这里只回看最近若干页，避免逐作者并发请求。
     */
    public static void followingFeed(final java.util.Set<Long> authorIds, final int pages,
                                     final Callback<java.util.List<Dtos.WorkCard>> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                java.util.List<Dtos.WorkCard> out = new java.util.ArrayList<>();
                java.util.Set<Long> seen = new java.util.HashSet<>();
                int max = Math.max(1, Math.min(pages, 8));
                for (int page = 1; page <= max; page++) {
                    Dtos.CardQuery query = new Dtos.CardQuery("", "latest", page, 48, siteLocale());
                    Dtos.CardsResp resp = postWithSession(cfg.url("/api/public/work/cards"),
                            GSON.toJson(query), Dtos.CardsResp.class);
                    if (resp == null || !resp.success || resp.payload == null) break;
                    java.util.List<Dtos.WorkCard> records = resp.payload.records;
                    if (records == null || records.isEmpty()) break;
                    for (Dtos.WorkCard card : records) {
                        if (card != null && card.author != null && authorIds.contains(card.author.userId)
                                && seen.add(card.workId)) {
                            out.add(card);
                        }
                    }
                    if (records.size() < 48) break;
                    if (out.size() >= 192) break;
                }
                cb.ok(out);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.list_failed", e.getMessage()));
            }
        });
    }

    /** 解析当前登录用户 ID：密码登录的 JWT 里带 sub；个人访问令牌回退到按用户名精确搜索。 */
    public static void resolveUserId(final Callback<Long> cb) {
        io(() -> {
            Long fromToken = jwtSubject(ModConfig.get().token);
            if (fromToken != null) {
                cb.ok(fromToken);
                return;
            }
            try {
                ModConfig cfg = ModConfig.get();
                Dtos.UserContextResp resp = postAuthed(cfg.url("/api/account/info"), "{}",
                        Dtos.UserContextResp.class);
                if (resp == null || !resp.success || resp.payload == null || resp.payload.userId <= 0) {
                    cb.fail(tr("ltsmodelshare.api.user_missing"));
                    return;
                }
                cb.ok(resp.payload.userId);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.user_missing"));
            }
        });
    }

    private static Long jwtSubject(String token) {
        try {
            if (token == null) return null;
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            byte[] raw = java.util.Base64.getUrlDecoder().decode(parts[1]);
            com.google.gson.JsonObject json = GSON.fromJson(
                    new String(raw, StandardCharsets.UTF_8), com.google.gson.JsonObject.class);
            if (json == null || !json.has("sub")) return null;
            return Long.parseLong(json.get("sub").getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 收藏 / 取消收藏：POST /api/work/social/favorite?workId=（点一下切一次） */
    public static void toggleFavorite(final long workId, final Callback<Dtos.SocialToggle> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (!cfg.loggedIn()) {
                    cb.fail(tr("ltsmodelshare.api.login_required"));
                    return;
                }
                Dtos.SocialToggleResp resp = postAuthed(
                        cfg.url("/api/work/social/favorite?workId=" + workId), "{}",
                        Dtos.SocialToggleResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.common.action_failed", e.getMessage()));
            }
        });
    }

    /** 关注 / 取消关注：POST /api/work/social/follow?userId= */
    public static void toggleFollow(final long userId, final Callback<Dtos.SocialToggle> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (!cfg.loggedIn()) {
                    cb.fail(tr("ltsmodelshare.api.login_required"));
                    return;
                }
                Dtos.SocialToggleResp resp = postAuthed(
                        cfg.url("/api/work/social/follow?userId=" + userId), "{}",
                        Dtos.SocialToggleResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.common.action_failed", e.getMessage()));
            }
        });
    }

    /** 关注按钮的初始状态：POST /api/public/work/stats?userId= */
    public static void followingState(final long userId, final Callback<Boolean> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (!cfg.loggedIn()) {
                    cb.ok(false);
                    return;
                }
                Dtos.ProfileStatsResp resp = postAuthed(
                        cfg.url("/api/public/work/stats?userId=" + userId), "{}",
                        Dtos.ProfileStatsResp.class);
                cb.ok(resp != null && resp.success && resp.payload != null
                        && Boolean.TRUE.equals(resp.payload.following));
            } catch (Exception e) {
                cb.ok(false);
            }
        });
    }

    /** 账号设置的惯用语言（没设置过返回 null，由调用方回退到游戏语言）。 */
    public static void accountLocale(final Callback<String> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (!cfg.loggedIn()) {
                    cb.ok(null);
                    return;
                }
                Dtos.UserContextResp resp = postAuthed(cfg.url("/api/account/info"), "{}",
                        Dtos.UserContextResp.class);
                cb.ok(resp != null && resp.success && resp.payload != null
                        ? resp.payload.preferredLocale : null);
            } catch (Exception e) {
                cb.ok(null);
            }
        });
    }

    /** 标签列表：POST /api/public/work/tags */
    public static void listTags(final Callback<java.util.List<Dtos.Tag>> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                Dtos.TagsResp resp = postWithSession(cfg.url("/api/public/work/tags"), "{}",
                        Dtos.TagsResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.tags_failed", e.getMessage()));
            }
        });
    }

    /** 搜作者：POST /api/public/work/authors?q=（返回最多若干个候选） */
    public static void searchAuthors(final String keyword, final Callback<java.util.List<Dtos.Author>> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String q = keyword == null ? "" : keyword.trim();
                String url = cfg.url("/api/public/work/authors?q=" + encode(q) + "&exact=false");
                Dtos.AuthorsResp resp = postWithSession(url, "{}", Dtos.AuthorsResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.search_author_failed", e.getMessage()));
            }
        });
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
    /**
     * 作品详情：POST /api/public/work/detail?workId=
     *
     * <p>不带访问码的版本（列表里点进来的公开作品）。unlisted 作品走
     * {@link #detail(long, String, Callback)}，凭邀请入口用。</p>
     */
    public static void detail(final long workId, final Callback<Dtos.WorkDetail> cb) {
        detail(workId, null, cb);
    }

    /**
     * 作品详情（带访问码）：POST /api/public/work/detail?workId=&code=
     *
     * <p>仅 unlisted（凭链接）作品可携访问码：缺码回 M05-030、错码回 M05-031；
     * 作品改成公开后，旧邀请携码请求回 M05-064，公开作品需走不带码的列表入口。</p>
     */
    public static void detail(final long workId, final String code,
                              final Callback<Dtos.WorkDetail> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String tail = code == null || code.trim().isEmpty() ? "" : "&code=" + encode(code.trim());
                Dtos.DetailResp resp = postWithSession(cfg.url("/api/public/work/detail?workId=" + workId + "&locale=" + encode(siteLocale()) + tail),
                        "{}", Dtos.DetailResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.detail_failed", e.getMessage()));
            }
        });
    }

    /**
     * 凭邀请访问：先按 workId + code 取详情，成功后再拼一张 {@link Dtos.WorkCard}。
     *
     * <p>为什么要有这个：详情页 {@code GuiWorkDetail} 的构造参数是卡片（列表点进来那份），
     * 而详情接口给的是 {@link Dtos.WorkDetail} —— 这里补一层转换，不动详情页现有构造。</p>
     */
    public static void detailCard(final long workId, final String code,
                                  final Callback<Dtos.WorkCard> cb) {
        detail(workId, code, new Callback<Dtos.WorkDetail>() {
            @Override
            public void ok(Dtos.WorkDetail value) {
                cb.ok(cardOf(value));
            }

            @Override
            public void fail(String message) {
                cb.fail(message);
            }
        });
    }

    /** 详情 → 卡片：只填详情页显示要用的字段（ID / 标题 / 封面 / 作者 / 标签 / 数据） */
    private static Dtos.WorkCard cardOf(Dtos.WorkDetail d) {
        Dtos.WorkCard card = new Dtos.WorkCard();
        card.kind = d.kind;
        card.workId = d.workId;
        card.title = d.title;
        card.summary = d.summary;
        card.cover = d.images == null || d.images.isEmpty() ? null : d.images.get(0);
        card.tags = d.tags;
        card.author = d.author;
        card.viewCount = d.viewCount;
        card.downloadCount = d.downloadCount;
        return card;
    }

    /** 取下载地址（需要登录）：POST /api/work/download-snbt?itemId=&version= */
    public static void downloadInfo(final long itemId, final String version,
                                    final Callback<Dtos.DownloadInfo> cb) {
        downloadInfo(itemId, version, null, cb);
    }

    /**
     * 取下载地址（带访问码）：POST /api/work/download-snbt?itemId=&version=&code=
     *
     * <p>unlisted 作品设了访问码时，下载接口也要校验（M05-030 缺码 / M05-031 错码）。
     * 公开作品传 null 即可，行为与以前一样。</p>
     */
    public static void downloadInfo(final long itemId, final String version, final String code,
                                    final Callback<Dtos.DownloadInfo> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (!cfg.loggedIn()) {
                    cb.fail(tr("ltsmodelshare.api.login_hint"));
                    return;
                }
                String url = cfg.url("/api/work/download-snbt?itemId=" + itemId
                        + (version == null || version.isEmpty() ? "" : "&version=" + version)
                        + (code == null || code.trim().isEmpty() ? "" : "&code=" + encode(code.trim())));
                Dtos.DownloadResp resp = postAuthed(url, "{}", Dtos.DownloadResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.download_failed", e.getMessage()));
            }
        });
    }

    /**
     * 把下载地址的内容读成**字符串**（SNBT 文本），不落盘。
     *
     * <p>蓝图是直接在内存里生成的（{@code StructureStringUtils.importStructure}），
     * 所以不需要写文件 —— 界面上也不再有「保存到」。</p>
     */
    public static void fetchText(final Dtos.DownloadInfo info, final Callback<String> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String url = info.assetUrl;
                if (url == null || url.isEmpty()) {
                    cb.fail(tr("ltsmodelshare.api.download_empty"));
                    return;
                }
                if (url.startsWith("/")) {
                    url = cfg.baseUrl + url;
                }
                HttpURLConnection conn = open(url, "GET", null, null);
                String text;
                try {
                    AssetHeaders.apply(conn, cfg.baseUrl);
                    int code = conn.getResponseCode();
                    if (code / 100 != 2) {
                        cb.fail(tr("ltsmodelshare.api.structure_http", code));
                        return;
                    }
                    try (InputStream in = conn.getInputStream()) {
                        text = readAll(in);
                    }
                } finally {
                    conn.disconnect();
                }
                if (text == null || text.trim().isEmpty()) {
                    cb.fail(tr("ltsmodelshare.api.empty_response"));
                    return;
                }
                cb.ok(text);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.structure_failed", e.getMessage()));
            }
        });
    }
    /** 读取站点生成的 OBJ 白模，限制响应字节数，防止恶意资源耗尽客户端内存。 */
    public static void fetchObj(final String assetUrl, final long maxBytes, final Callback<String> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (assetUrl == null || assetUrl.trim().isEmpty()) throw new IOException("OBJ URL is empty");
                URL url = new URL(new URL(cfg.baseUrl + "/"), assetUrl.startsWith("//") ? "https:" + assetUrl : assetUrl);
                if (!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol())) {
                    throw new IOException("Unsupported OBJ URL scheme");
                }
                HttpURLConnection conn = open(url.toExternalForm(), "GET", null, null);
                try {
                    AssetHeaders.apply(conn, cfg.baseUrl);
                    if (conn.getResponseCode() / 100 != 2) throw new IOException("OBJ HTTP " + conn.getResponseCode());
                    if (conn.getContentLengthLong() > maxBytes) throw new IOException("OBJ exceeds size limit");
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    try (InputStream stream = conn.getInputStream()) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = stream.read(buffer)) != -1) {
                            if (bytes.size() + (long) n > maxBytes) throw new IOException("OBJ exceeds size limit");
                            bytes.write(buffer, 0, n);
                        }
                    }
                    cb.ok(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception error) {
                cb.fail(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            }
        });
    }

    // ==================== 上传 ====================

    /**
     * 上传 SNBT 文本：{@code POST /api/studio/work/upload-snbt}（multipart，字段名 file）。
     *
     * <p>站点只认 txt 文件，所以把文本包成一个 txt 再传 —— 跟网页端粘贴 SNBT 的做法一致，
     * 哈希/去重/白模生成都在服务端，模组这边不重复实现。</p>
     */
    public static void uploadSnbt(final String text, final String fileNameHint,
                                  final Callback<Dtos.SnbtUpload> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (!cfg.loggedIn()) {
                    cb.fail(tr("ltsmodelshare.api.login_required"));
                    return;
                }
                String boundary = "----ltsmodelshare" + System.currentTimeMillis();
                String name = (fileNameHint == null || fileNameHint.isEmpty() ? "blueprint" : fileNameHint)
                        .replaceAll("[^\\w\\-\\u4e00-\\u9fa5]", "_") + ".txt";

                byte[] head = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\n"
                        + "Content-Type: text/plain; charset=utf-8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                byte[] body = text.getBytes(StandardCharsets.UTF_8);
                byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

                Dtos.SnbtUploadResp resp = multipartWithSession(cfg.url("/api/studio/work/upload-snbt"),
                        boundary, head, body, tail, 120000, Dtos.SnbtUploadResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.upload_failed", e.getMessage()));
            }
        });
    }

    /**
     * 上传一张截图：{@code POST /api/studio/work/upload-image}（multipart，字段名 file）。
     *
     * @return 服务端返回的图片地址（预览档），直接放进 save 的 images 里
     */
    public static void uploadImage(final java.io.File file, final Callback<String> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (!cfg.loggedIn()) {
                    cb.fail(tr("ltsmodelshare.api.login_required"));
                    return;
                }
                if (file == null || !file.isFile()) {
                    cb.fail(tr("ltsmodelshare.api.file_missing"));
                    return;
                }
                String boundary = "----ltsmodelshare" + System.currentTimeMillis();
                byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
                String ext = file.getName().toLowerCase().endsWith(".png") ? "png" : "jpg";

                byte[] head = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"shot." + ext + "\"\r\n"
                        + "Content-Type: " + ("png".equals(ext) ? "image/png" : "image/jpeg") + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8);
                byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

                Dtos.ImageUploadResp resp = multipartWithSession(cfg.url("/api/studio/work/upload-image"),
                        boundary, head, data, tail, 60000, Dtos.ImageUploadResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.image_failed", e.getMessage()));
            }
        });
    }
    /** 保存作品（{@code POST /api/studio/work/save}）→ 返回作品 ID */
    public static void saveWork(final Dtos.WorkSaveRequest request, final Callback<Long> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String body = GSON.toJson(request);
                Dtos.WorkSaveResp resp = postAuthed(cfg.url("/api/studio/work/save"), body,
                        Dtos.WorkSaveResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.save_failed", e.getMessage()));
            }
        });
    }
    /**
     * 同意「上传授权」协议，拿到本次提交要用的 consentId。
     *
     * <p>站点要求：**每次提交审核**都要新拿一个 consentId，不能复用很久以前的同意。</p>
     */
    public static void agreeUploadRights(final Callback<Long> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String body = "{\"consentType\":\"upload_rights\",\"agreed\":true}";
                Dtos.ConsentResp resp = postAuthed(cfg.url("/api/account/notification/consent/agree"),
                        body, Dtos.ConsentResp.class);
                if (resp == null || !resp.success || resp.payload == null
                        || resp.payload.consentId == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload.consentId);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.consent_failed", e.getMessage()));
            }
        });
    }

    /** 提交审核：{@code POST /api/studio/work/submit-review?workId=&keepOnline=&consentId=} */
    public static void submitReview(final long workId, final boolean keepOnline, final long consentId,
                                    final Callback<Dtos.SubmitReview> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                String url = cfg.url("/api/studio/work/submit-review?workId=" + workId
                        + "&keepOnline=" + keepOnline + "&consentId=" + consentId);
                Dtos.SubmitReviewResp resp = postAuthed(url, "{}", Dtos.SubmitReviewResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail(tr("ltsmodelshare.api.review_failed", e.getMessage()));
            }
        });
    }
    // ==================== 底层 ====================

    private static final class MultipartResponse {
        final int status;
        final String text;
        MultipartResponse(int status, String text) { this.status = status; this.text = text; }
    }

    /** 多段上传与网站 authFetch 一样仅在 401 时续期并重试整个请求一次。 */
    private static <T extends Dtos.Resp<?>> T multipartWithSession(String url, String boundary,
            byte[] head, byte[] data, byte[] tail, int timeout, Class<T> type) throws IOException {
        ModConfig cfg = ModConfig.get();
        String tokenUsed = cfg.token;
        MultipartResponse response = multipart(url, boundary, head, data, tail, timeout, tokenUsed);
        if (response.status == 401) {
            if (refreshBlocking()) {
                response = multipart(url, boundary, head, data, tail, timeout, cfg.token);
            } else if (tokenUsed.equals(cfg.token)) {
                cfg.clearSession();
            }
        }
        return response.text == null || response.text.trim().isEmpty()
                ? null : GSON.fromJson(response.text, type);
    }

    private static MultipartResponse multipart(String url, String boundary, byte[] head,
            byte[] data, byte[] tail, int timeout, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(timeout);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "ltsmodelshare-mod/0.1.0");
            if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(head);
                out.write(data);
                out.write(tail);
            }
            int status = conn.getResponseCode();
            return new MultipartResponse(status,
                    readAll(status / 100 == 2 ? conn.getInputStream() : conn.getErrorStream()));
        } finally {
            conn.disconnect();
        }
    }

    private static <T> T post(String url, String body, String token, Class<T> type) throws IOException {
        HttpURLConnection conn = open(url, "POST", body, token);
        int code = conn.getResponseCode();
        InputStream stream = code / 100 == 2 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(stream);
        conn.disconnect();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return GSON.fromJson(text, type);
    }

    private static HttpURLConnection open(String url, String method, String body, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept", "application/json, */*");
        conn.setRequestProperty("User-Agent", "ltsmodelshare-mod/0.1.0");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (Writer w = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(body);
            }
        }
        return conn;
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            BufferedReader br = new BufferedReader(r);
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    /** 把响应壳翻成人话 */
    private static String describe(Dtos.Resp<?> resp) {
        if (resp == null) return tr("ltsmodelshare.api.invalid_response");
        String m = resp.error_message == null || resp.error_message.isEmpty() ? tr("ltsmodelshare.api.request_failed") : resp.error_message;
        return resp.error_code == null || resp.error_code.isEmpty() ? m : m + "（" + resp.error_code + "）";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "model-" + System.currentTimeMillis() + ".snbt" : s;
    }

    /** 简易回调（都在后台线程回调；要动界面请自己包 {@link #post}） */
    public interface Callback<T> {
        void ok(T value);

        void fail(String message);
    }
}
