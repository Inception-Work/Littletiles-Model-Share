package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.UpdateCheck;
import work.inception.ltsmodelshare.gui.GuiUpdate;
import static work.inception.ltsmodelshare.client.Translations.tr;

/** One asynchronous check per connection, presented only after a world is ready. */
public final class UpdateClient {
    private static Object connection;
    private static boolean checked;
    private static UpdateCheck.Release pending;
    private static String pendingBase;
    private UpdateClient() { }
    public static void tick() {
        if(ModConfig.get().updateNever)return;
        Minecraft mc=Minecraft.getMinecraft();
        Object live=mc.getConnection();
        if(live!=connection) { connection=live;checked=false;pending=null; }
        if(live==null||mc.world==null||mc.player==null)return;
        if(!checked) {
            checked=true;
            final Object requested=connection;
            final String base=ModConfig.get().baseUrl,locale=Translations.siteLocale();
            ApiClient.io(() -> {
                try {
                    UpdateCheck.Release release=UpdateCheck.check(base,"forge","1.12.2",locale);
                    ApiClient.post(() -> {
                        if(connection==requested&&base.equals(ModConfig.get().baseUrl)) {pending=release;pendingBase=base;}
                    });
                } catch(Exception failure) {
                    work.inception.ltsmodelshare.LtsModelShare.LOG.debug("Release check unavailable: {}",failure.getClass().getSimpleName());
                }
            });
        }
        if(pending==null||mc.currentScreen!=null)return;
        UpdateCheck.Release release=pending;pending=null;
        final String url;
        try { url=UpdateCheck.downloadPage(pendingBase); } catch(Exception invalid) {return;}
        mc.displayGuiScreen(new GuiUpdate(release.version,url,release.changelog));
    }
}
