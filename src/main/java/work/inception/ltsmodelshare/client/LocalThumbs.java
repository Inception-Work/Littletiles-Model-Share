package work.inception.ltsmodelshare.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import work.inception.ltsmodelshare.LtsModelShare;

/** Upload-owned, render-thread-only PNG/JPEG thumbnails with bounded CPU/GPU storage. */
public final class LocalThumbs {
    private static final int MAX = 64;
    private static final int MAX_EDGE = 240;
    private static final long MAX_SOURCE_PIXELS = 64L * 1024 * 1024;
    private static long serial;
    // Failed entries are cached too: corrupt files must not decode/log every rendered frame.
    private final Map<File, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);

    public LocalThumbs() { }

    private static final class Entry {
        final long modified;
        final long length;
        final ResourceLocation texture;
        final int width;
        final int height;
        Entry(File file, ResourceLocation texture, int width, int height) {
            this.modified = file.lastModified();
            this.length = file.length();
            this.texture = texture;
            this.width = width;
            this.height = height;
        }
        boolean unchanged(File file) { return modified == file.lastModified() && length == file.length(); }
    }

    private Entry get(File file) {
        Entry entry = cache.get(file);
        if (entry != null && entry.unchanged(file)) return entry;
        if (entry != null) { release(entry); cache.remove(file); }
        while (cache.size() >= MAX) {
            Iterator<Entry> oldest = cache.values().iterator();
            release(oldest.next());
            oldest.remove();
        }
        entry = load(file);
        cache.put(file, entry);
        return entry;
    }

    private Entry load(File file) {
        NativeImage nativeImage = null;
        DynamicTexture texture = null;
        ResourceLocation id = null;
        try {
            BufferedImage image = readSmall(file);
            int width = image.getWidth(), height = image.getHeight();
            nativeImage = new NativeImage(width, height, false);
            try {
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    // NativeImage RGBA storage is packed ABGR, BufferedImage is ARGB.
                    int abgr = (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
                    nativeImage.setPixelRGBA(x, y, abgr);
                }
            } finally { image.flush(); }
            texture = new DynamicTexture(nativeImage);
            nativeImage = null; // DynamicTexture now owns native storage.
            id = new ResourceLocation(LtsModelShare.MODID, "upload_shot/" + serial++);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture = null; // TextureManager now owns the texture.
            return new Entry(file, id, width, height);
        } catch (Exception error) {
            if (id != null) Minecraft.getInstance().getTextureManager().release(id);
            LtsModelShare.LOG.warn("Could not load screenshot thumbnail {}: {}", file, error.toString());
            return new Entry(file, null, 0, 0);
        } finally {
            if (texture != null) texture.close();
            if (nativeImage != null) nativeImage.close();
        }
    }

    private BufferedImage readSmall(File file) throws IOException {
        // Decode with subsampling rather than allocating a full screenshot/native texture.
        // Explicit file stream avoids ImageIO's global disk-cache setting/temporary files.
        try (ImageInputStream input = new FileImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            BufferedImage source;
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 32768 || height > 32768
                        || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new IOException("Screenshot dimensions exceed thumbnail safety limit");
                }
                int sample = Math.max(1, Math.max(width, height) / MAX_EDGE);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sample, sample, 0, 0);
                source = reader.read(0, param);
                if (source == null) throw new IOException("Image reader returned no pixels");
            } finally { reader.dispose(); }
            int max = Math.max(source.getWidth(), source.getHeight());
            if (max <= MAX_EDGE) return source;
            int w = Math.max(1, source.getWidth() * MAX_EDGE / max);
            int h = Math.max(1, source.getHeight() * MAX_EDGE / max);
            BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = small.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, w, h, null);
            } finally { graphics.dispose(); source.flush(); }
            return small;
        }
    }

    /** Original ImageDraw semantics: centered aspect crop, never stretch. */
    public void draw(PoseStack pose, File file, int x, int y, int w, int h) {
        Entry entry = get(file);
        if (entry.texture == null) { GuiComponent.fill(pose, x, y, x + w, y + h, 0x40FFFFFF); return; }
        int sw = entry.width, sh = entry.height, u = 0, v = 0;
        float target = w / (float) h, source = entry.width / (float) entry.height;
        if (source > target) { sw = Math.round(entry.height * target); u = (entry.width - sw) / 2; }
        else if (source < target) { sh = Math.round(entry.width / target); v = (entry.height - sh) / 2; }
        sw = Math.max(1, Math.min(sw, entry.width));
        sh = Math.max(1, Math.min(sh, entry.height));
        RenderSystem.setShaderTexture(0, entry.texture);
        GuiComponent.blit(pose, x, y, w, h, (float) u, (float) v, sw, sh, entry.width, entry.height);
    }

    /** Drop deleted/changed screenshots, without clearing valid thumbnails on every resize. */
    public void retainFiles(Collection<File> files) {
        Iterator<Map.Entry<File, Entry>> entries = cache.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<File, Entry> entry = entries.next();
            if (!files.contains(entry.getKey()) || !entry.getValue().unchanged(entry.getKey())) {
                release(entry.getValue());
                entries.remove();
            }
        }
    }

    private void release(Entry entry) {
        if (entry.texture != null) Minecraft.getInstance().getTextureManager().release(entry.texture);
    }

    /** Called from Screen.removed(), covering Escape, Back and external screen changes. */
    public void clear() {
        for (Entry entry : cache.values()) release(entry);
        cache.clear();
    }
}
