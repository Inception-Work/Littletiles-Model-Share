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
 * <p>远程图片（站点的 /uploads 或 CDN）不能在渲染线程里拉，也不能在渲染线程里做 IO。
 * 流程：</p>
 * <ol>
 *   <li>界面每帧调 {@link #img(String)}：有就返回贴图（含原始宽高，供等比裁剪算 UV），
 *       正在下返回 null（界面画占位块）；</li>
 *   <li>后台线程 ImageIO 读成 BufferedImage；</li>
 *   <li>用 {@code Minecraft.addScheduledTask} 切回渲染线程建 DynamicTexture 上传 GL。</li>
 * </ol>
 * <p>失败（404 / 不是图片）记下来，不重复请求。</p>
 */
public final class ImageCache {

    /** 已上传的贴图 + 原始像素尺寸（等比裁剪要靠它算源矩形） */
    public static final class Img {
        public final ResourceLocation tex;
        public final int width;
        public final int height;

        Img(ResourceLocation tex, int width, int height) {
            this.tex = tex;
            this.width = width;
            this.height = height;
        }
    }

    private static final Map<String, Img> READY = new HashMap<>();
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
    public static Img img(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String key = absolute(url);
        if (key == null) {
            return null;
        }
        Img ready = READY.get(key);
        if (ready != null) {
            return ready;
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
                    ResourceLocation loc = new ResourceLocation(LtsModelShare.MODID, "img/" + (counter++));
                    // loadTexture 内部会调 tex.loadTexture(...)，把 BufferedImage 传到 GL
                    Minecraft.getMinecraft().getTextureManager().loadTexture(loc, tex);
                    READY.put(key, new Img(loc, finalImage.getWidth(), finalImage.getHeight()));
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
