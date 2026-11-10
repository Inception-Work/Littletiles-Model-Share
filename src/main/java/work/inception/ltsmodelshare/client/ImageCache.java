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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote image cache: bounded LRU textures, decoded off-thread, registered on the render thread. */
public final class ImageCache {
    /** Covers are card-sized; keep direct + GPU memory bounded while browsing. */
    private static final int MAX_TEXTURES = 96;
    private static final int MAX_PENDING = 24;
    /** Separate from the API executor so a page full of covers can never starve list requests. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "ltsmodelshare-images");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, ResourceLocation> LOADED =
            new LinkedHashMap<>(32, 0.75f, true);
    private static final Map<String, int[]> SIZES = new LinkedHashMap<>(32, 0.75f, true);
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private static String sessionBase;
    private static String sessionToken;
    private static int sessionGeneration;

    /** Called on the render thread; private textures and failures belong to one session. */
    private static void syncSession() {
        ModConfig cfg = ModConfig.get();
        if (java.util.Objects.equals(sessionBase, cfg.baseUrl)
                && java.util.Objects.equals(sessionToken, cfg.token)) return;
        for (ResourceLocation id : LOADED.values()) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        LOADED.clear(); SIZES.clear(); LOADING.clear(); FAILED.clear();
        sessionBase = cfg.baseUrl;
        sessionToken = cfg.token;
        sessionGeneration++;
    }

    private ImageCache() { }

    public static ResourceLocation img(String url) {
        syncSession();
        if (url == null || url.trim().isEmpty()) return null;
        ResourceLocation id = LOADED.get(url);
        if (id != null) return id;
        if (!LOADING.contains(url) && !FAILED.contains(url) && LOADING.size() < MAX_PENDING) {
            LOADING.add(url);
            load(url);
        }
        return null;
    }

    public static int[] size(String url) {
        syncSession();
        return SIZES.get(url);
    }

    private static void load(final String rawUrl) {
        final String base = sessionBase, token = sessionToken;
        final int generation = sessionGeneration;
        POOL.execute(() -> {
            try {
                String url = rawUrl.startsWith("//") ? "https:" + rawUrl : rawUrl;
                byte[] bytes = ApiClient.imageBytes(base, url, token);
                java.awt.image.BufferedImage decoded=decodeAndScale(bytes);
                ApiClient.post(() -> {
                    syncSession();
                    if (generation == sessionGeneration) register(rawUrl, decoded);
                });
            } catch (Throwable error) {
                ApiClient.post(() -> {
                    syncSession();
                    if (generation != sessionGeneration) return;
                    FAILED.add(rawUrl); LOADING.remove(rawUrl);
                });
                LtsModelShare.LOG.debug("Image load failed: {}", error.getClass().getSimpleName());
            }
        });
    }

    private static void register(String url, java.awt.image.BufferedImage source) {
        LOADING.remove(url);
        NativeImage image = null;
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            image = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = source.getRGB(x, y);
                    int rgba = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
                    image.setPixelRGBA(x, y, rgba);
                }
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
            LtsModelShare.LOG.warn("Image registration failed: {}", error.getClass().getSimpleName());
        }
    }

    /**
     * Decode on the heap and downscale before creating a NativeImage.
     * Header dimensions are checked first so decompression bombs never allocate direct memory.
     */
    private static java.awt.image.BufferedImage decodeAndScale(byte[] data) throws java.io.IOException {
        if (data.length > 16 * 1024 * 1024) throw new java.io.IOException("image file too large");
        try (javax.imageio.stream.ImageInputStream input =
                     javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new java.io.IOException("unsupported image format");
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192
                        || (long) width * height > 16_000_000L) {
                    throw new java.io.IOException("image dimensions rejected: " + width + "x" + height);
                }
                java.awt.image.BufferedImage source = reader.read(0);
                int largest = Math.max(width, height);
                if (largest <= 512) return source;
                float scale = 512f / largest;
                int scaledW = Math.max(1, Math.round(width * scale));
                int scaledH = Math.max(1, Math.round(height * scale));
                java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(
                        scaledW, scaledH, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, scaledW, scaledH, null);
                } finally {
                    graphics.dispose();
                }
                return scaled;
            } finally {
                reader.dispose();
            }
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
