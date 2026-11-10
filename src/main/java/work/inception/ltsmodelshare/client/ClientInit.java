package work.inception.ltsmodelshare.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.command.CommandLts;

/** Client-only key binding, tick polling and client command registration. */
public final class ClientInit {
    public static final KeyMapping OPEN = new KeyMapping("key.ltsmodelshare.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.ltsmodelshare");
    private static boolean debugOpened;

    private ClientInit() { }

    @EventBusSubscriber(modid = LtsModelShare.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() { }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    @EventBusSubscriber(modid = LtsModelShare.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() { }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
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
