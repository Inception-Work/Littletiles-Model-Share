package work.inception.ltsmodelshare.server;

import com.creativemd.littletiles.common.util.converation.StructureStringUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
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
                reply(player, false, "ltsmodelshare.server.empty");
                return;
            }
            if (snbt.length() > work.inception.ltsmodelshare.network.SnbtCodec.MAX_CHARS) {
                reply(player, false, "ltsmodelshare.server.too_large", snbt.length());
                return;
            }
            BlueprintValidation.requireSupported(snbt);
            ItemStack stack = StructureStringUtils.importStructure(snbt);
            if (stack == null || stack.isEmpty()) {
                reply(player, false, "ltsmodelshare.common.parse_incompatible");
                return;
            }
            BlueprintValidation.requireRenderable(stack);
            // 名字统一用站点标题：LittleTiles 从 SNBT 里取的名字在某些结构上会变成乱码
            // （结构里没有 name 字段时更明显），这里用 UTF-16 的标题覆盖掉，中文一定正常。
            String cleanTitle = title == null ? "" : title.trim();
            if (!cleanTitle.isEmpty()) {
                stack.setStackDisplayName(cleanTitle);
            }
            boolean added = player.inventory.addItemStackToInventory(stack);
            if (!added) {
                player.dropItem(stack, false);
                reply(player, true, "ltsmodelshare.server.dropped", stack.getDisplayName());
                LtsModelShare.LOG.info("背包已满，蓝图已掉落：{}", title);
                return;
            }
            player.inventory.markDirty();
            reply(player, true, "ltsmodelshare.server.created", stack.getDisplayName(), snbt.length());
            LtsModelShare.LOG.info("服务端生成蓝图：player={} title={} 字符数={}",
                    player.getName(), title, snbt.length());
        } catch (Throwable t) {
            LtsModelShare.LOG.warn("服务端生成蓝图失败：{}", t.toString());
            reply(player, false, "ltsmodelshare.server.failed", t.toString());
        }
    }

    private static void reply(EntityPlayerMP player, boolean ok, String key, Object... args) {
        ITextComponent message = new TextComponentTranslation(key, args);
        String text = ITextComponent.Serializer.componentToJson(message);
        // 网络层出任何问题都不能让服务端把玩家踢掉（Forge 在 handler 里抛异常会直接断连）
        try {
            if (PacketHandler.NET != null) {
                PacketHandler.NET.sendTo(new MsgBlueprintResult(ok, text), player);
            }
        } catch (Throwable e) {
            LtsModelShare.LOG.warn("回执发送失败（不影响道具发放）：{}", e.toString());
        }
        // 聊天栏只在开发环境打：发布版不要往玩家聊天框里刷屏（界面里仍有状态文字）
        if (work.inception.ltsmodelshare.DevEnv.isDev()) {
            message.getStyle().setColor(ok ? TextFormatting.GREEN : TextFormatting.RED);
            player.sendMessage(message);
        }
    }
}
