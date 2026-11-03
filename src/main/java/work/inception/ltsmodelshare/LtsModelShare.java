package work.inception.ltsmodelshare;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * LittleTiles Model Share · Forge 1.20.1 入口。
 * 功能以 1.12.2 版本为基准逐步移植。
 */
@Mod(LtsModelShare.MODID)
public final class LtsModelShare {
    public static final String MODID = "ltsmodelshare";
    /** Version this build was compiled for; used by the "only this version" filters. */
    public static final String GAME_VERSION = "1.20.1";
    public static final String MOD_LOADER = "neoforge";
    public static final Logger LOG = LogUtils.getLogger();

    public LtsModelShare() {
        MinecraftForge.EVENT_BUS.register(this);
        work.inception.ltsmodelshare.network.Network.register();
        LOG.info("LittleTiles Model Share 1.20.1 初始化");
    }
}
