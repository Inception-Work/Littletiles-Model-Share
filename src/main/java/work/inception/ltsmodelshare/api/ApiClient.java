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
                Dtos.DownloadResp resp = post(url, "{}", cfg.token, Dtos.DownloadResp.class);
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
