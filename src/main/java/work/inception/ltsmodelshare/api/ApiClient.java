package work.inception.ltsmodelshare.api;

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
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(r);
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
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cfg.token = nullToEmpty(resp.payload.token);
                cfg.refreshToken = nullToEmpty(resp.payload.refreshToken);
                cfg.username = username;
                cfg.save();
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail("登录失败：" + e.getMessage());
            }
        });
    }

    /**
     * 静默续期：用 refreshToken 换新的 access token（滑动窗口，服务端每次轮换）。
     *
     * <p>服务端方案：access 30 分钟、refresh 2 小时且**每次续期重新计时**，
     * 所以只要两小时内用过一次（打开面板/取结构都算），就一直不用重新登录。</p>
     *
     * @return true = 续期成功（cfg 里的 token/refreshToken 已更新并落盘）
     */
    public static boolean refreshBlocking() {
        ModConfig cfg = ModConfig.get();
        if (cfg.refreshToken == null || cfg.refreshToken.isEmpty()) {
            return false;
        }
        try {
            String body = GSON.toJson(new Dtos.RefreshRequest(cfg.refreshToken));
            Dtos.RefreshResp resp = post(cfg.url("/api/account/auth/refresh"), body, null,
                    Dtos.RefreshResp.class);
            if (resp == null || !resp.success || resp.payload == null
                    || resp.payload.token == null || resp.payload.token.isEmpty()) {
                LtsModelShare.LOG.warn("续期失败：{}", describe(resp));
                return false;
            }
            cfg.token = resp.payload.token;
            if (resp.payload.refreshToken != null && !resp.payload.refreshToken.isEmpty()) {
                cfg.refreshToken = resp.payload.refreshToken;
            }
            cfg.save();
            LtsModelShare.LOG.info("登录已续期（access + refresh 都换了新的）");
            return true;
        } catch (Exception e) {
            LtsModelShare.LOG.warn("续期异常：{}", e.toString());
            return false;
        }
    }

    /** 后台线程里静默续期一次（打开面板时调用，顺便把过期时间往后推） */
    public static void refreshQuietly() {
        io(() -> {
            if (ModConfig.get().loggedIn()) {
                refreshBlocking();
            }
        });
    }

    /**
     * 需要登录的请求：401 / 失败时先续期再重试一次。
     */
    private static <T extends Dtos.Resp<?>> T postAuthed(String url, String body, Class<T> type) throws IOException {
        ModConfig cfg = ModConfig.get();
        T resp = post(url, body, cfg.token, type);
        if (needsRefresh(resp) && refreshBlocking()) {
            resp = post(url, body, cfg.token, type);
        }
        return resp;
    }

    /** 响应看起来像「令牌过期/未登录」就给一次续期机会 */
    private static boolean needsRefresh(Dtos.Resp<?> resp) {
        if (resp == null) {
            return true;
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
                if (query.locale == null || query.locale.isEmpty()) query.locale = "zh-CN";
                if (query.size <= 0) query.size = 12;
                if (query.current <= 0) query.current = 1;
                String body = GSON.toJson(query);
                Dtos.CardsResp resp = post(cfg.url("/api/public/work/cards"), body,
                        cfg.token, Dtos.CardsResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail("取列表失败：" + e.getMessage());
            }
        });
    }

    /** 标签列表：POST /api/public/work/tags */
    public static void listTags(final Callback<java.util.List<Dtos.Tag>> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                Dtos.TagsResp resp = post(cfg.url("/api/public/work/tags"), "{}", cfg.token,
                        Dtos.TagsResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail("取标签失败：" + e.getMessage());
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
                Dtos.AuthorsResp resp = post(url, "{}", cfg.token, Dtos.AuthorsResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail("搜作者失败：" + e.getMessage());
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
    /** 作品详情：POST /api/public/work/detail?workId= */
    public static void detail(final long workId, final Callback<Dtos.WorkDetail> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                Dtos.DetailResp resp = post(cfg.url("/api/public/work/detail?workId=" + workId),
                        "{}", cfg.token, Dtos.DetailResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail("取详情失败：" + e.getMessage());
            }
        });
    }

    /** 取下载地址（需要登录）：POST /api/work/download-snbt?itemId=&version= */
    public static void downloadInfo(final long itemId, final String version,
                                    final Callback<Dtos.DownloadInfo> cb) {
        io(() -> {
            try {
                ModConfig cfg = ModConfig.get();
                if (!cfg.loggedIn()) {
                    cb.fail("请先登录（M 打开面板 → 登录）");
                    return;
                }
                String url = cfg.url("/api/work/download-snbt?itemId=" + itemId
                        + (version == null || version.isEmpty() ? "" : "&version=" + version));
                Dtos.DownloadResp resp = postAuthed(url, "{}", Dtos.DownloadResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail("取下载地址失败：" + e.getMessage());
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
                    cb.fail("下载地址为空");
                    return;
                }
                if (url.startsWith("/")) {
                    url = cfg.baseUrl + url;
                }
                HttpURLConnection conn = open(url, "GET", null, null);
                int code = conn.getResponseCode();
                if (code / 100 != 2) {
                    cb.fail("取结构失败：HTTP " + code);
                    return;
                }
                String text;
                try (InputStream in = conn.getInputStream()) {
                    text = readAll(in);
                } finally {
                    conn.disconnect();
                }
                if (text == null || text.trim().isEmpty()) {
                    cb.fail("服务器返回了空内容");
                    return;
                }
                cb.ok(text);
            } catch (Exception e) {
                cb.fail("取结构失败：" + e.getMessage());
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
                    cb.fail("请先登录");
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

                HttpURLConnection conn = (HttpURLConnection) new URL(cfg.url("/api/studio/work/upload-snbt"))
                        .openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(120000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "ltsmodelshare-mod/0.1.0");
                if (!cfg.token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + cfg.token);
                }
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(head);
                    out.write(body);
                    out.write(tail);
                }
                int code = conn.getResponseCode();
                String respText = readAll(code / 100 == 2 ? conn.getInputStream() : conn.getErrorStream());
                conn.disconnect();

                Dtos.SnbtUploadResp resp = respText == null || respText.trim().isEmpty() ? null
                        : GSON.fromJson(respText, Dtos.SnbtUploadResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail("上传失败：" + e.getMessage());
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
                    cb.fail("请先登录");
                    return;
                }
                if (file == null || !file.isFile()) {
                    cb.fail("文件不存在");
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

                HttpURLConnection conn = (HttpURLConnection) new URL(cfg.url("/api/studio/work/upload-image"))
                        .openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "ltsmodelshare-mod/0.1.0");
                conn.setRequestProperty("Authorization", "Bearer " + cfg.token);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(head);
                    out.write(data);
                    out.write(tail);
                }
                int code = conn.getResponseCode();
                String respText = readAll(code / 100 == 2 ? conn.getInputStream() : conn.getErrorStream());
                conn.disconnect();

                Dtos.ImageUploadResp resp = respText == null || respText.trim().isEmpty() ? null
                        : GSON.fromJson(respText, Dtos.ImageUploadResp.class);
                if (resp == null || !resp.success || resp.payload == null) {
                    cb.fail(describe(resp));
                    return;
                }
                cb.ok(resp.payload);
            } catch (Exception e) {
                cb.fail("上传截图失败：" + e.getMessage());
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
                cb.fail("保存失败：" + e.getMessage());
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
                cb.fail("授权失败：" + e.getMessage());
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
                cb.fail("提交审核失败：" + e.getMessage());
            }
        });
    }
    // ==================== 底层 ====================

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
        if (resp == null) return "响应解析失败（服务器返回了非 JSON？）";
        String m = resp.error_message == null || resp.error_message.isEmpty() ? "请求失败" : resp.error_message;
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
