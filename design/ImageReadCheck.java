import com.google.gson.*;
import java.nio.file.*;
import work.inception.ltsmodelshare.api.V2Transport;

/** Read-only diagnosis using the existing local session. Never logs credentials or response bodies. */
class ImageReadCheck {
    public static void main(String[] args) throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(Path.of("run/config/ltsmodelshare.json"))).getAsJsonObject();
        String base = config.get("baseUrl").getAsString(), token = config.get("token").getAsString();
        var response = V2Transport.request(base, "/api/v2/studio/library?size=100", "GET", null, null, token, 4*1024*1024);
        System.out.println("Library HTTP " + response.status());
        if (response.status() != 200) return;
        JsonObject payload = JsonParser.parseString(response.text()).getAsJsonObject().getAsJsonObject("payload");
        java.util.List<JsonElement> rows = new java.util.ArrayList<>();
        payload.getAsJsonArray("records").forEach(rows::add);
        rows.sort(java.util.Comparator.comparing(row -> "public".equals(row.getAsJsonObject().get("visibility").getAsString())));
        int count = 0;
        for (JsonElement row : rows) {
            JsonObject card = row.getAsJsonObject();
            if (!card.has("cover") || card.get("cover").isJsonNull()) continue;
            String url = card.get("cover").getAsString();
            for (boolean auth : new boolean[]{true}) {
                long started = System.nanoTime();
                var image = V2Transport.request(base, url, "GET", null, null, auth ? token : null, 16*1024*1024);
                long elapsed = (System.nanoTime() - started) / 1_000_000;
                boolean decoded = image.status() == 200 && javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.bytes())) != null;
                System.out.println("Work " + card.get("workId") + " " + card.get("visibility") + " HTTP=" + image.status() + " finalHost=" + image.finalUrl().getHost() + " bytes=" + image.bytes().length + " elapsedMs=" + elapsed + " decoded=" + decoded);
            }
            if (++count >= 2) break;
        }
    }
}
