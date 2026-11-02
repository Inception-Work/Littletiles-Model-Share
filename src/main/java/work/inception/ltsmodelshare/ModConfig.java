package work.inception.ltsmodelshare;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 模组配置：站点地址 + 登录令牌 + 下载目录。
 *
 * <p>存成 {@code config/ltsmodelshare.json}，纯文本，方便玩家自己改站点地址
 * （比如换成测试服或者本地起的后端）。</p>
 */
public class ModConfig {

    /** 默认站点地址 */
    public static final String DEFAULT_BASE_URL = "https://www.inception.work";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 站点根地址，例如 https://www.inception.work（不带结尾斜杠） */
    public String baseUrl = DEFAULT_BASE_URL;

    /** 登录令牌（Bearer）；为空 = 未登录 */
    public String token = "";

    /** 密码登录的刷新令牌（服务端滑动两小时，每次续期轮换）；个人访问令牌模式留空。 */
    public String refreshToken = "";

    /** 上次登录的用户名（只用于回显，不存密码） */
    public String username = "";

    /** 下载目录；空 = 用 gameDir/ltsmodels */
    public String downloadDir = "";

    // ==================== 读写 ====================

    private static File file() {
        return FMLPaths.CONFIGDIR.get().resolve("ltsmodelshare.json").toFile();
    }

    private static ModConfig cached;

    public static ModConfig get() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    private static ModConfig load() {
        File f = file();
        if (f.isFile()) {
            try (Reader r = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
                ModConfig cfg = GSON.fromJson(r, ModConfig.class);
                if (cfg != null) {
                    cfg.normalize();
                    return cfg;
                }
            } catch (Exception e) {
                LtsModelShare.LOG.warn("读取配置失败，用默认值：{}", e.toString());
            }
        }
        ModConfig cfg = new ModConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        normalize();
        File f = file();
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            LtsModelShare.LOG.warn("建不了配置目录：{}", dir);
        }
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(f.toPath()), StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            LtsModelShare.LOG.warn("写配置失败：{}", e.toString());
        }
    }

    private void normalize() {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        baseUrl = baseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (token == null) token = "";
        if (refreshToken == null) refreshToken = "";
        if (username == null) username = "";
        if (downloadDir == null) downloadDir = "";
    }

    /** 接口地址（相对路径 → 完整 URL） */
    public String url(String path) {
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    /** 下载目录（不存在就建） */
    public File downloadFolder() {
        File f = downloadDir == null || downloadDir.trim().isEmpty()
                ? new File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "ltsmodels")
                : new File(downloadDir);
        if (!f.isDirectory() && !f.mkdirs()) {
            LtsModelShare.LOG.warn("建不了下载目录：{}", f);
        }
        return f;
    }

    public boolean loggedIn() {
        return token != null && !token.isEmpty();
    }

    /** 服务器确认密码会话过期时清除本地双令牌（保留用户名供重新登录）。 */
    public void clearSession() {
        token = "";
        refreshToken = "";
        save();
    }
}
