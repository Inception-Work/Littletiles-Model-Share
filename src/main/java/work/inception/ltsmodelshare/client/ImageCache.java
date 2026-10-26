package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.ModConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 封面 / 头像的图片缓存。
 *
 * <p>界面上要显示封面和作者头像，但两张东西都可能是远程 URL（站点的 /uploads 或 CDN），
 * 而 Minecraft 的渲染线程不能做网络 / 磁盘 IO。所以：</p>
 * <ol>
 *   <li>界面每帧调 {@link #get(String)}：**有就返回**贴图，正在下就返回 null（界面画占位块）；</li>
 *   <li>后台线程用 ImageIO 把图片读成 BufferedImage；</li>
 *   <li>再用 {@code Minecraft.addScheduledTask} 切回渲染线程，建 DynamicTexture 上传给 GL。</li>
 * </ol>
 * <p>失败（404 / 不是图片）就在本地记住，不重复请求。</p>
 */
public final class ImageCache {

    private static final Map<String, ResourceLocation> READY = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ltsmodelshare-img");
        t.setDaemon(true);
        return t;
    });

    private static int counter;

    private ImageCache() {
    }

    /** 取贴图；还没加载好返回 null（调用方画占位） */
    public static ResourceLocation get(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String key = absolute(url);
        if (key == null) {
            return null;
        }
        ResourceLocation rl = READY.get(key);
        if (rl != null) {
            return rl;
        }
        if (FAILED.contains(key) || !LOADING.add(key)) {
            return null;
        }
        POOL.execute(() -> {
            BufferedImage image = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(key).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("User-Agent", "ltsmodelshare-mod/0.1.0");
                if (conn.getResponseCode() / 100 != 2) {
                    FAILED.add(key);
                    LOADING.remove(key);
                    return;
                }
                try (InputStream in = conn.getInputStream()) {
                    image = ImageIO.read(in);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                LtsModelShare.LOG.debug("图片加载失败 {}：{}", key, e.toString());
            }
            if (image == null) {
                FAILED.add(key);
                LOADING.remove(key);
                return;
            }
            final BufferedImage finalImage = image;
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    DynamicTexture tex = new DynamicTexture(finalImage);
                    String name = "img/" + (counter++);
                    ResourceLocation loc = new ResourceLocation(LtsModelShare.MODID, name);
                    // loadTexture 内部会调 tex.loadTexture(...)，即把 BufferedImage 传到 GL
                    Minecraft.getMinecraft().getTextureManager().loadTexture(loc, tex);
                    READY.put(key, loc);
                } catch (Exception e) {
                    FAILED.add(key);
                    LtsModelShare.LOG.debug("图片上传失败 {}：{}", key, e.toString());
                } finally {
                    LOADING.remove(key);
                }
            });
        });
        return null;
    }

    /** 清空（换账号 / 调试用） */
    public static void clear() {
        READY.clear();
        FAILED.clear();
    }

    private static String absolute(String url) {
        String u = url.trim();
        if (u.isEmpty()) {
            return null;
        }
        if (u.startsWith("//")) {
            return "https:" + u;
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return u;
        }
        return ModConfig.get().url(u.startsWith("/") ? u : "/" + u);
    }
}
