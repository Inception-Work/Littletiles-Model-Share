package work.inception.ltsmodelshare.api;

import com.google.gson.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** V2 wire objects -> the existing 1.12 GUI models. No game or network dependency. */
public final class V2Contract {
    public static final Gson JSON = new Gson();
    private V2Contract() { }

    public static JsonObject object(JsonElement e) {
        if (e == null || !e.isJsonObject()) throw new IllegalArgumentException("V2_INVALID_RESPONSE: expected object");
        return e.getAsJsonObject();
    }
    public static boolean has(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }
    public static String text(JsonObject o, String key) {
        return has(o, key) ? o.get(key).getAsString() : null;
    }
    public static long number(JsonObject o, String key, long fallback) {
        return has(o, key) ? o.get(key).getAsLong() : fallback;
    }
    public static boolean flag(JsonObject o, String key) {
        return has(o, key) && o.get(key).getAsBoolean();
    }
    public static long positive(JsonObject o, String key) {
        long n = number(o, key, 0);
        if (n <= 0) throw new IllegalArgumentException("V2_INVALID_RESPONSE: missing " + key);
        return n;
    }
    public static String encode(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    public static String param(String path, String key, Object value) {
        if (value == null) return path;
        return path + (path.contains("?") ? "&" : "?") + encode(key) + "=" + encode(String.valueOf(value));
    }
    public static JsonObject body(String key, Object value) {
        JsonObject o = new JsonObject(); o.add(key, JSON.toJsonTree(value)); return o;
    }

    /** V2 now honours the catalogue filters, so the client sends them instead of refusing. */
    public static String cardsPath(Dtos.CardQuery q) throws IOException {
        boolean byAuthor = q.authorId != null && q.grid == null
                && (q.tagCodes == null || q.tagCodes.isEmpty())
                && (q.sourceLoader == null || q.sourceLoader.trim().isEmpty())
                && (q.sourceGameVersions == null || q.sourceGameVersions.isEmpty());
        String p = byAuthor ? "/api/v2/authors/" + q.authorId + "/works" : "/api/v2/works";
        if (!byAuthor && q.authorId != null) p = param(p, "authorId", q.authorId);
        p = param(p, "q", q.q);
        p = param(p, "locale", q.locale);
        p = param(p, "page", Math.max(1, q.current));
        p = param(p, "size", Math.max(1, Math.min(q.size, byAuthor ? 100 : 50)));
        if (byAuthor) {
            p = param(p, "exact", q.exact);
            p = param(p, "sort", mapSort(q.sort));
            return p;
        }
        if (q.tagCodes != null && !q.tagCodes.isEmpty()) {
            p = param(p, "tagCodes", String.join(",", q.tagCodes));
        }
        if (q.sourceLoader != null && !q.sourceLoader.trim().isEmpty()) {
            p = param(p, "sourceLoader", q.sourceLoader.trim());
        }
        if (q.sourceGameVersions != null && !q.sourceGameVersions.isEmpty()) {
            p = param(p, "sourceGameVersions", String.join(",", q.sourceGameVersions));
        }
        if (q.grid != null) p = param(p, "grid", q.grid);
        if (q.exact) p = param(p, "exact", true);
        p = param(p, "sort", mapSort(q.sort));
        return p;
    }

    /** The GUI uses latest/hot; the API names the same intents newest/downloads. */
    private static String mapSort(String sort) {
        if (sort == null || sort.isEmpty() || "latest".equals(sort)) return "newest";
        if ("hot".equals(sort)) return "downloads";
        if ("newest".equals(sort) || "oldest".equals(sort)
                || "downloads".equals(sort) || "favorites".equals(sort)) return sort;
        return "newest";
    }

    public static Dtos.Author author(JsonObject o) {
        Dtos.Author a = new Dtos.Author();
        a.userId = number(o, "userId", number(o, "id", number(o, "accountId", 0)));
        a.username = text(o, "username");
        a.displayName = has(o, "displayName") ? text(o, "displayName") : text(o, "nickname");
        a.avatarUrl = text(o, "avatarUrl"); a.bannerUrl = text(o, "bannerUrl"); a.bio = text(o, "bio");
        a.preferredLocale = has(o, "preferredLocale") ? text(o, "preferredLocale") : text(o, "locale");
        a.followingCount = number(o, "followingCount", 0); a.followerCount = number(o, "followerCount", 0);
        return a;
    }
    public static List<Dtos.Tag> tags(JsonElement e) {
        List<Dtos.Tag> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        for (JsonElement t : e.getAsJsonArray()) {
            if (t.isJsonNull()) continue;
            Dtos.Tag tag;
            if (t.isJsonObject()) tag = JSON.fromJson(t, Dtos.Tag.class);
            else {
                tag = new Dtos.Tag(); tag.name = t.getAsString();
                // A display name is not a controlled tag code.
            }
            out.add(tag);
        }
        return out;
    }
    public static List<String> images(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        for (JsonElement image : e.getAsJsonArray()) {
            String url = image.isJsonObject() ? text(image.getAsJsonObject(), "url")
                    : image.isJsonNull() ? null : image.getAsString();
            if (url != null && !url.isEmpty()) out.add(url);
        }
        return out;
    }
    public static Dtos.WorkCard card(JsonObject o) {
        Dtos.WorkCard c = new Dtos.WorkCard();
        c.workId = number(o, "workId", number(o, "id", 0));
        if (has(o, "itemId")) c.itemId = o.get("itemId").getAsLong();
        c.kind = text(o, "kind"); c.visibility = text(o, "visibility"); c.title = text(o, "title"); c.summary = text(o, "summary");
        c.cover = has(o, "cover") ? text(o, "cover") : text(o, "coverUrl");
        if (c.cover == null && has(o, "coverResourceId"))
            c.cover = "/api/v2/resources/" + positive(o, "coverResourceId") + "/content";
        if (has(o, "author")) c.author = author(object(o.get("author")));
        else {
            c.author = new Dtos.Author(); c.author.userId = number(o, "authorId", 0);
            c.author.username = text(o, "authorUsername"); c.author.displayName = text(o, "authorName");
            if (has(o, "authorAvatarId"))
                c.author.avatarUrl = "/api/v2/resources/" + positive(o, "authorAvatarId") + "/content";
        }
        c.tags = tags(o.get("tags"));
        c.viewCount = number(o, "viewCount", number(o, "views", 0));
        c.downloadCount = number(o, "downloadCount", number(o, "downloads", 0));
        c.publishedAt = text(o, "publishedAt");
        c.sourceLoader = text(o, "sourceLoader"); c.sourceGameVersion = text(o, "sourceGameVersion");
        return c;
    }
    public static String libraryPath(int page, String locale) {
        return param(param(param("/api/v2/studio/library", "page", Math.max(1, page)), "size", 100), "locale", locale);
    }
    public static Dtos.Page<Dtos.WorkCard> cards(JsonElement value) {
        JsonObject o = object(value);
        if (flag(o, "hidden")) throw new IllegalArgumentException("V2_LIST_HIDDEN: 作者未公开此列表");
        Dtos.Page<Dtos.WorkCard> p = new Dtos.Page<>();
        p.total = number(o, "total", 0); p.current = number(o, "current", number(o, "page", 1));
        p.size = number(o, "size", 12); p.records = new ArrayList<>();
        JsonElement rows = has(o, "records") ? o.get("records") : o.get("items");
        if (rows == null || !rows.isJsonArray()) throw new IllegalArgumentException("V2_INVALID_RESPONSE: missing page records");
        for (JsonElement row : rows.getAsJsonArray()) p.records.add(card(object(row)));
        return p;
    }
    public static Dtos.WorkDetail detail(JsonElement value) {
        JsonObject o = object(value);
        Dtos.WorkDetail d = new Dtos.WorkDetail();
        d.workId = number(o, "workId", number(o, "id", 0));
        d.kind = text(o, "kind"); d.title = text(o, "title");
        d.summary = has(o, "summary") ? text(o, "summary") : text(o, "description");
        d.visibility = text(o, "visibility"); d.author = author(object(o.get("author")));
        d.tags = tags(o.get("tags")); d.images = images(o.get("images"));
        d.viewCount = number(o, "views", number(o, "viewCount", 0));
        d.downloadCount = number(o, "downloads", number(o, "downloadCount", 0));
        d.favoriteCount = number(o, "favorites", number(o, "favoriteCount", 0));
        if (has(o, "commentCount")) d.commentCount = o.get("commentCount").getAsLong();
        d.favorited = flag(o, "favorited");
        if (has(o, "dependencies"))
            d.dependencies = Arrays.asList(JSON.fromJson(o.get("dependencies"), Dtos.Dependency[].class));
        d.items = new ArrayList<>();
        if (has(o, "items")) for (JsonElement e : o.getAsJsonArray("items")) {
            JsonObject row = object(e);
            // Clone before normalizing object images for the old GUI's String[] contract.
            JsonObject normalized = new JsonParser().parse(row.toString()).getAsJsonObject();
            normalized.add("images", JSON.toJsonTree(images(row.get("images"))));
            Dtos.Item i = JSON.fromJson(normalized, Dtos.Item.class);
            i.itemId = number(row, "itemId", number(row, "id", 0));
            if (has(row, "sizeBytes")) i.snbtSizeBytes = row.get("sizeBytes").getAsLong();
            if (i.preview != null && has(row, "meshSizeBytes"))
                i.preview.sizeBytes = row.get("meshSizeBytes").getAsLong();
            d.items.add(i);
        }
        return d;
    }

    /** Resource IDs originate only from upload responses, never from URL guessing. */
    public static JsonObject saveBody(Dtos.WorkSaveRequest request, Map<String, Long> imageIds) throws IOException {
        JsonObject body = JSON.toJsonTree(request).getAsJsonObject();
        body.remove("images");
        JsonArray ids = new JsonArray();
        if (request.images != null) for (String url : request.images) {
            Long id = imageIds.get(url);
            if (id == null || id <= 0) throw new IOException("V2_IMAGE_RESOURCE_MISSING: 请重新上传截图");
            ids.add(id);
        }
        body.add("imageResourceIds", ids);
        if (body.has("items")) for (JsonElement e : body.getAsJsonArray("items")) {
            JsonObject item = e.getAsJsonObject();
            for (String key : new String[]{"hasModBlocks", "usesOtherTextures"})
                if (has(item, key)) item.addProperty(key, item.get(key).getAsInt() != 0);
        }
        return body;
    }

    /** Binding a receipt requires a prior user decision for this exact published text. */
    public static JsonObject receiptBody(JsonObject agreement, long workId, long version) throws IOException {
        if (!flag(agreement, "consented") || flag(agreement, "consentInherited")
                || number(agreement, "consentTextId", 0) != number(agreement, "textId", -1)
                || number(agreement, "consentReleaseId", 0) != number(agreement, "releaseId", -1)) {
            throw new IOException("V2_WEB_CONSENT_REQUIRED: 草稿已保存；请先在网站查看并确认当前上传授权协议，再送审");
        }
        positive(agreement, "releaseId"); positive(agreement, "textId");
        String hash = text(agreement, "contentHash"), locale = text(agreement, "locale");
        if (hash == null || hash.isEmpty() || locale == null || locale.isEmpty() || workId <= 0 || version <= 0)
            throw new IOException("V2_INVALID_CONSENT_CONTEXT: 上传授权上下文不完整");
        JsonObject out = body("decision", "granted");
        out.addProperty("entryPoint", "model_share_forge_112");
        out.addProperty("scopeType", "work_upload"); out.addProperty("scopeId", workId);
        out.addProperty("scopeVersion", version); out.addProperty("locale", locale);
        out.addProperty("contentHash", hash); out.add("textId", agreement.get("textId"));
        return out;
    }
}
