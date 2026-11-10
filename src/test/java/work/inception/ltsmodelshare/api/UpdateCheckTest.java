package work.inception.ltsmodelshare.api;
import work.inception.ltsmodelshare.api.UpdateCheck;
public class UpdateCheckTest {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value)throw new AssertionError("check "+checks); }
    private static String release(String version,String channel,String loader,String game) {
        return "{\"product\":\"model_share\",\"version\":\""+version+"\",\"channel\":\""+channel+"\",\"loader\":\""+loader+"\",\"gameVersion\":\""+game+"\",\"changelog\":\"fallback\",\"changelogI18n\":{\"en\":\"English changes\",\"ja\":\"Japanese changes\"}}";
    }
    private static String envelope(String... values) { return "{\"success\":true,\"payload\":{\"items\":["+String.join(",",values)+"]}}"; }
    public static void main(String[] args)throws Exception {
        check(UpdateCheck.compare("0.1.10","0.1.9")>0);
        check(UpdateCheck.compare("v1.0.0","1.0") == 0);
        check(UpdateCheck.compare("1.0.0","1.0.0-beta.9")>0);
        check(UpdateCheck.compare("1.0-beta.10","1.0-beta.2")>0);
        check(UpdateCheck.compare("1.0-beta.1","1.0-beta.1.1")<0);
        String json=envelope(release("0.1.10","test","forge","1.12.2"),release("0.2.0","stable","forge","1.12.2"),
                release("9.0","test","neoforge","1.12.2"),release("8.0","test","forge","1.20.1"));
        UpdateCheck.Release test=UpdateCheck.select(json,"0.1.9","test","forge","1.12.2","en");
        check(test!=null&&test.version.equals("0.1.10")&&test.changelog.equals("English changes"));
        UpdateCheck.Release stable=UpdateCheck.select(json,"0.1.9","stable","forge","1.12.2","ja");
        check(stable!=null&&stable.version.equals("0.2.0")&&stable.changelog.equals("Japanese changes"));
        check(UpdateCheck.select(json,"0.1.10","test","forge","1.12.2","en")==null);
        check(UpdateCheck.select(envelope(release("0.1.10","test","forge","1.12.2")),"0.1.9","stable","forge","1.12.2","en")==null);
        check(UpdateCheck.select(json,"0.1.9","test","forge","1.12.2","zh-Hans").changelog.equals("fallback"));
        check(UpdateCheck.select(envelope(),"0.1.9","test","forge","1.12.2","en")==null);
        check(UpdateCheck.select(envelope(release("broken","test","forge","1.12.2")),"0.1.9","test","forge","1.12.2","en")==null);
        check(UpdateCheck.downloadPage("https://www.inception.work/").equals("https://www.inception.work/lt-share-mod"));
        try { UpdateCheck.downloadPage("file:///secret");throw new AssertionError("unsafe URL"); } catch(java.io.IOException expected) { checks++; }
        System.out.println("Update channel/version/locale checks passed: "+checks);
    }
}
