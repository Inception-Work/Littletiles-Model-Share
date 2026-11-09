package work.inception.ltsmodelshare.api;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Protocol identities, never prose matching. Catalog is a snapshot of the website's seven languages. */
public final class V2Errors {
    private static final JsonObject MESSAGES = load();
    private static final Set<String> PARAMETERS = new HashSet<>(Arrays.asList(
            "field","min","max","limit","actual","expected","remaining","seconds","workId","version","ruleCount"));
    private static JsonObject load() {
        try (Reader reader = new InputStreamReader(V2Errors.class.getResourceAsStream(
                "/assets/ltsmodelshare/v2-errors.json"), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } catch (Exception failed) { throw new IllegalStateException("Missing V2 error catalog"); }
    }
    private V2Errors() {}
    public static String message(String code, String type, String fallback, JsonObject params, String locale) {
        String normalized = locale == null ? "en" : locale.toLowerCase(Locale.ROOT).replace('_','-');
        int index = normalized.startsWith("zh") ? (normalized.contains("hant") || normalized.endsWith("-tw") ? 1 : 0)
                : normalized.startsWith("ja") ? 3 : normalized.startsWith("ko") ? 4
                : normalized.startsWith("de") ? 5 : normalized.startsWith("fr") ? 6 : 2;
        String safeCode = code != null && code.matches("[A-Z0-9_-]{1,80}") ? code : "V2_INVALID_RESPONSE";
        String template = MESSAGES.has(safeCode) ? MESSAGES.getAsJsonArray(safeCode).get(index).getAsString()
                : MESSAGES.getAsJsonArray("C0-01").get(index).getAsString();
        // Known templates only. Arbitrary server fallback or parameter values may expose credentials.
        Matcher matcher = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)\\}").matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1), value = matcher.group();
            if (params != null && PARAMETERS.contains(key) && params.has(key) && params.get(key).isJsonPrimitive()) {
                String proposed = params.get(key).getAsString();
                if (proposed.matches("[\\p{L}\\p{N}_ .-]{1,100}")) value = proposed;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result + " [" + (type != null && type.matches("[A-Z_]{1,64}") ? type + "/" : "") + safeCode + "]";
    }
}
