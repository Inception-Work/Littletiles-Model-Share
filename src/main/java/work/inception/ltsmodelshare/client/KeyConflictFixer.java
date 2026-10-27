package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import work.inception.ltsmodelshare.LtsModelShare;

/**
 * 按键冲突自愈。
 *
 * <p>踩过的坑：我们的面板键默认是 <b>M</b>，而 LittleTiles 的 `little.mark`（标记）
 * 默认也是 M —— 两个绑定占同一个键时，LittleTiles 先吃掉按键，按 M 就什么都不会发生
 * （用户表现为"按 M 打不开"）。</p>
 *
 * <p>做法：进游戏第一帧检查一次，如果我们的键和别人撞了，就按候选列表找一个空闲键
 * （I / K / J / L / P / ] / F8）挪过去并保存 options.txt，同时在日志里说明。</p>
 */
public class KeyConflictFixer {

    private static final int[] CANDIDATES = {
            Keyboard.KEY_I, Keyboard.KEY_K, Keyboard.KEY_J,
            Keyboard.KEY_L, Keyboard.KEY_P, Keyboard.KEY_RBRACKET, Keyboard.KEY_F8
    };

    private boolean done;
    private boolean announced;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings == null || work.inception.ltsmodelshare.proxy.ClientProxy.keyOpen == null) {
            return;
        }
        done = true;
        try {
            fixIfConflicting(mc);
        } catch (Throwable t) {
            LtsModelShare.LOG.warn("按键冲突检查失败：{}", t.toString());
        }
    }

    private void fixIfConflicting(Minecraft mc) {
        KeyBinding ours = work.inception.ltsmodelshare.proxy.ClientProxy.keyOpen;
        int current = ours.getKeyCode();
        if (current == Keyboard.KEY_NONE) {
            return; // 玩家自己解绑了，别管
        }

        KeyBinding clash = findClash(mc, ours, current);
        if (clash == null) {
            return;
        }

        LtsModelShare.LOG.warn("按键冲突：{} 和 {} 都绑在 {}，把我们的挪走",
                ours.getKeyDescription(), clash.getKeyDescription(), Keyboard.getKeyName(current));

        for (int candidate : CANDIDATES) {
            if (findClash(mc, ours, candidate) == null) {
                ours.setKeyCode(candidate);
                KeyBinding.resetKeyBindingArrayAndHash();
                mc.gameSettings.saveOptions();
                announced = true;
                LtsModelShare.LOG.info("面板键已从 {} 改成 {}（可在「控制 → 按键绑定」里改）",
                        Keyboard.getKeyName(current), Keyboard.getKeyName(candidate));
                return;
            }
        }
        LtsModelShare.LOG.warn("候选键全被占用，请手动在「控制 → 按键绑定」里改");
    }

    /** 有没有别的绑定占着这个键 */
    private KeyBinding findClash(Minecraft mc, KeyBinding ours, int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return null;
        }
        for (KeyBinding kb : mc.gameSettings.keyBindings) {
            if (kb != ours && kb.getKeyCode() == keyCode) {
                return kb;
            }
        }
        return null;
    }
}
