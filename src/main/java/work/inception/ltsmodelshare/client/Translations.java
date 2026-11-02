package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Uses Minecraft's active language with English fallback; never cache translated labels. */
public final class Translations {
    private Translations() { }

    public static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString().replace("\\n", "\n");
    }

    /** Website locale codes differ from Minecraft's region-specific language codes. */
    public static String siteLocale() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return siteLocale(code);
    }

    public static String siteLocale(String code) {
        String normalized = code == null ? "" : code.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        if (normalized.equals("zh-tw") || normalized.equals("zh-hk") || normalized.equals("zh-mo")
                || normalized.equals("zh-hant")) return "zh-Hant";
        if (normalized.startsWith("zh-")) return "zh-Hans";
        String language = normalized.split("-", 2)[0];
        switch (language) {
            case "ja": case "ko": case "de": case "fr": return language;
            default: return "en";
        }
    }
}
