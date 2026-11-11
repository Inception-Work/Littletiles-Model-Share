package work.inception.ltsmodelshare.api;

import work.inception.ltsmodelshare.api.*;
import com.google.gson.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import work.inception.v2.security.AuthRepo;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static work.inception.ltsmodelshare.api.V2Contract.*;
import work.inception.v2.content.snbt.SnbtConverter;
import work.inception.v2.content.snbt.SnbtDialect;

/** Real HTTP against the owned fixture database ONLY. Never accepts an arbitrary write target. */
public class LiveV2Checks {
    static final String BASE="http://127.0.0.1:18126";
    static final V2Http HTTP=new V2Http();
    static final List<Map<String,Object>> EVIDENCE=new ArrayList<>();
    static final String LEGACY="{grid:16,tiles:[{tile:{block:\"minecraft:stone\",meta:0},boxes:[[I;0,0,0,16,16,16]]}]}";
    static JdbcTemplate db;
    static String owner,peer,reviewer;
    static long ownerId,peerId;
    static int failures;
    static void record(String name,boolean pass,Object... kv) {
        Map<String,Object> row=new LinkedHashMap<>();row.put("test",name);row.put("pass",pass);
        for(int i=0;i<kv.length;i+=2)row.put(kv[i].toString(),kv[i+1]);
        EVIDENCE.add(row);if(!pass)failures++;
        System.out.println((pass?"PASS ":"GAP ")+name);
    }
    static V2Http.Response call(String method,String path,JsonElement input,String token) throws Exception {
        return HTTP.request(BASE,path,method,input==null?null:input.toString().getBytes(StandardCharsets.UTF_8),
            "application/json",token,20000,4*1024*1024,"en");
    }
    static JsonObject envelope(V2Http.Response r) { return object(new JsonParser().parse(r.text())); }
    static JsonObject ok(String name,String method,String path,JsonElement input,String token) throws Exception {
        V2Http.Response r=call(method,path,input,token);JsonObject e=envelope(r);
        record(name,r.status==200&&flag(e,"success"),"method",method,"path",path.split("\\?")[0],"status",r.status,"code",text(e,"error_code"));
        if(r.status!=200||!flag(e,"success"))throw new IOException("prerequisite:"+name);
        JsonElement p=e.get("payload");
        if(p.isJsonObject())return p.getAsJsonObject();
        JsonObject out=new JsonObject();out.add("value",p);return out;
    }
    static void denied(String name,String method,String path,JsonElement input,String token,int expected) throws Exception {
        V2Http.Response r=call(method,path,input,token);JsonObject e=envelope(r);
        record(name,r.status==expected,"method",method,"path",path.split("\\?")[0],"status",r.status,"code",text(e,"error_code"),"type",text(e,"error_type"));
    }
    static String seed(String name,String role) {
        AuthRepo repo=new AuthRepo(db,null);
        long id=repo.insertAccount(name,name);repo.ensureProfile(id,name);repo.insertEmail(id,name+"@example.invalid",name+"@example.invalid");
        String password=UUID.randomUUID().toString()+"Aa9!";
        repo.insertPassword(id,new BCryptPasswordEncoder().encode(password));repo.grantRole(id,role,"default",null);
        return password;
    }
    static JsonObject login(String name,String password) throws Exception {
        JsonObject b=body("username",name);b.addProperty("password",password);
        return ok("password login "+name,"POST","/api/v2/auth/login",b,null);
    }
    static long upload(String name,byte[] content,String purpose,String token) throws Exception {
        String boundary="audit"+UUID.randomUUID().toString().replace("-","");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+
            (name.endsWith(".png")?"image/png":"text/plain")+" \r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(content);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        V2Http.Response r=HTTP.request(BASE,"/api/v2/uploads?purpose="+purpose+"&module=community","POST",bytes.toByteArray(),
            "multipart/form-data; boundary="+boundary,token,20000,1024*1024,"en");
        JsonObject e=envelope(r);record("upload "+purpose,r.status==200,"status",r.status,"code",text(e,"error_code"));
        if(r.status!=200)throw new IOException("upload prerequisite");
        JsonObject p=object(e.get("payload"));return positive(p,"resourceId");
    }
    static JsonObject draftBody(long source,long image,String visibility) throws Exception {
        Dtos.WorkSaveRequest req=new Dtos.WorkSaveRequest();req.kind="single";req.title="Isolated Model Share";req.summary="Owned integration fixture";
        req.sourceLoader="forge";req.sourceGameVersion="1.12.2";req.sourceLocale="en";req.visibility=visibility;
        req.images=Collections.singletonList("/api/v2/resources/"+image+"/content");
        req.items=Collections.singletonList(new Dtos.WorkSaveRequest.SaveItem(source,"stone","1.12.2"));
        JsonObject b=saveBody(req,Collections.singletonMap(req.images.get(0),image));
        if("unlisted".equals(visibility))b.addProperty("accessCode","audit-code");
        return b;
    }
    static long receipt(long work,long version) throws Exception {
        JsonObject a=ok("read current upload agreement","GET","/api/v2/agreements/upload_rights?locale=en",null,owner);
        // Fixture actor explicitly accepts the actual text fetched above, not a production user's consent.
        JsonObject b=body("decision","granted");b.addProperty("entryPoint","isolated_model_share_test");
        b.addProperty("scopeType","work_upload");b.addProperty("scopeId",work);b.addProperty("scopeVersion",version);
        b.add("locale",a.get("locale"));b.add("contentHash",a.get("contentHash"));b.add("textId",a.get("textId"));
        JsonObject result=ok("explicit fixture consent receipt","POST","/api/v2/agreements/"+positive(a,"releaseId")+"/consent",b,owner);
        record("receipt binding",number(result,"scopeId",0)==work&&number(result,"scopeVersion",0)==version);
        return positive(result,"receiptId");
    }
    static long publish(JsonObject saved) throws Exception {
        long work=positive(saved,"workId"),version=positive(saved,"version");
        JsonObject b=body("expectedVersion",version);b.addProperty("keepOldOnline",true);b.addProperty("consentReceiptId",receipt(work,version));
        JsonObject submitted=ok("submit real draft","POST","/api/v2/works/"+work+"/submit",b,owner);
        long review=positive(submitted,"reviewId");
        JsonObject draft=ok("read owner draft","GET","/api/v2/works/"+work+"/draft",null,owner);
        long item=positive(object(draft.getAsJsonArray("items").get(0)),"itemId");
        ok("review fixture item","POST","/api/v2/admin/reviews/"+review+"/items/"+item,body("status","passed"),reviewer);
        ok("approve fixture publication","POST","/api/v2/admin/reviews/"+review+"/approve",new JsonObject(),reviewer);
        return item;
    }
    static String grant(long work) throws Exception {
        JsonObject d=ok("exchange share code","POST","/api/v2/works/"+work+"/access?locale=en",body("code","audit-code"),peer);
        return text(d,"accessGrant");
    }
    static void run() throws Exception {
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/lts112_v2_audit_20261107?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC","root","");
        db=new JdbcTemplate(ds);
        if(!"lts112_v2_audit_20261107".equals(db.queryForObject("SELECT DATABASE()",String.class)))throw new SecurityException("Database mismatch");
        String suffix=Long.toString(System.currentTimeMillis(),36);
        String n="lts_owner_"+suffix,p="lts_peer_"+suffix,r="lts_review_"+suffix;
        String pass=seed(n,"USER"),peerPass=seed(p,"USER"),reviewPass=seed(r,"ADMIN");
        JsonObject first=login(n,pass);
        owner=text(first,"accessToken");String refresh=text(first,"refreshToken");
        ownerId=positive(ok("authenticated me","GET","/api/v2/auth/me",null,owner),"id");
        JsonObject rotated=ok("refresh rotation","POST","/api/v2/auth/refresh",body("refreshToken",refresh),null);
        record("refresh credential rotates",!refresh.equals(text(rotated,"refreshToken")) && text(rotated,"accessToken") != null);
        owner=text(rotated,"accessToken");refresh=text(rotated,"refreshToken");
        // Use a separate session for refresh replay: some policies revoke the entire affected session.
        JsonObject replay=login(n,pass);String old=text(replay,"refreshToken");
        ok("replay setup rotation","POST","/api/v2/auth/refresh",body("refreshToken",old),null);
        denied("old refresh rejected","POST","/api/v2/auth/refresh",body("refreshToken",old),null,410);
        JsonObject logged=login(n,pass);
        ok("logout session","POST","/api/v2/auth/logout",new JsonObject(),text(logged,"accessToken"));
        denied("logout invalidates access","GET","/api/v2/auth/me",null,text(logged,"accessToken"),401);
        denied("logout invalidates refresh","POST","/api/v2/auth/refresh",body("refreshToken",text(logged,"refreshToken")),null,410);
        peer=text(login(p,peerPass),"accessToken");peerId=positive(ok("peer me","GET","/api/v2/auth/me",null,peer),"id");
        reviewer=text(login(r,reviewPass),"accessToken");
        apiClientChecks(n,pass,p,peerPass);
        ok("follow author","POST","/api/v2/accounts/"+ownerId+"/follow",body("value",true),peer);
        record("follow state true",flag(ok("read follow state","GET","/api/v2/accounts/"+ownerId+"/follow-state",null,peer),"following"));
        ok("unfollow author","POST","/api/v2/accounts/"+ownerId+"/follow",body("value",false),peer);
        record("follow state false",!flag(ok("read cancelled follow","GET","/api/v2/accounts/"+ownerId+"/follow-state",null,peer),"following"));
        long snbt=upload("stone.snbt",LEGACY.getBytes(StandardCharsets.UTF_8),"work_snbt",owner);
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB),"png",png);
        long image=upload("screenshot.png",png.toByteArray(),"work_image",owner);
        JsonObject b=draftBody(snbt,image,"public");
        JsonObject saved=ok("save aggregate draft","POST","/api/v2/works/save",b,owner);
        long work=positive(saved,"workId"),version=positive(saved,"version");
        JsonObject conflict=new JsonParser().parse(b.toString()).getAsJsonObject();conflict.addProperty("expectedVersion",version-1);
        denied("draft version conflict","PUT","/api/v2/works/"+work+"/save",conflict,owner,409);
        JsonObject agreement=ok("read unseen agreement","GET","/api/v2/agreements/upload_rights?locale=en",null,owner);
        boolean refused=false;try{receiptBody(agreement,work,version);}catch(IOException expected){refused=expected.getMessage().contains("V2_WEB_CONSENT_REQUIRED");}
        record("mod refuses unseen consent",refused);
        JsonObject missing=body("expectedVersion",version);missing.addProperty("keepOldOnline",true);
        denied("submit requires receipt","POST","/api/v2/works/"+work+"/submit",missing,owner,428);
        ok("draft survives missing consent","GET","/api/v2/works/"+work+"/draft",null,owner);
        long item=publish(saved);
        for(String locale:new String[]{"zh-Hans","zh-Hant","en","ja","ko","de","fr"}){
            JsonObject detail=ok("detail locale "+locale,"GET","/api/v2/works/"+work+"?locale="+locale,null,peer);
            Dtos.WorkDetail mapped=V2Contract.detail(detail);record("detail mapping "+locale,mapped.workId==work&&mapped.items.size()==1);
        }
        JsonObject detail=ok("published geometry metadata","GET","/api/v2/works/"+work,null,peer);
        JsonObject itemJson=object(detail.getAsJsonArray("items").get(0));
        record("grid and boxCount present",has(itemJson,"grid")&&has(itemJson,"boxCount"),
            "fields",new ArrayList<>(itemJson.entrySet().stream().map(Map.Entry::getKey).toList()));
        JsonObject info=ok("legacy download info","GET","/api/v2/works/download-info/"+item+"?version=1.12",null,peer);
        record("raw download size present",number(info,"sizeBytes",0)>0,"fields",info.entrySet().stream().map(Map.Entry::getKey).toList());
        V2Http.Response download=HTTP.request(BASE,text(info,"assetUrl"),"GET",null,null,peer,20000,10*1024*1024,"en");
        LegacySnbt.requireLegacy(LegacySnbt.utf8(download.bytes));
        record("legacy returned bytes match upload",Arrays.equals(download.bytes,LEGACY.getBytes(StandardCharsets.UTF_8)),
            "status",download.status,"bytes",download.bytes.length,"sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(download.bytes)));
        denied("source download needs login","GET",text(info,"assetUrl"),null,null,401);
        ok("favorite true","POST","/api/v2/works/"+work+"/favorite",body("value",true),peer);
        record("favorite visible",flag(ok("read favorite state","GET","/api/v2/works/"+work,null,peer),"favorited"));
        ok("favorite false","POST","/api/v2/works/"+work+"/favorite",body("value",false),peer);
        ok("author localized works","GET","/api/v2/authors/"+ownerId+"/works?locale=ja&sort=latest&page=1&size=1",null,peer);
        ok("localized bookmarks","GET","/api/v2/authors/"+peerId+"/bookmarks?locale=de&page=1&size=1",null,peer);
        JsonObject link=ok("save unlisted draft","POST","/api/v2/works/save",draftBody(snbt,image,"unlisted"),owner);
        long linkId=positive(link,"workId"),linkItem=publish(link);
        denied("share code required","GET","/api/v2/works/"+linkId,null,peer,403);
        denied("wrong share code","POST","/api/v2/works/"+linkId+"/access",body("code","wrong-code"),peer,403);
        String cap=grant(linkId);
        ok("share authorized download info","GET","/api/v2/works/download-info/"+linkItem+"?version=1.12&access="+encode(cap),null,peer);
        System.out.println("Waiting for the real 120-second share capability expiry; owned fixture only.");
        Thread.sleep(121000);
        denied("naturally expired share grant","GET","/api/v2/works/"+linkId+"?access="+encode(cap),null,peer,410);
        cap=grant(linkId);
        db.update("UPDATE lt_work SET share_version=share_version+1 WHERE work_id=?",linkId);
        denied("revoked grant","GET","/api/v2/works/"+linkId+"?access="+encode(cap),null,peer,410);
        // This independent fixture mutation creates privacy/takedown boundary conditions.
        db.update("UPDATE lt_work SET visibility='private' WHERE work_id=?",linkId);
        denied("private detail hidden","GET","/api/v2/works/"+linkId,null,owner,404);
        denied("private source hidden","GET","/api/v2/works/"+linkId+"/download/"+linkItem,null,owner,404);
        db.update("UPDATE lt_work SET enforcement='taken_down' WHERE work_id=?",work);
        denied("taken down detail","GET","/api/v2/works/"+work,null,peer,404);
        denied("taken down source","GET","/api/v2/works/"+work+"/download/"+item,null,peer,404);
        db.update("UPDATE lt_work SET enforcement='none' WHERE work_id=?",work);
        // A new publication replaces the old item binding; old source URLs must not survive.
        JsonObject beforeUpdate=ok("read draft before revision","GET","/api/v2/works/"+work+"/draft",null,owner);
        JsonObject changed=draftBody(snbt,image,"public");changed.addProperty("expectedVersion",positive(beforeUpdate,"version"));
        JsonObject updated=ok("update draft version","PUT","/api/v2/works/"+work+"/save",changed,owner);
        long nextItem=publish(updated);
        denied("old revision download info denied","GET","/api/v2/works/download-info/"+item+"?version=1.12",null,peer,404);
        denied("old revision source denied","GET","/api/v2/works/"+work+"/download/"+item,null,peer,404);
        // Generate a valid modern fixture using the pure website converter, then deliberately mislabel its item.
        String modern=SnbtConverter.convert(LEGACY,
                SnbtDialect.MODERN_119).text();
        long modernSource=upload("modern.snbt",modern.getBytes(StandardCharsets.UTF_8),"work_snbt",owner);
        JsonObject mislabeled=ok("save modern bytes with legacy label","POST","/api/v2/works/save",draftBody(modernSource,image,"public"),owner);
        long modernItem=publish(mislabeled);
        JsonObject wrongInfo=ok("legacy target on mislabeled modern source","GET","/api/v2/works/download-info/"+modernItem+"?version=1.12",null,peer);
        V2Http.Response wrongBytes=HTTP.request(BASE,text(wrongInfo,"assetUrl"),"GET",null,null,peer,20000,1024*1024);
        boolean rejected=false;try{LegacySnbt.requireLegacy(LegacySnbt.utf8(wrongBytes.bytes));}catch(IOException expected){rejected=true;}
        record("server actually converts mislabeled modern source",!rejected,"status",wrongBytes.status,
            "version",text(wrongInfo,"version"),"converted",flag(wrongInfo,"converted"),"bytes",wrongBytes.bytes.length);
        record("mod blocks wrong dialect before game import",rejected);
        JsonObject patBody=body("name","isolated matrix");patBody.add("scopes",JSON.toJsonTree(Arrays.asList("read","write","upload","download")));
        String pat=text(ok("create scoped fixture PAT","POST","/api/v2/pats",patBody,owner),"token");
        ok("PAT me read","GET","/api/v2/auth/me",null,pat);
        ok("PAT works read","GET","/api/v2/works?page=1&size=1",null,pat);
        for(String path:Arrays.asList("/api/v2/tags","/api/v2/authors/"+ownerId,"/api/v2/agreements/upload_rights","/api/v2/accounts/"+peerId+"/follow-state"))
            denied("PAT denied "+path,"GET",path,null,pat,403);
        denied("PAT favorite denied","POST","/api/v2/works/"+work+"/favorite",body("value",true),pat,403);
        denied("PAT share exchange denied","POST","/api/v2/works/"+work+"/access",body("code","test"),pat,403);
        db.update("UPDATE sec_account SET status='disabled' WHERE account_id=?",ownerId);
        denied("disabled account access","GET","/api/v2/auth/me",null,owner,401);
        denied("disabled account refresh","POST","/api/v2/auth/refresh",body("refreshToken",refresh),null,410);
        JsonObject disabled=body("username",n);disabled.addProperty("password",pass);
        denied("disabled login","POST","/api/v2/auth/login",disabled,null,403);

    }
    static final class MemoryConfig extends work.inception.ltsmodelshare.ModConfig {
        @Override public void save() { } // Never read/write the user's Minecraft configuration.
    }
    interface Operation<T> { void run(ApiClient.Callback<T> cb); }
    static <T> T await(Operation<T> op) throws Exception {
        java.util.concurrent.CompletableFuture<T> future=new java.util.concurrent.CompletableFuture<>();
        op.run(new ApiClient.Callback<T>() {
            public void ok(T value){future.complete(value);}
            public void fail(String error){future.completeExceptionally(new IOException(error));}
        });
        return future.get(30,java.util.concurrent.TimeUnit.SECONDS);
    }
    static void apiClientChecks(String name,String password,String peerName,String peerPassword) throws Exception {
        MemoryConfig cfg=new MemoryConfig();cfg.baseUrl=BASE;
        ApiClient.environment=new ApiClient.Environment(){
            public work.inception.ltsmodelshare.ModConfig config(){return cfg;}
            public String locale(){return "en";}
            public String translate(String key,Object...args){return args.length>0?String.valueOf(args[0]):key;}
        };
        LiveV2Checks.<Dtos.LoginPayload>await(cb->ApiClient.login(name,password,cb));
        record("ApiClient actual login",cfg.loggedIn());
        Long id=LiveV2Checks.<Long>await(ApiClient::resolveUserId);
        record("ApiClient actual me",id==ownerId);
        String before=cfg.refreshToken;
        // Invalidate only the signature of this fixture access token, preserving sid/sub.
        String[] token=cfg.token.split("\\.");
        token[2]=(token[2].charAt(0)=='A'?"B":"A")+token[2].substring(1);
        cfg.token=String.join(".",token);
        denied("fixture access rejected before retry","GET","/api/v2/auth/me",null,cfg.token,401);
        id=LiveV2Checks.<Long>await(ApiClient::resolveUserId);
        record("ApiClient 401 refresh retries actual HTTP",id==ownerId&&!before.equals(cfg.refreshToken));
        Dtos.SnbtUpload uploaded=LiveV2Checks.<Dtos.SnbtUpload>await(cb->ApiClient.uploadSnbt(LEGACY,"isolated",cb));
        Path shot=Paths.get("fixture-screen.png");ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB),"png",shot.toFile());
        String url=LiveV2Checks.<String>await(cb->ApiClient.uploadImage(shot.toFile(),cb));
        Dtos.WorkSaveRequest req=new Dtos.WorkSaveRequest();req.kind="single";req.title="ApiClient draft";
        req.summary="isolated HTTP";req.sourceLoader="forge";req.sourceGameVersion="1.12.2";req.sourceLocale="en";
        req.images=Collections.singletonList(url);req.items=Collections.singletonList(new Dtos.WorkSaveRequest.SaveItem(uploaded.blobId,"stone","1.12.2"));
        long draft=LiveV2Checks.<Long>await(cb->ApiClient.saveWork(req,cb));
        record("ApiClient actual upload image and draft",draft>0);
        boolean consentRefused=false;
        try{LiveV2Checks.<Long>await(ApiClient::agreeUploadRights);}catch(java.util.concurrent.ExecutionException expected){
            consentRefused=expected.getCause().getMessage().contains("V2_WEB_CONSENT_REQUIRED");
        }
        record("ApiClient preserves draft for website consent",consentRefused);
        ok("ApiClient draft still exists","GET","/api/v2/works/"+draft+"/draft",null,cfg.token);
        Dtos.Page<Dtos.WorkCard> cards=LiveV2Checks.<Dtos.Page<Dtos.WorkCard>>await(cb->ApiClient.listCards(new Dtos.CardQuery("","latest",1,2,"ja"),cb));
        record("ApiClient real pagination",cards.current==1&&cards.size==2);
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        ApiClient.io(()->{entered.countDown();try{release.await();}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});
        entered.await();
        java.util.concurrent.CompletableFuture<String> switched=new java.util.concurrent.CompletableFuture<>();
        ApiClient.resolveUserId(new ApiClient.Callback<Long>(){
            public void ok(Long value){switched.complete("WRONG_ACCOUNT");}
            public void fail(String error){switched.complete(error);}
        });
        JsonObject peerLogin=login(peerName,peerPassword);cfg.token=text(peerLogin,"accessToken");cfg.refreshToken=text(peerLogin,"refreshToken");
        release.countDown();
        record("queued action cannot switch account",switched.get(10,java.util.concurrent.TimeUnit.SECONDS).contains("V2_SESSION_CHANGED"));
        String old=cfg.token;
        ApiClient.revokeRefreshQuietly(cfg.refreshToken);cfg.clearSession();
        java.util.concurrent.CompletableFuture<Boolean> drained=new java.util.concurrent.CompletableFuture<>();
        ApiClient.io(()->drained.complete(true));drained.get(20,java.util.concurrent.TimeUnit.SECONDS);
        denied("ApiClient captures logout session","GET","/api/v2/auth/me",null,old,401);
        LiveV2Checks.<Dtos.LoginPayload>await(cb->ApiClient.login(name,password,cb));
        db.update("UPDATE sec_session SET revoked_at=UTC_TIMESTAMP(3) WHERE account_id=?",ownerId);
        boolean failed=false;
        try{LiveV2Checks.<Long>await(ApiClient::resolveUserId);}catch(java.util.concurrent.ExecutionException expected){failed=true;}
        record("ApiClient revoked session clears credentials",failed&&!cfg.loggedIn()&&cfg.refreshToken.isEmpty());
        // Replace the scenario's revoked owner session; no user credential persists.
        owner=text(login(name,password),"accessToken");
    }

    public static void main(String[] args) throws Exception {
        try{run();}catch(Exception e){record("scenario interrupted",false,"exception",e.getClass().getSimpleName(),
            "stage",e instanceof IOException?e.getMessage():"see fixture runner");}
        Files.write(Paths.get("evidence-http.json"),new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE).getBytes(StandardCharsets.UTF_8));
        System.out.println("Recorded "+EVIDENCE.size()+" real HTTP/fixture checks; gaps="+failures+"; no credentials recorded.");
    }
}
