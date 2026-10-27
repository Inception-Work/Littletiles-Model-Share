package work.inception.ltsmodelshare.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import work.inception.ltsmodelshare.client.BlueprintClient;

import java.nio.charset.StandardCharsets;

/** 服务端 → 客户端：蓝图生成结果（成功/失败 + 一句给玩家看的话） */
public class MsgBlueprintResult implements IMessage {

    private boolean ok;
    private String text = "";

    public MsgBlueprintResult() {
    }

    public MsgBlueprintResult(boolean ok, String text) {
        this.ok = ok;
        this.text = text == null ? "" : text;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ok = buf.readBoolean();
        int len = buf.readInt();
        if (len < 0 || len > 32768) {
            text = "";
            return;
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        text = new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(ok);
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<MsgBlueprintResult, IMessage> {

        @Override
        public IMessage onMessage(MsgBlueprintResult msg, MessageContext ctx) {
            final boolean ok = msg.ok;
            final String text = msg.text;
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                    () -> BlueprintClient.onResult(ok, text));
            return null;
        }
    }
}
