package work.inception.ltsmodelshare.server;

import com.creativemd.littletiles.common.util.converation.StructureStringUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.network.MsgBlueprintResult;
import work.inception.ltsmodelshare.network.PacketHandler;

/**
 * 服务端生成蓝图 —— 联机时唯一的正确做法。
 *
 * <p>客户端只负责把 SNBT 发过来；真正的 {@code importStructure} 和入包都发生在**服务端**，
 * 这样道具是权威的：重进游戏还在、其他玩家也能看到、不会被服务端同步覆盖。</p>
 *
 * <p>安全：这是个"任意玩家可请求服务端创建物品"的入口，所以
 * <ul>
 *   <li>限制文本长度（{@code MsgBlueprintRequest.MAX_CHARS}）</li>
 *   <li>只接受 LittleTiles 自己的结构格式（解析失败就当失败处理）</li>
 *   <li>背包满了就掉落，不丢弃、不越权</li>
 * </ul></p>
 */
public final class BlueprintServer {

    private BlueprintServer() {
    }

    public static void give(EntityPlayerMP player, String snbt, String title) {
        if (player == null) {
            return;
        }
        try {
            if (snbt == null || snbt.trim().isEmpty()) {
                reply(player, false, "结构文本为空");
                return;
            }
            if (snbt.length() > work.inception.ltsmodelshare.network.MsgBlueprintRequest.MAX_CHARS) {
                reply(player, false, "结构文本太大（" + snbt.length() + " 字符），已拒绝");
                return;
            }
            ItemStack stack = StructureStringUtils.importStructure(snbt);
            if (stack == null || stack.isEmpty()) {
                reply(player, false, "解析失败：结构为空或版本不兼容");
                return;
            }
            boolean added = player.inventory.addItemStackToInventory(stack);
            if (!added) {
                player.dropItem(stack, false);
                reply(player, true, "背包满了，蓝图掉在你脚下：" + stack.getDisplayName());
                LtsModelShare.LOG.info("背包已满，蓝图已掉落：{}", title);
                return;
            }
            player.inventory.markDirty();
            reply(player, true, "已生成蓝图：" + stack.getDisplayName() + "（" + snbt.length() + " 字符）");
            LtsModelShare.LOG.info("服务端生成蓝图：player={} title={} 字符数={}",
                    player.getName(), title, snbt.length());
        } catch (Throwable t) {
            LtsModelShare.LOG.warn("服务端生成蓝图失败：{}", t.toString());
            reply(player, false, "生成失败：" + t);
        }
    }

    private static void reply(EntityPlayerMP player, boolean ok, String text) {
        if (PacketHandler.NET != null) {
            PacketHandler.NET.sendTo(new MsgBlueprintResult(ok, text), player);
        }
        // 同时打一行到聊天栏：玩家可能已经关掉我们的界面
        TextComponentString msg = new TextComponentString(
                (ok ? TextFormatting.GREEN : TextFormatting.RED) + "[模型共享] " + text);
        player.sendMessage(msg);
    }
}
