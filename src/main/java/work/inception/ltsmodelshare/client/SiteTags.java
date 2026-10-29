package work.inception.ltsmodelshare.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.api.Dtos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/** Matches the website's utils/workTag.ts: database translation, built-in label, original name. */
public final class SiteTags {
    private static final Map<String, Map<String, String>> FALLBACKS = loadFallbacks();

    private SiteTags() { }

    public static String label(Dtos.Tag tag) {
        return label(tag, Translations.siteLocale());
    }

    static String label(Dtos.Tag tag, String locale) {
        if (tag == null) return "";
        String language = "zh-Hans".equals(locale) ? "zh-cn" : "zh-Hant".equals(locale) ? "zh-tw" : locale;
        String translated = tag.nameI18n == null ? null : tag.nameI18n.get(language);
        if (translated != null && !translated.trim().isEmpty()) return translated;
        Map<String, String> builtins = FALLBACKS.get(language);
        String builtin = builtins == null ? null : builtins.get(tag.code);
        return builtin != null ? builtin : tag.name == null ? tag.code == null ? "" : tag.code : tag.name;
    }

    private static Map<String, Map<String, String>> loadFallbacks() {
        try (InputStream input = SiteTags.class.getResourceAsStream("/assets/ltsmodelshare/site_tag_fallbacks.json")) {
            if (input != null) {
                Map<String, Map<String, String>> result = new Gson().fromJson(
                        new InputStreamReader(input, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Map<String, String>>>() { }.getType());
                if (result != null) return result;
            }
        } catch (Exception error) {
            LtsModelShare.LOG.warn("Could not load website tag fallback translations", error);
        }
        return Collections.emptyMap();
    }
}
