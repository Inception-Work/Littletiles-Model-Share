package work.inception.ltsmodelshare;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * LittleTiles Model Share · NeoForge 1.21.1 入口。
 * 功能以 1.12.2 版本为基准逐步移植。
 */
@Mod(LtsModelShare.MODID)
public final class LtsModelShare {
    public static final String MODID = "ltsmodelshare";
    /** Version this build was compiled for; used by the "only this version" filters. */
    public static final String GAME_VERSION = "1.21.1";
    public static final Logger LOG = LogUtils.getLogger();

    public LtsModelShare(IEventBus modEventBus) {
        work.inception.ltsmodelshare.network.Network.register(modEventBus);
        LOG.info("LittleTiles Model Share 1.21.1 初始化");
    }
}
