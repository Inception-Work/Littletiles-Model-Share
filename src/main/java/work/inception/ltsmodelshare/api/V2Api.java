package work.inception.ltsmodelshare.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.ModConfig;

/**
 * V2 wire protocol for the 1.21.1 client.
 *
 * <p>The old build talked to first-version endpoints such as {@code /api/public/work/cards},
 * which the current site no longer serves (404). Everything here maps the V2 responses back into
 * the DTOs the existing screens already understand, so the GUI does not need to change.</p>
 */
final class V2Api {

    private static final java.util.Map<Long,Long> itemWorks=new java.util.HashMap<>();
    private static final java.util.Map<Long,String> workCodes=new java.util.HashMap<>();
    private static final java.util.Map<Long,String> workGrants=new java.util.HashMap<>();
    static void clearContext(){itemWorks.clear();workCodes.clear();workGrants.clear();imageResources.clear();authorProfiles.clear();}
    private static String grantPath(String path,long work){
        String grant=workGrants.get(work);
        return grant==null?path:path+(path.contains("?")?"&":"?")+"access="+encode(grant);
    }
    private record CachedAuthor(Dtos.Author author,long expires) {}
    private static final java.util.Map<Long,CachedAuthor> authorProfiles=new java.util.LinkedHashMap<>(32,.75f,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long,CachedAuthor> entry){return size()>128;}
    };
    private static void fillAvatar(Dtos.WorkCard card){
        if(card.author==null||card.author.userId<=0||card.author.avatarUrl!=null)return;
        long id=card.author.userId;
        CachedAuthor cached=authorProfiles.get(id);
        if(cached==null||cached.expires()<System.currentTimeMillis()){
            try{
                cached=new CachedAuthor(author(get("/authors/"+id)),System.currentTimeMillis()+300000L);
                authorProfiles.put(id,cached);
            }catch(IOException denied){return;} // An optional public avatar must not hide the work list.
        }
        card.author.avatarUrl=cached.author().avatarUrl;
    }
    private static final Gson GSON = new Gson();
    private static final java.util.Map<String,Long> imageResources=new java.util.HashMap<>();
    private static String imageSession="";
    private static void syncImages(){
        ModConfig c=ModConfig.get();String session=c.baseUrl+"|"+c.username;
        if(!session.equals(imageSession)){imageResources.clear();imageSession=session;}
    }
    private static final String LOCALES = "zh-Hans,zh-Hant,en,ja,ko,de,fr";

    private V2Api() {
    }

    // ---------------------------------------------------------------- transport

    /*
     * java.net.http.HttpClient rather than HttpURLConnection: the site's front proxy answers the
     * HTTP/1.1 connection with a bodiless 401 (so login looked like "unreadable response"), while
     * HTTP/2 gets the real JSON error. Minecraft 1.21 runs on Java 21, so the modern client is
     * available.
     */
    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
            .version(java.net.http.HttpClient.Version.HTTP_2)
            .build();

    private static final String USER_AGENT = "ltsmodelshare-neoforge-1.21.1/v2";

    private static JsonObject get(String path) throws IOException {
        return exchange("GET", path, null);
    }

    private static JsonObject post(String path, JsonObject body) throws IOException {
        return exchange("POST", path, body == null ? new JsonObject() : body);
    }

    static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status, String message) { super(message); this.status = status; }
    }

    static JsonObject decode(int status, String source) throws IOException {
        JsonObject envelope=parse(source);
        if(envelope==null)throw new HttpFailure(status,"V2_INVALID_RESPONSE(HTTP "+status+")");
        if(status/100!=2||!flag(envelope,"success")){
            String code=text(envelope,"error_code");
            throw new HttpFailure(status,code==null?"HTTP_"+status:code);
        }
        JsonElement payload=envelope.get("payload");
        if(payload==null||payload.isJsonNull())throw new IOException("V2_MISSING_PAYLOAD");
        if(payload.isJsonObject())return payload.getAsJsonObject();
        JsonObject wrapped=new JsonObject();wrapped.add(payload.isJsonArray()?"items":"value",payload);return wrapped;
    }
    private static JsonObject exchange(String method, String path, JsonObject body) throws IOException {
        boolean authRoute = path.startsWith("/auth/login") || path.startsWith("/auth/refresh") || path.startsWith("/auth/logout");
        byte[] bytes = body == null ? null : GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        V2Transport.Response response = ApiClient.sessionRequest("/api/v2" + path, method, bytes,
                "application/json; charset=utf-8", 4L*1024*1024, !authRoute);
        return decode(response.status(), response.text());
    }

    private static JsonObject parse(String text) {
        try {
            JsonElement element = GSON.fromJson(text, JsonElement.class);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static String text(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        return o.get(key).getAsString();
    }

    private static long number(JsonObject o, String key, long fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return fallback;
        try {
            return o.get(key).getAsLong();
        } catch (RuntimeException notNumber) {
            return fallback;
        }
    }

    private static Long boxedNumber(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            return o.get(key).getAsLong();
        } catch (RuntimeException notNumber) {
            return null;
        }
    }

    private static Integer integer(JsonObject o, String key) {
        Long value = boxedNumber(o, key);
        return value == null ? null : value.intValue();
    }

    private static boolean flag(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    // ---------------------------------------------------------------- mapping

    private static Dtos.Author author(JsonObject o) {
        Dtos.Author author = new Dtos.Author();
        author.userId = number(o, "id", number(o, "userId", number(o,"accountId",0)));
        author.username = text(o, "username");
        author.displayName = text(o, "nickname");
        if (author.displayName == null) author.displayName = text(o, "displayName");
        author.avatarUrl = text(o, "avatarUrl");
        author.bannerUrl = text(o, "bannerUrl");
        author.bio = text(o, "bio");
        author.followingCount = number(o, "followingCount", 0);
        author.followerCount = number(o, "followerCount", 0);
        return author;
    }

    private static List<Dtos.Tag> tags(JsonObject o) {
        List<Dtos.Tag> out = new ArrayList<>();
        if (o == null || !o.has("tags") || !o.get("tags").isJsonArray()) return out;
        for (JsonElement element : o.getAsJsonArray("tags")) {
            Dtos.Tag tag = new Dtos.Tag();
            if (element.isJsonObject()) {
                tag.code = text(element.getAsJsonObject(), "code");
                tag.name = text(element.getAsJsonObject(), "name");
                if(element.getAsJsonObject().has("nameI18n"))tag.nameI18n=GSON.fromJson(element.getAsJsonObject().get("nameI18n"),new com.google.gson.reflect.TypeToken<java.util.Map<String,String>>(){}.getType());
            } else if (element.isJsonPrimitive()) {
                tag.name = element.getAsString();
            }
            if (tag.name == null) tag.name = tag.code;
            out.add(tag);
        }
        return out;
    }

    private static Dtos.WorkCard card(JsonObject o) {
        Dtos.WorkCard card = new Dtos.WorkCard();
        card.workId = number(o, "id", number(o, "workId", 0));
        card.kind = text(o, "kind");
        card.visibility = text(o, "visibility");
        card.title = text(o, "title");
        card.summary = text(o, "summary");
        if (card.summary == null) card.summary = text(o, "description");
        card.cover = text(o, "cover");
        if(card.cover==null)card.cover = text(o, "coverUrl");
        if(card.cover==null&&boxedNumber(o,"coverResourceId")!=null)
            card.cover="/api/v2/resources/"+number(o,"coverResourceId",0)+"/content";
        card.viewCount = number(o, "views", number(o, "viewCount", 0));
        card.downloadCount = number(o, "downloads", number(o, "downloadCount", 0));
        card.publishedAt = text(o, "publishedAt");
        card.sourceLoader = text(o, "sourceLoader");
        card.sourceGameVersion = text(o, "sourceGameVersion");
        card.tags = tags(o);
        if (o.has("author") && o.get("author").isJsonObject()) card.author = author(o.getAsJsonObject("author"));
        else {
            card.author=new Dtos.Author();card.author.userId=number(o,"authorId",0);
            card.author.username=text(o,"authorUsername");card.author.displayName=text(o,"authorName");
            if(boxedNumber(o,"authorAvatarId")!=null)card.author.avatarUrl="/api/v2/resources/"+number(o,"authorAvatarId",0)+"/content";
        }
        return card;
    }

    private static Dtos.Item item(JsonObject o) {
        Dtos.Item item = new Dtos.Item();
        item.itemId = number(o, "id", number(o, "itemId", 0));
        item.title = text(o, "title");
        item.summary = text(o, "summary");
        item.mcVersion = text(o, "mcVersion");
        item.grid = integer(o, "grid");
        item.boxCount = integer(o, "boxCount");
        item.snbtSizeBytes = boxedNumber(o, "sizeBytes");
        item.hasModBlocks = flag(o, "hasModBlocks");
        item.usesOtherTextures = flag(o, "usesOtherTextures");
        item.images = new ArrayList<>();
        if (o.has("images") && o.get("images").isJsonArray()) {
            for (JsonElement element : o.getAsJsonArray("images")) {
                if (element.isJsonPrimitive()) item.images.add(element.getAsString());
                else if (element.isJsonObject() && element.getAsJsonObject().has("url")) {
                    item.images.add(text(element.getAsJsonObject(), "url"));
                }
            }
        }
        if (o.has("preview") && o.get("preview").isJsonObject()) {
            JsonObject preview = o.getAsJsonObject("preview");
            item.preview = new Dtos.Preview();
            item.preview.available = flag(preview, "available");
            item.preview.reason = text(preview, "reason");
            item.preview.assetUrl = text(preview, "assetUrl");
            item.preview.sizeBytes = boxedNumber(preview, "sizeBytes");
            if (item.preview.sizeBytes == null) item.preview.sizeBytes = boxedNumber(o, "meshSizeBytes");
        }
        return item;
    }

    // ---------------------------------------------------------------- auth

    static Dtos.LoginPayload login(String username, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        JsonObject payload = post("/auth/login", body);
        Dtos.LoginPayload login = new Dtos.LoginPayload();
        login.token = text(payload, "accessToken");
        login.refreshToken = text(payload, "refreshToken");
        if (login.token == null || login.token.isEmpty()) throw new IOException("V2_LOGIN_FAILED");
        return login;
    }

    static Dtos.LoginPayload refresh(String refreshToken) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("refreshToken", refreshToken);
        JsonObject payload = post("/auth/refresh", body);
        Dtos.LoginPayload login = new Dtos.LoginPayload();
        login.token = text(payload, "accessToken");
        login.refreshToken = text(payload, "refreshToken");
        if (login.token == null || login.token.isEmpty()) throw new IOException("V2_REFRESH_FAILED");
        return login;
    }

    static void logout(String refreshToken) throws IOException {
        JsonObject body = new JsonObject();
        if (refreshToken != null) body.addProperty("refreshToken", refreshToken);
        post("/auth/logout", body);
    }

    static String accountLocale() throws IOException {
        JsonObject payload = get("/auth/me");
        JsonObject user = payload.has("user") && payload.get("user").isJsonObject()
                ? payload.getAsJsonObject("user") : payload;
        return text(user, "locale") == null ? text(user, "preferredLocale") : text(user, "locale");
    }

    static long accountUserId() throws IOException {
        JsonObject payload = get("/auth/me");
        JsonObject user = payload.has("user") && payload.get("user").isJsonObject()
                ? payload.getAsJsonObject("user") : payload;
        return number(user, "id", number(user, "userId", 0));
    }

    // ---------------------------------------------------------------- author pages

    /** Session-owned published works, including private/unlisted works and collections. */
    static Dtos.WorkStats recordWorkEvent(long workId, Long itemId, String eventId) throws IOException {
        if (workCodes.containsKey(workId)) detail(workId, workCodes.get(workId));
        JsonObject body = new JsonObject();
        body.addProperty("eventId", eventId);
        if (itemId != null) body.addProperty("itemId", itemId);
        String path = "/works/" + workId + (itemId == null ? "/views" : "/downloads");
        return GSON.fromJson(post(grantPath(path, workId), body), Dtos.WorkStats.class);
    }

    static Dtos.AuthorHome myWorks(String locale) throws IOException {
        Dtos.AuthorHome home = new Dtos.AuthorHome();
        home.works = loadLibrary(locale, V2Api::get);
        return home;
    }

    interface LibraryReader {
        JsonObject read(String path) throws IOException;
    }

    static Dtos.Page<Dtos.WorkCard> loadLibrary(String locale, LibraryReader reader) throws IOException {
        Dtos.Page<Dtos.WorkCard> merged = new Dtos.Page<>();
        merged.records = new ArrayList<>();
        merged.current = 1;
        for (int current = 1; current <= 100; current++) {
            String path = "/studio/library?page=" + current + "&size=100&locale="
                    + encode(locale == null || locale.isEmpty() ? "zh-Hans" : locale);
            Dtos.Page<Dtos.WorkCard> part = page(reader.read(path), current, 100);
            merged.records.addAll(part.records);
            merged.total = part.total;
            if (merged.records.size() >= part.total || part.records.isEmpty()) {
                merged.size = merged.records.size();
                return merged;
            }
        }
        throw new IOException("V2_LIBRARY_TOO_LARGE: Please use the website for this library");
    }

    static Dtos.AuthorHome authorHome(long userId, String keyword, boolean exact, String locale,
                                      int page, int size, String kind, String requestedSort) throws IOException {
        JsonObject profile = get("/authors/" + userId);
        StringBuilder path = new StringBuilder("/authors/" + userId + "/works?")
                .append("q=").append(encode(keyword == null ? "" : keyword.trim()))
                .append("&exact=").append(exact)
                .append("&locale=").append(encode(locale == null || locale.isEmpty() ? "zh-Hans" : locale))
                .append("&page=").append(Math.max(1, page))
                .append("&size=").append(Math.max(1, Math.min(size, 100)));
        if(kind!=null)path.append("&kind=").append(encode(kind));
        if(requestedSort!=null)path.append("&sort=").append(encode(requestedSort));
        JsonObject works = get(path.toString());
        Dtos.AuthorHome home = new Dtos.AuthorHome();
        home.author = author(profile);
        home.works = page(works, Math.max(1, page), Math.max(1, size));
        authorProfiles.put(userId,new CachedAuthor(home.author,System.currentTimeMillis()+300000L));
        for(Dtos.WorkCard card:home.works.records){
            if(card.author==null||card.author.userId<=0)card.author=home.author;
        }
        return home;
    }

    private static Dtos.Page<Dtos.WorkCard> page(JsonObject payload, int page, int size) {
        List<Dtos.WorkCard> records = new ArrayList<>();
        JsonArray items = payload.has("items") && payload.get("items").isJsonArray()
                ? payload.getAsJsonArray("items")
                : payload.has("records") && payload.get("records").isJsonArray()
                ? payload.getAsJsonArray("records") : new JsonArray();
        for (JsonElement element : items) {
            if (element.isJsonObject()) {
                Dtos.WorkCard mapped=card(element.getAsJsonObject());
                if(mapped.author!=null&&mapped.author.userId>0)fillAvatar(mapped);
                records.add(mapped);
            }
        }
        Dtos.Page<Dtos.WorkCard> out = new Dtos.Page<>();
        out.records = records;
        out.total = number(payload, "total", records.size());
        out.current = page;
        out.size = size;
        return out;
    }

    static Dtos.Page<Dtos.WorkCard> bookmarks(String kind, int page, int size) throws IOException {
        long me = accountUserId();
        StringBuilder path = new StringBuilder("/authors/" + me + "/bookmarks?")
                .append("page=").append(Math.max(1, page))
                .append("&size=").append(Math.max(1, Math.min(size, 48)));
        if (kind != null && !kind.isEmpty()) path.append("&kind=").append(encode(kind));
        return page(get(path.toString()), Math.max(1, page), Math.max(1, size));
    }

    static Dtos.Page<Dtos.UserCard> followingUsers(long userId, int page, int size) throws IOException {
        JsonObject payload = get("/authors/" + userId + "/following?page=" + Math.max(1, page)
                + "&size=" + Math.max(1, Math.min(size, 50)));
        List<Dtos.UserCard> records = new ArrayList<>();
        JsonArray items = payload.has("items") && payload.get("items").isJsonArray()
                ? payload.getAsJsonArray("items")
                : payload.has("records") && payload.get("records").isJsonArray()
                ? payload.getAsJsonArray("records") : new JsonArray();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (row.has("author") && row.get("author").isJsonObject()) row = row.getAsJsonObject("author");
            Dtos.UserCard card = new Dtos.UserCard();
            card.userId = number(row, "id", number(row, "userId", 0));
            card.username = text(row, "username");
            card.displayName = text(row, "nickname");
            if (card.displayName == null) card.displayName = text(row, "displayName");
            card.avatarUrl = text(row, "avatarUrl");
            card.bio = text(row, "bio");
            card.followerCount = number(row, "followerCount", 0);
            records.add(card);
        }
        Dtos.Page<Dtos.UserCard> out = new Dtos.Page<>();
        out.records = records;
        out.total = number(payload, "total", records.size());
        out.current = Math.max(1, page);
        out.size = Math.max(1, size);
        return out;
    }

    static List<Dtos.Author> searchAuthors(String keyword, boolean exact) throws IOException {
        JsonObject payload = get("/authors?q=" + encode(keyword == null ? "" : keyword.trim())
                + "&exact=" + exact);
        List<Dtos.Author> out = new ArrayList<>();
        JsonArray items = payload.has("items") && payload.get("items").isJsonArray()
                ? payload.getAsJsonArray("items") : new JsonArray();
        for (JsonElement element : items) {
            if (element.isJsonObject()) out.add(author(element.getAsJsonObject()));
        }
        return out;
    }

    // ---------------------------------------------------------------- upload / save

    static Dtos.SnbtUpload uploadSnbt(String text, String fileNameHint) throws IOException {
        JsonObject payload = upload("work_snbt", (fileNameHint==null?"blueprint":fileNameHint)+".snbt", "text/plain; charset=utf-8",
                text.getBytes(StandardCharsets.UTF_8));
        Dtos.SnbtUpload upload = new Dtos.SnbtUpload();
        upload.blobId = number(payload, "resourceId", 0);
        upload.sizeBytes = number(payload, "size", number(payload,"sizeBytes",0));
        upload.sha256 = text(payload, "sha256");
        upload.assetUrl = text(payload, "url");
        return upload;
    }

    static String uploadImage(byte[] data, String fileName, String mime) throws IOException {
        JsonObject payload = upload("work_image", fileName, mime, data);
        String url=text(payload,"url");long id=number(payload,"resourceId",0);
        if(url==null||id<=0)throw new IOException("V2_IMAGE_RESPONSE");
        syncImages();imageResources.put(url,id);return url;
    }

    private static JsonObject upload(String purpose, String fileName, String mime, byte[] bytes) throws IOException {
        ModConfig cfg = ModConfig.get();
        String boundary = "----ltsmodelshare" + System.nanoTime();
        String name = (fileName == null || fileName.isEmpty() ? "upload" : fileName).replaceAll("[\\r\\n\\\"\\\\\\\\]", "_");
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + bytes.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(bytes, 0, body, head.length, bytes.length);
        System.arraycopy(tail, 0, body, head.length + bytes.length, tail.length);

        String path="/api/v2/uploads?purpose="+encode(purpose)+"&module=community";
        V2Transport.Response response = ApiClient.sessionRequest(path, "POST", body,
                "multipart/form-data; boundary="+boundary, 4L*1024*1024, true);
        return decode(response.status(),response.text());
    }

    private static Long resourceIdOf(String url) {
        if (url == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("/resources/([1-9][0-9]*)/content").matcher(url);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    static long save(Dtos.WorkSaveRequest request) throws IOException {
        syncImages();
        JsonObject body = new JsonObject();
        body.addProperty("kind", request.kind == null ? "single" : request.kind);
        body.addProperty("title", request.title);
        body.addProperty("summary", request.summary);
        body.addProperty("visibility", request.visibility == null ? "public" : request.visibility);
        body.addProperty("sourceLoader", request.sourceLoader);
        body.addProperty("sourceGameVersion", request.sourceGameVersion);
        if (request.sourceLocale != null) body.addProperty("sourceLocale", request.sourceLocale);
        JsonArray tags = new JsonArray();
        if (request.tagCodes != null) for (String code : request.tagCodes) tags.add(code);
        body.add("tagCodes", tags);
        JsonArray images = new JsonArray();
        if (request.images != null) {
            for (String url : request.images) {
                Long id = imageResources.get(url);
                if(id==null)throw new IOException("V2_IMAGE_RESOURCE_MISSING");
                images.add(id);
            }
        }
        body.add("imageResourceIds", images);
        JsonArray items = new JsonArray();
        if (request.items != null) {
            for (Dtos.WorkSaveRequest.SaveItem item : request.items) {
                JsonObject row = new JsonObject();
                row.addProperty("title", item.title);
                row.addProperty("summary", item.summary);
                row.addProperty("snbtBlobId", item.snbtBlobId);
                row.addProperty("mcVersion", item.mcVersion);
                if(item.dependencies!=null)row.add("dependencies",GSON.toJsonTree(item.dependencies));
                if (item.hasModBlocks != null) row.addProperty("hasModBlocks", item.hasModBlocks == 1);
                if (item.usesOtherTextures != null) row.addProperty("usesOtherTextures", item.usesOtherTextures == 1);
                items.add(row);
            }
        }
        body.add("items", items);
        JsonObject payload = post("/works/save", body);
        long workId = number(payload, "workId", 0);
        if (workId <= 0) throw new IOException("V2_SAVE_FAILED");
        return workId;
    }

    // ---------------------------------------------------------------- endpoints

    static Dtos.Page<Dtos.WorkCard> cards(Dtos.CardQuery query) throws IOException {
        StringBuilder path = new StringBuilder(query.authorId != null
                ? "/authors/" + query.authorId + "/works" : "/works");
        path.append("?q=").append(encode(query.q))
                .append("&locale=").append(encode(query.locale))
                .append("&page=").append(Math.max(1, query.current))
                .append("&size=").append(Math.max(1, Math.min(query.size, query.authorId != null ? 100 : 50)));
        if (query.authorId == null) {
            if (query.tagCodes != null && !query.tagCodes.isEmpty()) {
                path.append("&tagCodes=").append(encode(String.join(",", query.tagCodes)));
            }
            if (query.sourceLoader != null && !query.sourceLoader.trim().isEmpty()) {
                path.append("&sourceLoader=").append(encode(query.sourceLoader.trim()));
            }
            if (query.sourceGameVersions != null && !query.sourceGameVersions.isEmpty()) {
                path.append("&sourceGameVersions=").append(encode(String.join(",", query.sourceGameVersions)));
            }
            if (query.exact) path.append("&exact=true");
            path.append("&sort=").append(sort(query.sort));
        } else {
            if (query.exact) path.append("&exact=true");
            path.append("&sort=").append(sort(query.sort));
        }
        JsonObject payload = get(path.toString());
        List<Dtos.WorkCard> records = new ArrayList<>();
        JsonArray items = payload.has("items") && payload.get("items").isJsonArray()
                ? payload.getAsJsonArray("items")
                : payload.has("records") && payload.get("records").isJsonArray()
                ? payload.getAsJsonArray("records") : new JsonArray();
        for (JsonElement element : items) {
            if (element.isJsonObject()) {
                Dtos.WorkCard mapped=card(element.getAsJsonObject());
                if(mapped.author!=null&&mapped.author.userId>0)fillAvatar(mapped);
                records.add(mapped);
            }
        }
        Dtos.Page<Dtos.WorkCard> page = new Dtos.Page<>();
        page.records = records;
        page.total = number(payload, "total", records.size());
        page.current = Math.max(1, query.current);
        page.size = Math.max(1, query.size);
        return page;
    }

    static String sort(String raw) {
        if (raw == null || raw.isEmpty() || "latest".equals(raw)) return "newest";
        if ("hot".equals(raw)) return "downloads";
        if ("newest".equals(raw) || "oldest".equals(raw) || "downloads".equals(raw) || "favorites".equals(raw)) {
            return raw;
        }
        return "newest";
    }

    static Dtos.WorkDetail detail(long workId) throws IOException {
        return mapDetail(get("/works/" + workId+"?locale="+encode(work.inception.ltsmodelshare.client.Translations.siteLocale())), workId);
    }

    /** Unlisted works: the access code is exchanged for the same payload shape. */
    static Dtos.WorkDetail detail(long workId, String code) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        JsonObject payload=post("/works/" + workId + "/access?locale="+encode(work.inception.ltsmodelshare.client.Translations.siteLocale()), body);
        workCodes.put(workId,code);
        if(text(payload,"accessGrant")!=null)workGrants.put(workId,text(payload,"accessGrant"));
        return mapDetail(payload,workId);
    }

    private static Dtos.WorkDetail mapDetail(JsonObject payload, long workId) {
        Dtos.WorkDetail detail = new Dtos.WorkDetail();
        detail.workId = number(payload, "id", workId);
        detail.kind = text(payload, "kind");
        detail.title = text(payload, "title");
        detail.summary = text(payload, "description");
        if (detail.summary == null) detail.summary = text(payload, "summary");
        detail.visibility = text(payload, "visibility");
        detail.viewCount = number(payload, "views", number(payload, "viewCount", 0));
        detail.downloadCount = number(payload, "downloads", number(payload, "downloadCount", 0));
        detail.favoriteCount = boxedNumber(payload, "favorites");
        detail.favorited = flag(payload, "favorited");
        detail.tags = tags(payload);
        if (payload.has("author") && payload.get("author").isJsonObject()) {
            detail.author = author(payload.getAsJsonObject("author"));
        }
        detail.images = new ArrayList<>();
        if (payload.has("images") && payload.get("images").isJsonArray()) {
            for (JsonElement element : payload.getAsJsonArray("images")) {
                if (element.isJsonPrimitive()) detail.images.add(element.getAsString());
                else if (element.isJsonObject() && element.getAsJsonObject().has("url")) {
                    detail.images.add(text(element.getAsJsonObject(), "url"));
                }
            }
        }
        detail.items = new ArrayList<>();
        if (payload.has("items") && payload.get("items").isJsonArray()) {
            for (JsonElement element : payload.getAsJsonArray("items")) {
                if (element.isJsonObject()) {
                    Dtos.Item mapped=item(element.getAsJsonObject());
                    detail.items.add(mapped);itemWorks.put(mapped.itemId,workId);
                }
            }
        }
        return detail;
    }

    static Dtos.DownloadInfo downloadInfo(long itemId, String version, String code) throws IOException {
        if(code!=null&&!code.isBlank()){
            Long work=itemWorks.get(itemId);
            if(work==null)throw new IOException("V2_DETAIL_REQUIRED");
            workCodes.put(work,code.trim());
        }
        return downloadInfo(itemId,version);
    }

    static Dtos.DownloadInfo downloadInfo(long itemId, String version) throws IOException {
        String path = "/works/download-info/" + itemId
                + (version == null || version.isEmpty() ? "" : "?version=" + encode(version));
        Long work=itemWorks.get(itemId);
        if(work!=null&&workCodes.containsKey(work))detail(work,workCodes.get(work));
        JsonObject payload = get(work==null?path:grantPath(path,work));
        Dtos.DownloadInfo info = new Dtos.DownloadInfo();
        info.fileName = text(payload, "fileName");
        info.assetUrl = text(payload, "assetUrl");
        info.sizeBytes = number(payload, "sizeBytes", 0);
        info.version = text(payload, "version");
        info.converted = flag(payload, "converted");
        return info;
    }

    static List<Dtos.Tag> allTags() throws IOException {
        JsonObject payload = get("/tags");
        List<Dtos.Tag> out = new ArrayList<>();
        JsonArray array = payload.has("items") && payload.get("items").isJsonArray()
                ? payload.getAsJsonArray("items")
                : payload.has("tags") && payload.get("tags").isJsonArray()
                ? payload.getAsJsonArray("tags") : new JsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject tag = element.getAsJsonObject();
            Dtos.Tag value = new Dtos.Tag();
            value.code = text(tag, "code");
            value.name = text(tag, "name");
            if(tag.has("nameI18n"))value.nameI18n=GSON.fromJson(tag.get("nameI18n"),new com.google.gson.reflect.TypeToken<java.util.Map<String,String>>(){}.getType());
            out.add(value);
        }
        return out;
    }

    /** The submit endpoint needs the draft version; read it first instead of keeping local state. */
    static boolean submitCurrentDraft(long workId) throws IOException {
        JsonObject draft = get("/works/" + workId + "/draft");
        long version = number(draft, "version", 0);
        if (version <= 0) throw new IOException("V2_DRAFT_VERSION_MISSING");
        return submit(workId, version);
    }

    static boolean submit(long workId, long expectedVersion) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("expectedVersion", expectedVersion);
        body.addProperty("keepOldOnline", false);
        JsonObject payload = post("/works/" + workId + "/submit", body);
        return Boolean.TRUE.equals(flag(payload, "autoApproved")) || number(payload, "reviewId", 0) > 0;
    }

    /** V2 uses explicit set semantics, so the current value is read first. */
    static Dtos.SocialToggle toggleFavorite(long workId) throws IOException {
        accountUserId();
        JsonObject detail = get(grantPath("/works/" + workId,workId));
        return favorite(workId, !flag(detail, "favorited"));
    }

    static Dtos.SocialToggle toggleFollow(long userId) throws IOException {
        accountUserId();
        return follow(userId, !followState(userId));
    }

    static Dtos.SocialToggle favorite(long workId, boolean value) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("value", value);
        JsonObject payload = post(grantPath("/works/" + workId + "/favorite",workId), body);
        Dtos.SocialToggle toggle = new Dtos.SocialToggle();
        toggle.favorited = flag(payload, "favorited");
        return toggle;
    }

    static Dtos.SocialToggle follow(long userId, boolean value) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("value", value);
        JsonObject payload = post("/accounts/" + userId + "/follow", body);
        Dtos.SocialToggle toggle = new Dtos.SocialToggle();
        toggle.following = flag(payload, "following");
        toggle.followerCount = boxedNumber(payload, "followerCount");
        toggle.followingCount = boxedNumber(payload, "followingCount");
        return toggle;
    }

    static boolean followState(long userId) throws IOException {
        JsonObject payload = get("/accounts/" + userId + "/follow-state");
        return flag(payload, "following");
    }
}
