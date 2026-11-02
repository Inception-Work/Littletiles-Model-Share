package work.inception.ltsmodelshare.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.AssetHeaders;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Remote image cache: downloads once, registers a dynamic texture on the render thread. */
public final class ImageCache {
    private static final Map<String, ResourceLocation> LOADED = new HashMap<>();
    private static final Map<String, int[]> SIZES = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private ImageCache() { }

    public static ResourceLocation img(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        ResourceLocation id = LOADED.get(url);
        if (id != null) return id;
        if (!LOADING.contains(url) && !FAILED.contains(url)) {
            LOADING.add(url);
            load(url);
        }
        return null;
    }

    public static int[] size(String url) {
        return SIZES.get(url);
    }

    private static void load(final String rawUrl) {
        ApiClient.io(() -> {
            try {
                String url = rawUrl.startsWith("//") ? "https:" + rawUrl : rawUrl;
                if (url.startsWith("/")) url = ModConfig.get().baseUrl + url;
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
                AssetHeaders.apply(conn, ModConfig.get().baseUrl);
                int code = conn.getResponseCode();
                if (code / 100 != 2) throw new IllegalStateException("HTTP " + code);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                conn.disconnect();
                final byte[] data = out.toByteArray();
                ApiClient.post(() -> register(rawUrl, data));
            } catch (Exception error) {
                FAILED.add(rawUrl);
                LOADING.remove(rawUrl);
                LtsModelShare.LOG.debug("图片加载失败 {}", rawUrl, error);
            }
        });
    }

    private static void register(String url, byte[] data) {
        LOADING.remove(url);
        try (NativeImage image = NativeImage.read(data)) {
            int width = image.getWidth();
            int height = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            String hash = Integer.toHexString(url.hashCode());
            ResourceLocation id = new ResourceLocation("ltsmodelshare", "remote/" + hash);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            LOADED.put(url, id);
            SIZES.put(url, new int[]{width, height});
        } catch (Exception error) {
            FAILED.add(url);
            LtsModelShare.LOG.debug("图片解码失败 {}", url, error);
        }
    }
}
