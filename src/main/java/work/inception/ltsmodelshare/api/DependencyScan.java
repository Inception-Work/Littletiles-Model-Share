package work.inception.ltsmodelshare.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 SNBT 里扫出「用了哪些模组方块 / 哪些其它材质」。
 *
 * <p>目标是让上传的作品在详情页能显示依赖：以前模组只发 snbtBlobId，这几项为空，
 * 单个作品的详情页就永远显示「原版」。这里用正则做个轻量扫描，够用且不依赖结构解析。</p>
 */
public final class DependencyScan {

    /** 原版 + LittleTiles 自己的命名空间不算依赖 */
    private static final Set<String> IGNORED = new LinkedHashSet<String>() {
        {
            add("minecraft");
            add("littletiles");
            add("littletiles_tiles");
        }
    };

    /** 方块 id：id:"namespace:block" */
    private static final Pattern BLOCK = Pattern.compile("id\\s*:\\s*\"([a-z0-9_.-]+):[^\"]+\"");
    /** 材质：texture:"namespace:path" / material:"namespace:path" */
    private static final Pattern TEXTURE =
            Pattern.compile("(?:texture|material)\\s*:\\s*\"([a-z0-9_.-]+):[^\"]+\"");

    /** 扫描结果 */
    public static final class Result {
        public final List<Dtos.Dependency> dependencies = new ArrayList<>();
        public boolean hasModBlocks;
        public boolean usesOtherTextures;
    }

    private DependencyScan() {
    }

    public static Result scan(String snbt) {
        Result r = new Result();
        if (snbt == null || snbt.isEmpty()) {
            return r;
        }
        Set<String> mods = collect(BLOCK, snbt);
        if (!mods.isEmpty()) {
            r.hasModBlocks = true;
            for (String ns : mods) {
                r.dependencies.add(dep("mod", ns));
            }
        }
        Set<String> textures = collect(TEXTURE, snbt);
        if (!textures.isEmpty()) {
            r.usesOtherTextures = true;
            for (String ns : textures) {
                r.dependencies.add(dep("texture", ns));
            }
        }
        return r;
    }

    private static Set<String> collect(Pattern p, String snbt) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = p.matcher(snbt);
        while (m.find()) {
            String ns = m.group(1);
            if (ns != null && !IGNORED.contains(ns)) {
                out.add(ns);
            }
        }
        return out;
    }

    private static Dtos.Dependency dep(String type, String name) {
        Dtos.Dependency d = new Dtos.Dependency();
        d.type = type;
        d.name = name;
        return d;
    }
}