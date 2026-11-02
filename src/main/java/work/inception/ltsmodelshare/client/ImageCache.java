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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Remote image cache: downloads once, registers a dynamic texture on the render thread. */
public final class ImageCache {
    /** Bounded LRU: browsing many pages must not keep every cover texture in RAM/VRAM. */
    private static final int MAX_TEXTURES = 96;
    private static final Map<String, ResourceLocation> LOADED =
            new LinkedHashMap<>(32, 0.75f, true);
    private static final Map<String, int[]> SIZES = new LinkedHashMap<>(32, 0.75f, true);
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
        NativeImage image = null;
        try {
            // read(byte[]) copies the whole file onto LWJGL's small per-thread stack and
            // overflows ("Out of stack space") for multi-megabyte screenshots. The stream
            // overload uses a fixed 8 KB buffer instead.
            image = NativeImage.read(new java.io.ByteArrayInputStream(data));
            int width = image.getWidth();
            int height = image.getHeight();
            // Screenshots can be 1920x1080 (8 MB decoded); downscale cards to 512 px.
            int largest = Math.max(width, height);
            if (largest > 512) {
                float scale = 512f / largest;
                int scaledW = Math.max(1, Math.round(width * scale));
                int scaledH = Math.max(1, Math.round(height * scale));
                NativeImage scaled = new NativeImage(scaledW, scaledH, false);
                image.resizeSubRectTo(0, 0, width, height, scaled);
                image.close();
                image = scaled;
                width = scaledW;
                height = scaledH;
            }
            DynamicTexture texture = new DynamicTexture(image);
            image = null; // DynamicTexture owns the pixels now.
            String hash = Integer.toHexString(url.hashCode());
            ResourceLocation id = new ResourceLocation("ltsmodelshare", "remote/" + hash);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            LOADED.put(url, id);
            SIZES.put(url, new int[]{width, height});
            evictOldest();
        } catch (Throwable error) {
            if (image != null) image.close();
            FAILED.add(url);
            LtsModelShare.LOG.warn("图片解码失败 {}", url, error);
        }
    }

    /** Release least-recently-used textures once the cache exceeds its bound. */
    private static void evictOldest() {
        Iterator<Map.Entry<String, ResourceLocation>> iterator = LOADED.entrySet().iterator();
        while (LOADED.size() > MAX_TEXTURES && iterator.hasNext()) {
            Map.Entry<String, ResourceLocation> entry = iterator.next();
            Minecraft.getInstance().getTextureManager().release(entry.getValue());
            SIZES.remove(entry.getKey());
            iterator.remove();
        }
    }
}
