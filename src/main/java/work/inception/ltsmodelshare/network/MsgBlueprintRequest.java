package work.inception.ltsmodelshare.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import work.inception.ltsmodelshare.server.BlueprintServer;

import java.nio.charset.StandardCharsets;

/**
 * 客户端 → 服务端：请把这段 SNBT 变成蓝图放进我背包。
 *
 * <p>SNBT 可能上万字符（一个模型 export 出来 6k 很常见），
 * {@link ByteBufUtils#writeUTF8String} 用 short 存长度（上限 32767 字符），
 * 大结构会直接炸，所以这里自己用 int 长度 + UTF-8 字节。</p>
 */
public class MsgBlueprintRequest implements IMessage {

    /** 防御：超过这个长度直接拒绝（服务端也会再校验一次） */
    public static final int MAX_CHARS = 2 * 1024 * 1024;

    private String snbt = "";
    private String title = "";

    public MsgBlueprintRequest() {
    }

    public MsgBlueprintRequest(String snbt, String title) {
        this.snbt = snbt == null ? "" : snbt;
        this.title = title == null ? "" : title;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        snbt = read(buf);
        title = read(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        write(buf, snbt);
        write(buf, title);
    }

    private static void write(ByteBuf buf, String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    private static String read(ByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > MAX_CHARS * 4) {
            return "";
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<MsgBlueprintRequest, IMessage> {

        @Override
        public IMessage onMessage(MsgBlueprintRequest msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            final String snbt = msg.snbt;
            final String title = msg.title;
            // 网络线程不能直接动背包，切回服务端主线程
            player.getServer().addScheduledTask(() -> BlueprintServer.give(player, snbt, title));
            return null;
        }
    }
}
