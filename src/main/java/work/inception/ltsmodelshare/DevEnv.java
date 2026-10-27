package work.inception.ltsmodelshare;

import net.minecraft.launchwrapper.Launch;

/**
 * 开发环境判定 —— 用来决定"只在开发时打的东西"。
 *
 * <p>例子：联机取蓝图的各种提示，开发时打聊天框方便调试，
 * 正式发布时不该在玩家聊天栏里刷屏（界面里仍然有状态文字）。</p>
 *
 * <p>1.12 的判定方式是 launchwrapper 的 blackboard 标记
 * {@code fml.deobfuscatedEnvironment}（dev 环境为 true，release 为 false）。</p>
 */
public final class DevEnv {

    private static Boolean cached;

    private DevEnv() {
    }

    public static boolean isDev() {
        if (cached != null) {
            return cached;
        }
        boolean dev = false;
        try {
            Object flag = Launch.blackboard.get("fml.deobfuscatedEnvironment");
            dev = flag instanceof Boolean && (Boolean) flag;
        } catch (Throwable ignored) {
            // launchwrapper 不在（极少数环境）→ 当发布版处理
        }
        cached = dev;
        return dev;
    }
}
