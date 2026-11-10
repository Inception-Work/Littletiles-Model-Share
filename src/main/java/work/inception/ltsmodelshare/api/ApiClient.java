package work.inception.ltsmodelshare.api;

import static work.inception.ltsmodelshare.api.V2Contract.*;

import com.google.gson.*;
import work.inception.ltsmodelshare.ModConfig;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * V2 pre-integration for Forge 1.12.2. Public signatures preserve the tested GUI.
 * Only the HTTP/DTO boundary changes; no fallback writes to V1.
 */
public final class ApiClient {
    /** Package-private seam lets integration tests use an in-memory session without a game config. */
    interface Environment {
        ModConfig config();
        String locale();
        String translate(String key, Object... arguments);
        default void loginRequired(String base) { }
    }
    static volatile Environment environment = new Environment() {
        public ModConfig config() { return ModConfig.get(); }
        public void loginRequired(String base) { work.inception.ltsmodelshare.client.SessionLogin.required(base); }
        public String locale() { return work.inception.ltsmodelshare.client.Translations.siteLocale(); }
        public String translate(String key, Object... arguments) {
            return work.inception.ltsmodelshare.client.Translations.tr(key, arguments);
        }
    };
    private static String siteLocale() { return environment.locale(); }
    private static String tr(String key, Object... arguments) { return environment.translate(key, arguments); }
    private static final V2Http HTTP = new V2Http();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ltsmodelshare-http"); t.setDaemon(true); return t;
    });
    private static final long JSON_LIMIT = 4L * 1024 * 1024;
    private static State state = new State("");
    private static volatile boolean refreshRejected;
    private static final SessionRequests.Session SESSION = new SessionRequests.Session() {
        public String base() { return environment.config().baseUrl; }
        public String token() { return environment.config().token; }
        public SessionRequests.Refresh refresh() {
            return refreshBlocking() ? SessionRequests.Refresh.UPDATED
                    : refreshRejected ? SessionRequests.Refresh.REJECTED : SessionRequests.Refresh.UNAVAILABLE;
        }
        public void expired(String base, String token) {
            ModConfig cfg = environment.config();
            if (!java.util.Objects.equals(base, cfg.baseUrl) || !java.util.Objects.equals(token, cfg.token)) return;
            cfg.clearSession();
            environment.loginRequired(base);
        }
    };


    private static final ThreadLocal<String> taskIdentity = new ThreadLocal<>();
    private static void checkTask() throws IOException {
        String expected = taskIdentity.get();
        if (expected != null) unchanged(expected);
    }
    private ApiClient() { }

    public static void post(Runnable r) { net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(r); }
    public static void io(Runnable r) { POOL.execute(r); }
    public interface Callback<T> { void ok(T value); void fail(String message); }
    private interface Task<T> { T get() throws Exception; }
    private static <T> void run(Callback<T> cb, Task<T> task) {
        final String requestedIdentity = identity();
        io(() -> {
            taskIdentity.set(requestedIdentity);
            try { unchanged(requestedIdentity); syncState(); cb.ok(task.get()); }
            catch (Exception e) {
                // Do not log URLs, bodies, credentials or signed capabilities.
                cb.fail(e instanceof Failure ? e.getMessage()
                        : tr("ltsmodelshare.common.action_failed", safeMessage(e)));
            } finally { taskIdentity.remove(); }
        });
    }
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        String code = message == null ? "" : message.split(":", 2)[0];
        if (code.matches("V2_[A-Z_]+")) return V2Errors.message(code, "CLIENT_ERROR", null, null, siteLocale());
        // Exceptions can contain credential-bearing URLs. Never display arbitrary transport text.
        return V2Errors.message("V2_NETWORK_ERROR", "CLIENT_ERROR", null, null, siteLocale());
    }
    private static final class Failure extends IOException {
        final int status;
        Failure(int status, String code, String message) {
            this(status, code, null, message, null);
        }
        Failure(int status, String code, String type, String message, JsonObject parameters) {
            super(V2Errors.message(code, type, message, parameters, siteLocale()));
            this.status = status;
        }
    }
    private static <K,V> Map<K,V> bounded() {
        return new LinkedHashMap<K,V>(32, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest) { return size() > 256; }
        };
    }
    private static final class Access {
        final long work; final String code; final String grant; final long expires;
        Access(long work, String code, String grant) {
            this.work = work; this.code = code; this.grant = grant;
            expires = System.currentTimeMillis() + 100_000L;
        }
    }
    private static final class Pending {
        final long work, version; Long receipt;
        Pending(long work, long version) { this.work = work; this.version = version; }
    }
    private static final class State {
        final String identity;
        final Map<Long,Access> access = bounded();
        final Map<Long,Long> itemWorks = bounded();
        final Map<String,Access> assets = bounded();
        final Map<String,Long> imageIds = bounded();
        final Map<Long,Pending> pending = bounded();
        Pending lastSaved;
        State(String identity) { this.identity = identity; }
    }
    /** A stable JWT session ID survives refresh; PAT identity is a non-reversible digest. */
    private static String identity() {
        ModConfig cfg = environment.config();
        String token = cfg.token == null ? "" : cfg.token;
        String session;
        try {
            String[] parts = token.split("\\.");
            JsonObject claims = object(new JsonParser().parse(new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)));
            String sid = text(claims, "sid"), sub = text(claims, "sub");
            if (sid == null || sub == null) throw new IllegalArgumentException();
            session = sub + ":" + sid;
        } catch (Exception notJwt) {
            try { session = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8))); }
            catch (Exception impossible) { throw new IllegalStateException(impossible); }
        }
        return cfg.baseUrl + "|" + session;
    }
    private static void syncState() {
        String current = identity();
        if (!current.equals(state.identity)) state = new State(current);
    }
    private static void unchanged(String expected) throws IOException {
        if (!expected.equals(identity())) throw new IOException("V2_SESSION_CHANGED: 会话已切换，请重试");
    }

    private static JsonElement payload(V2Http.Response response) throws IOException {
        JsonObject envelope;
        try { envelope = object(new JsonParser().parse(response.text())); }
        catch (Exception malformed) { throw new Failure(response.status, "V2_INVALID_RESPONSE", "网站返回了非预期响应"); }
        if (response.status / 100 != 2 || !flag(envelope, "success")) {
            String code = text(envelope, "error_code");
            if (code == null) code = text(envelope, "code");
            String message = text(envelope, "error_message");
            if (message == null) message = text(envelope, "message");
            throw new Failure(response.status, code == null ? "HTTP_" + response.status : code,
                    text(envelope, "error_type"), message,
                    has(envelope, "error_params") && envelope.get("error_params").isJsonObject()
                            ? envelope.getAsJsonObject("error_params") : null);
        }
        if (!has(envelope, "payload")) throw new Failure(response.status, "V2_INVALID_RESPONSE", "网站缺少响应数据");
        return envelope.get("payload");
    }
    private static V2Http.Response raw(String base, String path, String method, JsonElement body, String token) throws IOException {
        byte[] bytes = body == null ? null : JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        return HTTP.request(base, path, method, bytes, "application/json; charset=utf-8", token, 20000, JSON_LIMIT, siteLocale());
    }
    private static JsonElement request(String method, String path, JsonElement body) throws IOException {
        checkTask();
        V2Http.Response response = SessionRequests.execute(SESSION,
                (base, token) -> raw(base, path, method, body, token),
                r -> credentialStatus(r), environment.config().loggedIn());
        return payload(response);
    }
    private static int credentialStatus(V2Http.Response response) {
        try { return V2Http.credentialTarget(environment.config().baseUrl, response.finalUrl) ? response.status : 0; }
        catch (IOException invalid) { return 0; }
    }
    public static void validateSession() {
        if (!environment.config().loggedIn()) return;
        resolveUserId(new Callback<Long>() {
            public void ok(Long id) { }
            public void fail(String message) { }
        });
    }
    public static byte[] imageBytes(String base, String path, String token) throws IOException {
        ModConfig cfg = environment.config();
        if (!Objects.equals(base, cfg.baseUrl) || !Objects.equals(token, cfg.token)) throw new IOException("V2_SESSION_CHANGED");
        V2Http.Response response = SessionRequests.execute(SESSION,
                (site, credential) -> HTTP.request(site,path,"GET",null,null,credential,12000,8L*1024*1024),
                r -> credentialStatus(r), environment.config().loggedIn());
        if (response.status/100 != 2) throw new IOException("Image HTTP " + response.status);
        return response.bytes;
    }
    private static JsonObject get(String path) throws IOException { return object(request("GET", path, null)); }
    private static void requireLogin() throws IOException {
        if (!environment.config().loggedIn()) {
            environment.loginRequired(environment.config().baseUrl);
            throw new Failure(401, "S00-401", tr("ltsmodelshare.api.login_required"));
        }
    }

    /** Public state endpoints return anonymous false for expired JWTs; validate identity before reading them. */
    private static void requireAuthenticated() throws IOException {
        requireLogin();
        positive(get("/api/v2/auth/me"), "id");
    }
    public static void login(String username, String password, Callback<Dtos.LoginPayload> cb) {
        run(cb, () -> {
            ModConfig cfg = environment.config();
            String expected = identity();
            JsonObject p = object(payload(raw(cfg.baseUrl, "/api/v2/auth/login", "POST",
                    JSON.toJsonTree(new Dtos.LoginRequest(username, password)), null)));
            unchanged(expected);
            String access = text(p, "accessToken"), refresh = text(p, "refreshToken");
            if (access == null || access.isEmpty() || refresh == null || refresh.isEmpty())
                throw new IOException("V2_INVALID_RESPONSE: 登录缺少双令牌");
            cfg.token = access; cfg.refreshToken = refresh; cfg.username = username; cfg.save();
            syncState();
            Dtos.LoginPayload out = new Dtos.LoginPayload(); out.token = access; out.refreshToken = refresh; return out;
        });
    }
    public static boolean refreshBlocking() {
        ModConfig cfg = environment.config();
        String previous = cfg.refreshToken, token = cfg.token, base = cfg.baseUrl;
        refreshRejected = false;
        if (previous == null || previous.isEmpty()) { refreshRejected = true; return false; }
        try {
            V2Http.Response response = raw(base, "/api/v2/auth/refresh", "POST", body("refreshToken", previous), null);
            refreshRejected = response.status == 400 || response.status == 401 || response.status == 403 || response.status == 410;
            JsonObject p = object(payload(response));
            String access = text(p, "accessToken"), refresh = text(p, "refreshToken");
            if (access == null || access.isEmpty() || refresh == null || refresh.isEmpty()
                    || !previous.equals(cfg.refreshToken) || !token.equals(cfg.token) || !base.equals(cfg.baseUrl)) return false;
            cfg.token = access; cfg.refreshToken = refresh; cfg.save(); return true;
        } catch (Exception failed) { return false; }
    }
    /** Capture credentials before the existing GUI immediately clears local configuration. */
    public static void revokeRefreshQuietly(String refreshToken) {
        ModConfig cfg = environment.config();
        final String token = cfg.token, base = cfg.baseUrl, refresh = refreshToken;
        if (token == null || token.isEmpty() || token.startsWith("lts_")) return; // local PAT exit is not revocation
        io(() -> {
            try {
                V2Http.Response response = raw(base, "/api/v2/auth/logout", "POST", new JsonObject(), token);
                if (response.status == 401 && refresh != null && !refresh.isEmpty()) {
                    JsonObject p = object(payload(raw(base, "/api/v2/auth/refresh", "POST", body("refreshToken", refresh), null)));
                    String access = text(p, "accessToken");
                    if (access != null && !access.isEmpty()) raw(base, "/api/v2/auth/logout", "POST", new JsonObject(), access);
                }
            } catch (Exception ignored) { /* local exit must succeed; never restore revoked credentials */ }
        });
    }
    public static void resolveUserId(Callback<Long> cb) {
        run(cb, () -> { requireLogin(); return positive(get("/api/v2/auth/me"), "id"); });
    }
    public static void accountLocale(Callback<String> cb) {
        run(cb, () -> {
            if (!environment.config().loggedIn()) return null;
            return text(get("/api/v2/auth/me"), "locale");
        });
    }
    public static void listCards(Dtos.CardQuery query, Callback<Dtos.Page<Dtos.WorkCard>> cb) {
        run(cb, () -> cards(request("GET", cardsPath(query), null)));
    }
    public static void authorHome(long userId, String keyword, boolean exact, String locale,
                                  int current, int size, Callback<Dtos.AuthorHome> cb) {
        authorHome(userId, keyword, exact, locale, null, null, current, size, cb);
    }
    public static void authorHome(long userId, String keyword, boolean exact, String locale, String kind, String sort,
                                  int current, int size, Callback<Dtos.AuthorHome> cb) {
        run(cb, () -> {
            
            Dtos.AuthorHome out = new Dtos.AuthorHome();
            out.author = author(get("/api/v2/authors/" + userId));
            String path = param("/api/v2/authors/" + userId + "/works", "q", keyword);
            path = param(path, "locale", locale);
            path = param(path, "exact", exact); path = param(path, "kind", kind); path = param(path, "sort", sort);
            path = param(path, "page", Math.max(1, current)); path = param(path, "size", Math.max(1, Math.min(size,100)));
            out.works = cards(request("GET", path, null)); return out;
        });
    }
    /** Own published library: all visibilities and both kinds, never the public author list. */
    public static void myWorks(Callback<Dtos.AuthorHome> cb) {
        run(cb, () -> {
            requireAuthenticated();
            long id = positive(get("/api/v2/auth/me"), "id");
            Dtos.AuthorHome out = new Dtos.AuthorHome();
            out.author = author(get("/api/v2/authors/" + id));
            Dtos.Page<Dtos.WorkCard> merged = new Dtos.Page<>();
            merged.records = new ArrayList<>(); merged.current = 1;
            for (int page = 1; page <= 100; page++) {
                Dtos.Page<Dtos.WorkCard> part = cards(request("GET", libraryPath(page, siteLocale()), null));
                merged.records.addAll(part.records); merged.total = part.total;
                if (merged.records.size() >= part.total || part.records.isEmpty()) {
                    merged.size = merged.records.size(); out.works = merged; return out;
                }
            }
            throw new IOException("V2_LIBRARY_TOO_LARGE: Please use the website for this library");
        });
    }

    public static void favorites(String kind, int current, int size, Callback<Dtos.Page<Dtos.WorkCard>> cb) {
        run(cb, () -> {
            requireLogin(); long id = positive(get("/api/v2/auth/me"), "id");
            String path = param(param("/api/v2/authors/" + id + "/bookmarks", "kind", kind), "locale", siteLocale());
            path = param(path, "page", Math.max(1, current)); path = param(path, "size", Math.max(1,Math.min(size,48)));
            return cards(request("GET", path, null));
        });
    }
    public static void followingUsers(long userId, int current, int size, Callback<Dtos.Page<Dtos.UserCard>> cb) {
        run(cb, () -> {
            requireAuthenticated();
            JsonObject p = get(param(param("/api/v2/authors/" + userId + "/following",
                    "page", Math.max(1,current)), "size", Math.max(1,Math.min(size,50))));
            if (flag(p,"hidden")) throw new IOException("V2_LIST_HIDDEN: 作者未公开关注列表");
            Dtos.Page<Dtos.UserCard> out = new Dtos.Page<>();
            out.current = number(p,"current",1); out.total = number(p,"total",0); out.size = number(p,"size",size);
            out.records = new ArrayList<>();
            if (!has(p,"records")) throw new IOException("V2_INVALID_RESPONSE: missing records");
            for (JsonElement e : p.getAsJsonArray("records")) {
                Dtos.Author a = author(object(e));
                Dtos.UserCard u = new Dtos.UserCard(); u.userId = a.userId; u.username = a.username;
                u.displayName = a.displayName; u.avatarUrl = a.avatarUrl; u.bio = a.bio; u.followerCount = a.followerCount;
                out.records.add(u);
            }
            return out;
        });
    }
    public static void followingFeed(int page, int size, Callback<Dtos.Page<Dtos.WorkCard>> cb) {
        run(cb, () -> {
            requireAuthenticated();
            String path = param(param(param("/api/v2/works/following", "page", Math.max(1,page)),
                    "size", Math.max(1,Math.min(size,100))), "locale", siteLocale());
            return cards(request("GET", path, null));
        });
    }

    public static void followingState(long userId, Callback<Boolean> cb) {
        run(cb, () -> {
            if (!environment.config().loggedIn()) return false;
            return flag(get("/api/v2/accounts/" + userId + "/follow-state"), "following");
        });
    }
    public static void toggleFollow(long userId, Callback<Dtos.SocialToggle> cb) {
        run(cb, () -> {
            requireAuthenticated();
            JsonObject current = get("/api/v2/accounts/" + userId + "/follow-state");
            if (!has(current,"following")) throw new IOException("V2_STATE_UNKNOWN: 无法读取关注状态");
            return JSON.fromJson(request("POST","/api/v2/accounts/" + userId + "/follow",
                    body("value", !flag(current,"following"))), Dtos.SocialToggle.class);
        });
    }
    public static void toggleFavorite(long workId, Callback<Dtos.SocialToggle> cb) {
        run(cb, () -> {
            requireAuthenticated();
            Access a = access(workId, null, false);
            JsonObject current = get(withGrant("/api/v2/works/" + workId, a));
            if (!has(current,"favorited")) throw new IOException("V2_STATE_UNKNOWN: 无法读取收藏状态");
            return JSON.fromJson(request("POST",withGrant("/api/v2/works/" + workId + "/favorite", a),
                    body("value", !flag(current,"favorited"))), Dtos.SocialToggle.class);
        });
    }
    public static void listTags(Callback<List<Dtos.Tag>> cb) {
        run(cb, () -> tags(request("GET","/api/v2/tags",null)));
    }
    public static void searchAuthors(String keyword, Callback<List<Dtos.Author>> cb) {
        run(cb, () -> {
            List<Dtos.Author> out = new ArrayList<>();
            JsonElement p = request("GET",param(param("/api/v2/authors", "q", keyword == null ? "" : keyword.trim()),
                    "exact", false),null);
            for (JsonElement e : p.getAsJsonArray()) out.add(author(object(e)));
            return out;
        });
    }

    private static String withGrant(String path, Access a) { return a == null ? path : param(path,"access",a.grant); }
    private static Dtos.WorkDetail detailBlocking(long workId, String code) throws IOException {
        
        JsonObject p = code == null || code.trim().isEmpty()
                ? get(param("/api/v2/works/" + workId, "locale", siteLocale()))
                : object(request("POST",param("/api/v2/works/" + workId + "/access","locale",siteLocale()), body("code",code.trim())));
        Dtos.WorkDetail d = V2Contract.detail(p);
        if (d.workId != workId) throw new IOException("V2_INVALID_RESPONSE: work ID mismatch");
        Access a = has(p,"accessGrant") ? new Access(workId,code,text(p,"accessGrant")) : null;
        if (a == null) state.access.remove(workId); else state.access.put(workId,a);
        if (d.items != null) for (Dtos.Item i : d.items) {
            state.itemWorks.put(i.itemId, workId);
            if (i.preview != null && i.preview.assetUrl != null && a != null)
                state.assets.put(V2Http.resolve(environment.config().baseUrl,i.preview.assetUrl).toExternalForm(),a);
        }
        return d;
    }
    /** Re-exchange the original code when a capability expires; never treat it as an account token. */
    private static Access access(long work, String code, boolean force) throws IOException {
        Access cached = state.access.get(work);
        boolean changedCode = code != null && (cached == null || !Objects.equals(code,cached.code));
        if (force || changedCode || (cached != null && cached.expires <= System.currentTimeMillis()))
            detailBlocking(work, code != null ? code : cached == null ? null : cached.code);
        return state.access.get(work);
    }
    public static void recordWorkEvent(long workId, Long itemId, String eventId, Callback<Dtos.WorkStats> cb) {
        run(cb, () -> {
            JsonObject input = body("eventId", eventId);
            if (itemId != null) input.addProperty("itemId", itemId);
            Access a = access(workId, null, false);
            String path = "/api/v2/works/" + workId + (itemId == null ? "/views" : "/downloads");
            for (int attempt = 0; ; attempt++) {
                try { return JSON.fromJson(request("POST", withGrant(path, a), input), Dtos.WorkStats.class); }
                catch (IOException error) {
                    if (attempt == 1) throw error;
                    if (error instanceof Failure && ((Failure)error).status == 410) a = access(workId, null, true);
                }
            }
        });
    }

    public static void detail(long workId, Callback<Dtos.WorkDetail> cb) { detail(workId,null,cb); }
    public static void detail(long workId, String code, Callback<Dtos.WorkDetail> cb) {
        run(cb, () -> detailBlocking(workId,code));
    }
    public static void detailCard(long workId, String code, Callback<Dtos.WorkCard> cb) {
        run(cb, () -> {
            Dtos.WorkDetail d = detailBlocking(workId,code);
            Dtos.WorkCard c = new Dtos.WorkCard(); c.workId=d.workId; c.kind=d.kind; c.title=d.title;
            c.summary=d.summary; c.author=d.author; c.tags=d.tags; c.viewCount=d.viewCount; c.downloadCount=d.downloadCount;
            c.cover=d.images.isEmpty()?null:d.images.get(0); return c;
        });
    }
    public static void downloadInfo(long itemId, String version, Callback<Dtos.DownloadInfo> cb) {
        downloadInfo(itemId,version,null,cb);
    }
    public static void downloadInfo(long itemId, String version, String code, Callback<Dtos.DownloadInfo> cb) {
        run(cb, () -> {
            Long work = state.itemWorks.get(itemId);
            if (code != null && !code.trim().isEmpty() && work == null)
                throw new IOException("V2_DETAIL_REQUIRED: 请先打开邀请作品详情");
            Access a = work == null ? null : access(work,code,false);
            String path = param("/api/v2/works/download-info/" + itemId,"version",version);
            JsonObject p;
            try { p=get(withGrant(path,a)); }
            catch (Failure expired) {
                if (expired.status != 410 || a == null) throw expired;
                a=access(a.work,a.code,true); p=get(withGrant(path,a));
            }
            work=positive(p,"workId"); state.itemWorks.put(itemId,work);
            Dtos.DownloadInfo out = JSON.fromJson(p,Dtos.DownloadInfo.class);
            if (out.assetUrl == null || out.assetUrl.isEmpty()) throw new IOException("V2_INVALID_RESPONSE: missing assetUrl");
            if (a != null) state.assets.put(V2Http.resolve(environment.config().baseUrl,out.assetUrl).toExternalForm(),a);
            return out;
        });
    }
    private static String replaceGrant(String url, Access a) throws IOException {
        if (a == null || !V2Http.credentialTarget(environment.config().baseUrl,V2Http.resolve(environment.config().baseUrl,url))) return url;
        String cleaned=url.replaceAll("([?&])access=[^&#]*", "$1").replace("?&","?").replaceAll("[?&]$","");
        return param(cleaned,"access",a.grant);
    }
    private static String asset(String rawUrl, long maxBytes) throws IOException {
        checkTask();
        ModConfig cfg=environment.config(); String expected=identity(), token=cfg.token;
        String key=V2Http.resolve(cfg.baseUrl,rawUrl).toExternalForm();
        Access original=state.assets.get(key), a=original;
        if (a != null) a=access(a.work,a.code,false);
        String path=replaceGrant(key,a);
        final String assetPath = path;
        V2Http.Response r = SessionRequests.execute(SESSION,
                (base, credential) -> HTTP.request(base,assetPath,"GET",null,null,credential,20000,maxBytes,siteLocale()),
                response -> credentialStatus(response), environment.config().loggedIn());
        if (r.status==410 && a!=null && V2Http.credentialTarget(cfg.baseUrl,r.finalUrl)) {
            a=access(a.work,a.code,true);
            r=HTTP.request(cfg.baseUrl,replaceGrant(key,a),"GET",null,null,cfg.token,20000,maxBytes,siteLocale());
            unchanged(expected);
        }
        if(r.status/100!=2) {
            // JSON failure bodies use the website's own error, otherwise report only the status.
            try { payload(r); } catch(Failure failure) { throw failure; }
            throw new Failure(r.status,"HTTP_"+r.status,"资源读取失败");
        }
        String text=LegacySnbt.utf8(r.bytes);
        if(text.trim().isEmpty()) throw new IOException("V2_EMPTY_ASSET: 资源内容为空");
        return text;
    }
    public static void fetchText(Dtos.DownloadInfo info, Callback<String> cb) {
        run(cb, () -> {
            if(info==null||info.assetUrl==null) throw new IOException(tr("ltsmodelshare.api.download_empty"));
            String snbt = asset(info.assetUrl,10L*1024*1024);
            LegacySnbt.requireLegacy(snbt);
            long actualBytes = snbt.getBytes(StandardCharsets.UTF_8).length;
            if (info.sizeBytes > 0 && info.sizeBytes != actualBytes)
                throw new IOException("V2_SIZE_MISMATCH");
            info.sizeBytes = actualBytes;
            return snbt;
        });
    }
    public static void fetchObj(String assetUrl, long maxBytes, Callback<String> cb) {
        run(cb, () -> asset(assetUrl,Math.min(maxBytes,8L*1024*1024)));
    }

    private static JsonObject upload(byte[] data, String filename, String mime, String purpose, int timeout) throws IOException {
        checkTask(); requireLogin(); ModConfig cfg=environment.config(); String expected=identity(), token=cfg.token;
        String boundary="----ltsmodelshare"+UUID.randomUUID().toString().replace("-","");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\n"
                +"Content-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data); out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] bytes=out.toByteArray();
        String path=param(param("/api/v2/uploads","purpose",purpose),"module","community");
        V2Http.Response r = SessionRequests.execute(SESSION,
                (base, credential) -> HTTP.request(base,path,"POST",bytes,"multipart/form-data; boundary="+boundary,
                        credential,timeout,JSON_LIMIT,siteLocale()), response -> credentialStatus(response), environment.config().loggedIn());
        return object(payload(r));
    }
    public static void uploadSnbt(String text, String fileNameHint, Callback<Dtos.SnbtUpload> cb) {
        run(cb, () -> {
            if(text==null||text.trim().isEmpty()) throw new IOException("SNBT内容为空");
            byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
            if(bytes.length>10*1024*1024)throw new IOException("SNBT文件超过10MiB");
            String name=(fileNameHint==null?"blueprint":fileNameHint).replaceAll("[^\\w\\-\\u4e00-\\u9fa5]","_");
            if(name.length()>64)name=name.substring(0,64);
            JsonObject p=upload(bytes,name+".txt","text/plain; charset=utf-8","work_snbt",120000);
            Dtos.SnbtUpload out=new Dtos.SnbtUpload();
            out.blobId=positive(p,"resourceId"); // GUI name retained; V2 save expects a resource ID here.
            out.sha256=text(p,"sha256");out.sizeBytes=number(p,"size",bytes.length);out.assetUrl=text(p,"url");
            return out;
        });
    }
    public static void uploadImage(File file, Callback<String> cb) {
        run(cb, () -> {
            if(file==null||!file.isFile())throw new IOException(tr("ltsmodelshare.api.file_missing"));
            if(file.length()>8L*1024*1024)throw new IOException("图片超过8MiB");
            boolean png=file.getName().toLowerCase(Locale.ROOT).endsWith(".png");
            JsonObject p=upload(java.nio.file.Files.readAllBytes(file.toPath()),png?"shot.png":"shot.jpg",
                    png?"image/png":"image/jpeg","work_image",60000);
            long id=positive(p,"resourceId");String url=text(p,"url");
            if(url==null||url.isEmpty())throw new IOException("V2_INVALID_RESPONSE: missing image URL");
            state.imageIds.put(url,id);return url;
        });
    }
    public static void saveWork(Dtos.WorkSaveRequest input, Callback<Long> cb) {
        run(cb, () -> {
            requireLogin();
            JsonObject p=object(request("POST","/api/v2/works/save",saveBody(input,state.imageIds)));
            Pending saved=new Pending(positive(p,"workId"),positive(p,"version"));
            state.pending.put(saved.work,saved);state.lastSaved=saved;return saved.work;
        });
    }
    /**
     * Preserve the old callback chain without fabricating first-time agreement to unseen text.
     * An existing decision for the current text is bound to this just-saved draft.
     */
    public static void agreeUploadRights(Callback<Long> cb) {
        run(cb, () -> {
            requireLogin();Pending p=state.lastSaved;
            if(p==null)throw new IOException("V2_DRAFT_CONTEXT_MISSING: 请先保存当前草稿");
            JsonObject agreement=get(param("/api/v2/agreements/upload_rights","locale",siteLocale()));
            JsonObject input=receiptBody(agreement,p.work,p.version);
            JsonObject receipt=object(request("POST","/api/v2/agreements/"+positive(agreement,"releaseId")+"/consent",input));
            if(number(receipt,"scopeId",0)!=p.work || number(receipt,"scopeVersion",0)!=p.version
                    || !"work_upload".equals(text(receipt,"scopeType")) || !"granted".equals(text(receipt,"decision")))
                throw new IOException("V2_INVALID_RESPONSE: 上传授权回执不匹配");
            p.receipt=positive(receipt,"receiptId");return p.receipt;
        });
    }
    /** {@code consentId} may be null: the site lets a token session submit without the web receipt. */
    public static void submitReview(long workId, boolean keepOnline, Long consentId, Callback<Dtos.SubmitReview> cb) {
        run(cb, () -> {
            requireLogin();Pending p=state.pending.get(workId);
            if(p==null)
                throw new IOException("V2_DRAFT_CONTEXT_MISSING: 草稿上下文缺失，请重新保存后再提交");
            if(consentId!=null && (p.receipt==null||p.receipt.longValue()!=consentId.longValue()))
                throw new IOException("V2_DRAFT_CONTEXT_MISSING: 授权或草稿已变化，请重新确认");
            JsonObject input=body("expectedVersion",p.version);
            input.addProperty("keepOldOnline",keepOnline);
            if(consentId!=null) input.addProperty("consentReceiptId",consentId);
            JsonObject result=object(request("POST","/api/v2/works/"+workId+"/submit",input));
            positive(result,"reviewId");
            state.pending.remove(workId);if(state.lastSaved==p)state.lastSaved=null;
            Dtos.SubmitReview out=new Dtos.SubmitReview();out.submitted=true;return out;
        });
    }
}
