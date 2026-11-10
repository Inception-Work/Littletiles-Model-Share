package work.inception.ltsmodelshare.api;

import java.util.List;
import com.google.gson.Gson;
import work.inception.ltsmodelshare.ModConfig;

public final class GridQueryChecks {
    private static int checks;
    private static void check(boolean condition) {checks++;if(!condition)throw new AssertionError("Grid query check "+checks);}
    public static void main(String[] args) {
        Dtos.CardQuery query=new Dtos.CardQuery("sample","latest",2,12,"en");
        check(!V2Api.cardsPath(query).contains("&grid="));
        query.authorId=9L;check(V2Api.cardsPath(query).startsWith("/authors/9/works?"));
        query.grid=32;query.tagCodes=List.of("building");query.sourceLoader="forge";query.sourceGameVersions=List.of("1.20.1");
        String path=V2Api.cardsPath(query);
        check(path.startsWith("/works?"));check(path.contains("&grid=32"));check(path.contains("&authorId=9"));
        check(path.contains("&tagCodes=building"));check(path.contains("&sourceLoader=forge"));
        check(path.contains("&sourceGameVersions=1.20.1"));check(path.contains("&page=2"));
        query.grid=null;
        path=V2Api.cardsPath(query);
        check(path.startsWith("/works?"));check(path.contains("&authorId=9"));check(path.contains("&tagCodes=building"));
        query.sourceLoader=null;query.sourceGameVersions=null;
        check(V2Api.cardsPath(query).contains("&tagCodes=building"));
        query.tagCodes=null;check(V2Api.cardsPath(query).startsWith("/authors/9/works?"));
        Gson gson=new Gson();ModConfig preferences=gson.fromJson("{}",ModConfig.class);
        check(!preferences.updateNever);preferences.updateNever=true;
        check(gson.fromJson(gson.toJson(preferences),ModConfig.class).updateNever);
        System.out.println("Grid query combinations and update preference checks passed: "+checks);
    }
}
