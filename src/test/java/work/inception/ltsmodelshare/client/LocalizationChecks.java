package work.inception.ltsmodelshare.client;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.translation.LanguageMap;
import work.inception.ltsmodelshare.api.Dtos;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Runs on the build JVM without opening a Minecraft window or requiring a display. */
public final class LocalizationChecks {
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + "; got " + actual);
    }

    private static Map<String, String> language(String code) throws Exception {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                LocalizationChecks.class.getResourceAsStream("/assets/ltsmodelshare/lang/" + code + ".lang"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || !line.contains("=")) continue;
                String[] pair = line.split("=", 2);
                map.put(pair[0], pair[1]);
            }
        }
        return map;
    }

    public static void main(String[] args) throws Exception {
        // Exercise actual outgoing HTTP headers, including a CDN-style redirect.
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicReference<String> received = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/asset", exchange -> {
            received.set(exchange.getRequestHeaders().getFirst("Referer"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/asset");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect").openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try {
                work.inception.ltsmodelshare.api.AssetHeaders.apply(connection, "https://www.inception.work");
                equal(204, connection.getResponseCode());
                equal("https://www.inception.work/", received.get());
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stop(0);
        }
        equal("https://example.com:8443/", work.inception.ltsmodelshare.api.AssetHeaders.referer(
                "https://user:secret@example.com:8443/site?private=value#fragment"));
        int[][] sizes = {{320, 240}, {427, 240}, {640, 360}, {854, 480}, {1920, 1080}};
        for (int[] size : sizes) {
            work.inception.ltsmodelshare.gui.ModelGridLayout grid =
                    new work.inception.ltsmodelshare.gui.ModelGridLayout(size[0], size[1]);
            if (grid.columns < 4 || grid.rows < 2 || grid.columns * grid.rows > 48
                    || grid.left + grid.columns * grid.cardWidth + (grid.columns - 1) * 6 > size[0] - 8
                    || grid.top + grid.rows * grid.cardHeight + (grid.rows - 1) * 6 > size[1] - 62) {
                throw new AssertionError("Grid does not fit " + size[0] + "x" + size[1]);
            }
            System.out.println("Grid " + size[0] + "x" + size[1] + ": " + grid.columns + " columns, " + grid.rows + " rows");
        }
        String[][] locales = {{"zh_cn", "zh-Hans"}, {"zh_sg", "zh-Hans"}, {"zh_tw", "zh-Hant"},
                {"zh_hk", "zh-Hant"}, {"en_us", "en"}, {"en_gb", "en"}, {"ja_jp", "ja"},
                {"ko_kr", "ko"}, {"de_de", "de"}, {"fr_fr", "fr"}, {"fr_ca", "fr"},
                {"ru_ru", "en"}, {"", "en"}, {null, "en"}};
        for (String[] pair : locales) equal(pair[1], Translations.siteLocale(pair[0]));

        Dtos.Tag tag = new Dtos.Tag();
        tag.code = "architecture";
        tag.name = "Original tag name";
        equal("Architecture", SiteTags.label(tag, "en"));
        equal("建筑", SiteTags.label(tag, "zh-Hans"));
        equal("建築", SiteTags.label(tag, "zh-Hant"));
        equal("建築", SiteTags.label(tag, "ja"));
        // Every website language has a built-in fallback before the original name.
        for (String language : new String[]{"en", "zh-Hans", "zh-Hant", "ja", "ko", "de", "fr"}) {
            if (tag.name.equals(SiteTags.label(tag, language))) throw new AssertionError("Missing site tags: " + language);
        }
        tag.nameI18n = new HashMap<>();
        tag.nameI18n.put("en", "Website override");
        equal("Website override", SiteTags.label(tag, "en"));
        tag.nameI18n.put("en", "  ");
        equal("Architecture", SiteTags.label(tag, "en"));
        tag.code = "custom-tag";
        equal(tag.name, SiteTags.label(tag, "en"));
        tag.nameI18n.put("en", "Custom website translation");
        equal("Custom website translation", SiteTags.label(tag, "en"));
        equal("custom-tag", tag.code);

        Locale locale = new Locale();
        Field properties = Locale.class.getDeclaredField("properties");
        properties.setAccessible(true);
        Field active = I18n.class.getDeclaredField("i18nLocale");
        active.setAccessible(true);
        active.set(null, locale);
        for (String code : new String[]{"en_us", "zh_cn", "zh_tw", "ja_jp", "ko_kr", "de_de", "fr_fr"}) {
            Map<String, String> labels = language(code);
            properties.set(locale, labels);
            LanguageMap.replaceWith(labels);
            equal(String.format(labels.get("ltsmodelshare.models.pagination"), 1, 3, 20),
                    Translations.tr("ltsmodelshare.models.pagination", 1, 3, 20));
            String draft = Translations.tr("ltsmodelshare.upload.draft_saved", 7, 2, 3);
            if (!draft.contains("\n") || draft.contains("\\n")) throw new AssertionError("Multiline translation failed");
            // The same server JSON must resolve in each recipient's active language.
            String json = ITextComponent.Serializer.componentToJson(
                    new TextComponentTranslation("ltsmodelshare.server.created", "Example", 42));
            equal(String.format(labels.get("ltsmodelshare.server.created"), "Example", 42),
                    ITextComponent.Serializer.jsonToComponent(json).getUnformattedText());
        }
        System.out.println("Localization behavior passed in all 7 languages: locale mapping, website tags, formatting, server translation components");
    }
}
