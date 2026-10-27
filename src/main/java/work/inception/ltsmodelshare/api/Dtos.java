package work.inception.ltsmodelshare.api;

import java.util.List;

/**
 * 站点接口的数据结构（只挑模组用得到的字段）。
 *
 * <p>和后端一一对应，见 inception-work 仓库的
 * {@code Sources/web/src/api/work_module/WorkPublicAPI.ts}。</p>
 */
public final class Dtos {

    private Dtos() {
    }

    /** 通用响应壳：{ success, payload, error_code, error_message } */
    public static class Resp<T> {
        public boolean success;
        public T payload;
        public String error_code;
        public String error_message;
    }

    /** 登录响应：payload.token / payload.refreshToken */

    public static class LoginPayload {
        public String token;
        public String refreshToken;
    }

    /** 分页壳 */
    public static class Page<T> {
        public List<T> records;
        public long total;
        public long current;
        public long size;
    }

    /** 作品卡片（列表用） */
    public static class WorkCard {
        public String kind;
        public long workId;
        public Long itemId;
        public String title;
        public String summary;
        public String cover;
        public List<Tag> tags;
        public Author author;
        public long viewCount;
        public long downloadCount;
        public String publishedAt;
    }

    /** 作品详情（详情/下载用） */
    public static class WorkDetail {
        public long workId;
        public String kind;
        public String title;
        public String summary;
        public String visibility;
        public List<Tag> tags;
        public Author author;
        public long viewCount;
        public long downloadCount;
        /** 收藏数 */
        public Long favoriteCount;
        /** 评论数 */
        public Long commentCount;
        /** 封面/详情图（第一张当大图用） */
        public List<String> images;
        /** 合集统一声明的模组/材质（单个作品为空，依赖在 items[] 里） */
        public List<Dependency> dependencies;
        /** 这条作品里的模型条目 */
        public List<Item> items;
    }

    /** 依赖（合集声明用） */
    public static class Dependency {
        public String type;
        public String name;
        public String url;
    }

    /**
     * 作品里的一条模型。
     *
     * <p>字段对着后端 PublicItemView 来：**没有** sizeBytes / snbtVersion，
     * 文件大小在 {@code preview.sizeBytes}（只有生成好白模的才有）。</p>
     */
    public static class Item {
        public long itemId;
        public String title;
        public String summary;
        /** 这条模型标注的 MC 版本（1.12 / 1.19…） */
        public String mcVersion;
        /** SNBT 顶层网格（1 方块 = grid 格） */
        public Integer grid;
        /** box 总数 */
        public Integer boxCount;
        public Integer boxOffsetCount;
        public Integer boxPlainCount;
        public List<String> images;
        /** 这份结构真实用到的模组命名空间（上传时解析出来的） */
        public List<String> modNamespaces;
        /** 是否用了 LittleTiles 之外的其他方块 */
        public boolean hasModBlocks;
        /** 是否用了别的材质 */
        public boolean usesOtherTextures;
        public List<Dependency> dependencies;
        /** 在线预览（白模 OBJ）；有 sizeBytes 时能当文件大小的参考 */
        public Preview preview;
    }

    /** 在线预览信息 */
    public static class Preview {
        public boolean available;
        public String reason;
        public String assetUrl;
        public Long sizeBytes;
    }

    public static class Tag {
        public String code;
        public String name;
    }

    public static class Author {
        public long userId;
        public String username;
        public String displayName;
        public String avatarUrl;
        public String preferredLocale;
    }

    /** 下载 SNBT 的结果 */
    public static class DownloadInfo {
        public String assetUrl;
        public String fileName;
        public long sizeBytes;
        /** 实际给的那一代 */
        public String version;
        public boolean converted;
    }

    /** 列表查询条件（POST /api/public/work/cards 的 body） */
    public static class CardQuery {
        public String q;
        public boolean exact;
        public List<String> tagCodes;
        public Long authorId;
        public String sort;
        public String locale;
        public long current;
        public long size;

        public CardQuery(String q, String sort, long current, long size, String locale) {
            this.q = q;
            this.sort = sort;
            this.current = current;
            this.size = size;
            this.locale = locale;
        }
    }

    /** 登录请求体 */
    public static class LoginRequest {
        public String username;
        public String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    // ==================== 各接口的具体响应类型 ====================
    // Gson 通过「继承泛型父类」就能推断出 T，比 TypeToken 干净

    public static class LoginResp extends Resp<LoginPayload> {
    }

    public static class CardsResp extends Resp<Page<WorkCard>> {
    }

    public static class DetailResp extends Resp<WorkDetail> {
    }

    public static class DownloadResp extends Resp<DownloadInfo> {
    }

    // ==================== 令牌续期 ====================

    /** POST /api/account/auth/refresh 的请求体 */
    public static class RefreshRequest {
        public String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    /** 续期返回：新的 access token + 轮换后的 refresh token */
    public static class TokenPair {
        public String token;
        public String refreshToken;
    }

    public static class RefreshResp extends Resp<TokenPair> {
    }

    // ==================== 上传（手持蓝图 → 站点草稿） ====================

    /** POST /api/studio/work/upload-snbt 的返回 */
    public static class SnbtUpload {
        public long blobId;
        public String sha256;
        public long sizeBytes;
        public String assetUrl;
        /** true = 同一份 SNBT 已存在，后端复用（不会重新生成白模） */
        public boolean dedup;
        /** SNBT 顶层网格 */
        public Integer grid;
        public Integer boxCount;
    }

    public static class SnbtUploadResp extends Resp<SnbtUpload> {
    }

    /** POST /api/studio/work/save 的请求体（只用到最少的字段） */
    public static class WorkSaveRequest {
        /** single / collection */
        public String kind = "single";
        public String title;
        public String summary;
        /** 受控标签 code */
        public List<String> tagCodes;
        /** 作品截图地址（≤6 张），来自 upload-image */
        public List<String> images;
        public List<SaveItem> items;

        public static class SaveItem {
            /** upload-snbt 返回的 blobId */
            public long snbtBlobId;
            public String title;
            public String summary;
            /** 模型是哪一代：1.12.2 / 1.19+ */
            public String mcVersion;

            public SaveItem(long snbtBlobId, String title, String mcVersion) {
                this.snbtBlobId = snbtBlobId;
                this.title = title;
                this.mcVersion = mcVersion;
            }
        }
    }

    /** save 返回作品 ID */
    public static class WorkSaveResp extends Resp<Long> {
    }

    // ==================== 高级搜索用 ====================

    public static class TagsResp extends Resp<List<Tag>> {
    }

    public static class AuthorsResp extends Resp<List<Author>> {
    }

    /** upload-image 返回图片地址（字符串） */
    public static class ImageUploadResp extends Resp<String> {
    }
}
