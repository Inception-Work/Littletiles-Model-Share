package work.inception.ltsmodelshare.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.command.CommandLts;

/** Client-only key binding, tick polling and client command registration. */
public final class ClientInit {
    public static final KeyMapping OPEN = new KeyMapping("key.ltsmodelshare.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.ltsmodelshare");
    private static boolean debugOpened;

    private ClientInit() { }

    @Mod.EventBusSubscriber(modid = LtsModelShare.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() { }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    @Mod.EventBusSubscriber(modid = LtsModelShare.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() { }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (OPEN.consumeClick()) {
                InputHandler.openPanel();
            }
            BlueprintClient.tick();
                work.inception.ltsmodelshare.client.UpdateClient.tick();
            // Dev-only smoke hook: -Dltsmodelshare.debugOpen=true opens the browser once.
            if (Boolean.getBoolean("ltsmodelshare.debugOpen") && !debugOpened
                    && (net.minecraft.client.Minecraft.getInstance().screen == null
                    || net.minecraft.client.Minecraft.getInstance().screen
                    instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                debugOpened = true;
                InputHandler.openPanel();
            }
        }

        @SubscribeEvent
        public static void commands(RegisterClientCommandsEvent event) {
            CommandLts.register(event.getDispatcher());
        }
    }
}
