package work.inception.ltsmodelshare.api;

import com.google.gson.*;
import work.inception.ltsmodelshare.client.ImageUrls;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic wire fixtures only: no sockets, Minecraft, accounts, database or website. */
public final class ApiContractChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static JsonObject json(String text) { return V2Contract.object(new JsonParser().parse(text)); }
    private interface Checked { void run() throws Exception; }
    private static void rejects(String code, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (IOException | IllegalArgumentException expected) {
            check(expected.getMessage().contains(code), "Expected " + code + ", got " + expected.getMessage());
        }
    }
    private static void dtoFixtures() throws Exception {
        Dtos.Page<Dtos.WorkCard> page=V2Contract.cards(json("{\"items\":[{\"id\":17,\"kind\":\"single\","
                +"\"title\":\"模型\",\"summary\":\"原文\",\"coverResourceId\":4,\"authorId\":9,"
                +"\"authorUsername\":\"fixture\",\"authorName\":\"作者\",\"authorAvatarId\":5,\"downloads\":8}],"
                +"\"total\":31,\"page\":2,\"size\":12}"));
        check(page.current==2 && page.total==31 && page.size==12,"V2 page metadata");
        Dtos.WorkCard card=page.records.get(0);
        check(card.workId==17 && card.author.userId==9 && card.downloadCount==8,"Flattened V2 card");
        check("/api/v2/resources/4/content".equals(card.cover),"Resource cover");
        check("/api/v2/resources/5/content".equals(card.author.avatarUrl),"Resource avatar");
        Dtos.Page<Dtos.WorkCard> author=V2Contract.cards(json("{\"records\":[{\"workId\":19,\"tags\":["
                +"{\"code\":\"building\",\"name\":\"建筑\",\"nameI18n\":{\"en\":\"Building\"}}],"
                +"\"author\":{\"userId\":10,\"displayName\":\"作者\"}}],\"current\":3,\"size\":1,\"total\":4}"));
        check(author.current==3 && author.records.get(0).workId==19,"Author page aliases");
        check("building".equals(author.records.get(0).tags.get(0).code),"Tag codes preserved");
        check("Building".equals(author.records.get(0).tags.get(0).nameI18n.get("en")),"Tag translations preserved");

        Dtos.WorkDetail detail=V2Contract.detail(json("{\"id\":17,\"kind\":\"single\",\"description\":\"详情\","
                +"\"author\":{\"id\":9,\"nickname\":\"作者\"},\"views\":12,\"downloads\":8,\"favorites\":2,"
                +"\"images\":[{\"resourceId\":4,\"url\":\"/api/v2/resources/4/content?access=fixture\"}],"
                +"\"tags\":[\"建筑\"],\"items\":[{\"id\":77,\"mcVersion\":\"1.12.2\",\"sizeBytes\":96,"
                +"\"images\":[{\"url\":\"/api/v2/resources/6/content\"}],\"hasModBlocks\":true,"
                +"\"meshSizeBytes\":123,\"preview\":{\"available\":true,\"assetUrl\":\"/api/v2/resources/8/content\"}}]}"));
        check(detail.workId==17 && "详情".equals(detail.summary) && detail.author.userId==9,"Detail aliases");
        check(detail.viewCount==12 && detail.downloadCount==8 && detail.favoriteCount==2,"Detail counts");
        check(detail.tags.get(0).code==null && "建筑".equals(detail.tags.get(0).name),"Names must not become tag codes");
        check(detail.images.get(0).endsWith("?access=fixture"),"Signed image query preserved");
        Dtos.Item item=detail.items.get(0);
        check(item.itemId==77 && item.snbtSizeBytes==96 && item.preview.sizeBytes==123,"Source and mesh sizes distinguished");
        check(item.hasModBlocks && item.images.get(0).endsWith("/6/content"),"Item media shape");
        Dtos.Author me=V2Contract.author(json("{\"id\":9,\"nickname\":\"账户名\",\"locale\":\"en\"}"));
        check(me.userId==9 && "en".equals(me.preferredLocale),"Account locale alias");
        rejects("V2_LIST_HIDDEN",()->V2Contract.cards(json("{\"hidden\":true,\"records\":[],\"total\":0}")));
        rejects("missing page records",()->V2Contract.cards(json("{\"total\":0}")));
    }
    private static void queryFixtures() throws Exception {
        Dtos.CardQuery q=new Dtos.CardQuery("a &中","latest",2,12,"zh-Hans");
        String path=V2Contract.cardsPath(q);
        check(path.startsWith("/api/v2/works?"),"V2 market route");
        check(path.contains("q=a+%26%E4%B8%AD") && path.contains("page=2"),"Encoded query and page");
        check(path.contains("locale=zh-Hans"),"Requested language reaches server");
        q.exact=true;
        check(V2Contract.cardsPath(q).contains("exact=true"),"Exact search reaches the server");
        q.exact=false; q.sort="hot";
        check(V2Contract.cardsPath(q).contains("sort=downloads"),"Hot sorting maps to downloads");
        q.authorId=9L;
        check(V2Contract.cardsPath(q).startsWith("/api/v2/authors/9/works?"),"Author route supports its own sorting");
        q.authorId=null;
        q.tagCodes=Collections.singletonList("building");
        check(V2Contract.cardsPath(q).contains("tagCodes=building"),"Tag filter reaches the server");
        q.tagCodes=null; q.sourceLoader="forge"; q.sourceGameVersions=Collections.singletonList("1.12.2");
        String environment=V2Contract.cardsPath(q);
        check(environment.contains("sourceLoader=forge"),"Loader filter reaches the server");
        check(environment.contains("sourceGameVersions=1.12.2"),"Game version filter reaches the server");
    }
    private static void uploadFixtures() throws Exception {
        Dtos.WorkSaveRequest request=new Dtos.WorkSaveRequest();
        request.sourceLoader="forge";request.sourceGameVersion="1.12.2";request.title="fixture";
        request.images=Collections.singletonList("/api/v2/resources/4/content");
        Dtos.WorkSaveRequest.SaveItem item=new Dtos.WorkSaveRequest.SaveItem(21,"fixture","1.12.2");
        item.hasModBlocks=1;item.usesOtherTextures=0;request.items=Collections.singletonList(item);
        Map<String,Long> ids=new HashMap<>();ids.put(request.images.get(0),4L);
        JsonObject body=V2Contract.saveBody(request,ids);
        check(!body.has("images") && body.getAsJsonArray("imageResourceIds").get(0).getAsLong()==4,"Image resource binding");
        JsonObject savedItem=body.getAsJsonArray("items").get(0).getAsJsonObject();
        check(savedItem.get("snbtBlobId").getAsLong()==21,"Upload resource ID retained");
        check(savedItem.get("hasModBlocks").getAsJsonPrimitive().isBoolean()
                && savedItem.get("hasModBlocks").getAsBoolean(),"Integer flag becomes Boolean");
        check(!savedItem.get("usesOtherTextures").getAsBoolean(),"Explicit false flag retained");
        check("1.12.2".equals(body.get("sourceGameVersion").getAsString()),"Forge 1.12 upload environment");
        rejects("V2_IMAGE_RESOURCE_MISSING",()->V2Contract.saveBody(request,Collections.emptyMap()));
        JsonObject agreement=json("{\"consented\":true,\"releaseId\":10,\"textId\":12,"
                +"\"contentHash\":\"fixture-hash\",\"locale\":\"en\",\"consentTextId\":12,\"consentReleaseId\":10}");
        JsonObject receipt=V2Contract.receiptBody(agreement,17,2);
        check(receipt.get("scopeId").getAsLong()==17 && receipt.get("scopeVersion").getAsLong()==2,"Receipt bound to work/version");
        check(receipt.get("textId").getAsLong()==12 && "fixture-hash".equals(receipt.get("contentHash").getAsString()),"Receipt bound to exact text");
        check("granted".equals(receipt.get("decision").getAsString()),"Explicit consent decision");
        agreement.addProperty("consented",false);
        rejects("V2_WEB_CONSENT_REQUIRED",()->V2Contract.receiptBody(agreement,17,2));
        agreement.addProperty("consented",true);agreement.addProperty("consentInherited",true);
        rejects("V2_WEB_CONSENT_REQUIRED",()->V2Contract.receiptBody(agreement,17,2));
        agreement.addProperty("consentInherited",false);agreement.addProperty("consentTextId",11);
        rejects("V2_WEB_CONSENT_REQUIRED",()->V2Contract.receiptBody(agreement,17,2));
        agreement.addProperty("consentTextId",12);agreement.remove("contentHash");
        rejects("V2_INVALID_CONSENT_CONTEXT",()->V2Contract.receiptBody(agreement,17,2));
    }
    private static final class Stub extends HttpURLConnection {
        final int status; final byte[] bytes; final String redirect;
        final Map<String,String> sent=new HashMap<>(); final ByteArrayOutputStream written=new ByteArrayOutputStream();
        boolean disconnected;
        Stub(URL url,int status,String body,String redirect) {
            super(url);this.status=status;this.bytes=body.getBytes(StandardCharsets.UTF_8);this.redirect=redirect;
        }
        @Override public void setRequestProperty(String k,String v){sent.put(k,v);}
        @Override public int getResponseCode(){return status;}
        @Override public String getHeaderField(String name){return "Location".equals(name)?redirect:null;}
        @Override public long getContentLengthLong(){return -1;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
        @Override public InputStream getErrorStream(){return new ByteArrayInputStream(bytes);}
        @Override public OutputStream getOutputStream(){return written;}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
    private static void transportFixtures() throws Exception {
        List<Stub> seen=new ArrayList<>();
        V2Http http=new V2Http(url->{
            Stub c=new Stub(url,seen.isEmpty()?302:200,"v 0 0 0\nf 1 1 1\n",
                    seen.isEmpty()?"https://cdn.example.test/model.obj":null);
            seen.add(c);return c;
        });
        V2Http.Response r=http.request("https://site.example.test","/api/v2/resources/8/content","GET",
                null,null,"fixture-credential",20000,1024);
        check(r.status==200 && seen.size()==2,"Explicit asset redirect");
        check("Bearer fixture-credential".equals(seen.get(0).sent.get("Authorization")),"Same-origin API authenticated");
        check(!seen.get(1).sent.containsKey("Authorization"),"Never forward Bearer to CDN");
        check(seen.get(0).disconnected && seen.get(1).disconnected,"Connections always closed");
        check("https://www.inception.work/".equals(seen.get(1).sent.get("Referer")),"CDN source referer");
        check(!V2Http.credentialTarget("https://site.example.test",r.finalUrl),"Final CDN response cannot trigger account refresh");
        check(!V2Http.credentialTarget("https://site.example.test",new URL("https://site.example.test:444/api/v2/auth/me")),"Port is part of origin");
        check(!V2Http.credentialTarget("https://site.example.test",new URL("https://site.example.test/uploads/model.txt")),"Legacy asset path gets no account credential");
        V2Http fail=new V2Http(url->new Stub(url,401,"{\"success\":false}",null));
        check(fail.request("https://site.example.test","/api/v2/auth/me","GET",null,null,"fixture",1000,1024).status==401,"HTTP status retained");
        V2Http huge=new V2Http(url->new Stub(url,200,"0123456789",null));
        rejects("V2_RESPONSE_TOO_LARGE",()->huge.request("https://site.example.test","/api/v2/resources/8/content","GET",null,null,null,1000,5));
        V2Http redirect=new V2Http(url->new Stub(url,302,"","https://other.example.test/path?access=fixture"));
        rejects("V2_CAPABILITY_REDIRECT_REFUSED",()->redirect.request("https://site.example.test","/api/v2/resources/8/content","GET",null,null,"fixture",1000,1024));
        V2Http downgrade=new V2Http(url->new Stub(url,302,"","http://site.example.test/api/v2/works"));
        rejects("V2_INSECURE_REDIRECT",()->downgrade.request("https://site.example.test","/api/v2/works","GET",null,null,"fixture",1000,1024));
        rejects("V2_WRITE_REDIRECT_REFUSED",()->redirect.request("https://site.example.test","/api/v2/works/save","POST",new byte[]{1},"application/json","fixture",1000,1024));
        rejects("V2_INVALID_ASSET_URL",()->V2Http.resolve("https://site.example.test","file:/etc/passwd"));
        rejects("V2_INVALID_ASSET_URL",()->V2Http.resolve("https://site.example.test","https://user@cdn.example.test/file"));
    }
    private static void imageFixtures() {
        String api="https://www.inception.work/api/v2/resources/4/content?access=fixture";
        check(api.equals(ImageUrls.thumb(api)) && api.equals(ImageUrls.preview(api)),"V2 API URLs must not gain CDN suffixes");
        check("https://cdn.inception.work/a.png/thumb?x=1".equals(ImageUrls.thumb("https://cdn.inception.work/a.png?x=1")),"Existing CDN processing unchanged");
        check("/api/v2/resources/4/content?media=fixture".equals(ImageUrls.preview("/api/v2/resources/4/content?media=fixture")),"Relative signed URL unchanged");
    }
    private static void dialectFixtures() throws Exception {
        LegacySnbt.requireLegacy("{grid:16,note:\"t:{fake}\",tiles:[{tile:{block:\"minecraft:stone\"},boxes:[[I;0,0,0,16,16,16]]}]}");
        check(true,"Legacy bytes accepted without rewriting quoted text");
        LegacySnbt.requireLegacy("{children:[{tiles:[]}],structure:{id:\"fixed\"}}");
        check(true,"Nested legacy structures retained");
        rejects("V2_LEGACY_REQUIRED",()->LegacySnbt.requireLegacy("{grid:16,t:{},tiles:1}"));
        rejects("V2_LEGACY_REQUIRED",()->LegacySnbt.requireLegacy("{tiles:[],children:[{t:{},tiles:1}]}"));
        rejects("V2_LEGACY_REQUIRED",()->LegacySnbt.requireLegacy("{tiles:[I;1,2,3]}"));
        rejects("V2_LEGACY_REQUIRED",()->LegacySnbt.requireLegacy("{tiles:1,names:[]}"));
        rejects("V2_LEGACY_REQUIRED",()->LegacySnbt.requireLegacy("{tiles:[]}trailing"));
        rejects("V2_LEGACY_REQUIRED",()->LegacySnbt.requireLegacy("<html>proxy</html>"));
        rejects("V2_LEGACY_REQUIRED",()->LegacySnbt.utf8(new byte[]{(byte)0xc3,0x28}));
        for(String locale:new String[]{"zh-Hans","zh-Hant","en","ja","ko","de","fr"}) {
            String consent=V2Errors.message("V2_WEB_CONSENT_REQUIRED","CLIENT_ERROR",null,null,locale);
            check(consent.contains("V2_WEB_CONSENT_REQUIRED")&&!consent.startsWith("ltsmodelshare"),"Consent explanation "+locale);
            String disabled=V2Errors.message("M01-006","SYSTEM_USER_MODULE_ERROR","secret=do-not-display",null,locale);
            check(disabled.contains("SYSTEM_USER_MODULE_ERROR/M01-006")&&!disabled.contains("do-not-display"),"Canonical error "+locale);
        }
        check(!V2Errors.message("UNKNOWN_CODE","COMMON_ERROR","https://bad/?access=secret",null,"en").contains("secret"),"Unknown error never leaks URL");
    }
    public static void main(String[] args) throws Exception {
        SessionChecks.run();
        String library=V2Contract.libraryPath(2,"en");
        check(library.startsWith("/api/v2/studio/library?"),"Own library uses authenticated dedicated endpoint");
        check(library.contains("page=2")&&library.contains("size=100")&&library.contains("locale=en"),"Own library has explicit pagination and language");
        check(!library.contains("authorId"),"Library owner is determined by the authenticated session");
        dtoFixtures();queryFixtures();uploadFixtures();transportFixtures();imageFixtures();dialectFixtures();
        System.out.println("V2 API contract fixtures passed ("+checks+" checks); no live services contacted.");
    }
}
