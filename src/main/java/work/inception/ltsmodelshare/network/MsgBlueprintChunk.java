package work.inception.ltsmodelshare.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import work.inception.ltsmodelshare.server.BlueprintReceiver;

/**
 * 客户端 → 服务端：结构数据的一个分片。
 *
 * <p>为什么要分片：Forge 的单包上限是 <b>32767 字节</b>，而一个模型的 SNBT
 * 轻松上万字符（utf8 字节更多），一次塞进去会直接
 * {@code IllegalArgumentException: Payload may not be larger than 32767 bytes}。</p>
 *
 * <p>所以客户端先 gzip（{@link SnbtCodec}）再切成 {@link #MAX_DATA} 字节的片，
 * 每片带 {@code transferId / index / total}，服务端按 transferId 拼回去。</p>
 */
public class MsgBlueprintChunk implements IMessage {

    /** 单包数据上限：留出头部余量，稳在 32767 以下 */
    public static final int MAX_DATA = 30_000;

    private int transferId;
    private int index;
    private int total;
    /** 标题只在第 0 片里带，省流量 */
    private String title = "";
    private byte[] data = new byte[0];

    public MsgBlueprintChunk() {
    }

    public MsgBlueprintChunk(int transferId, int index, int total, String title, byte[] data) {
        this.transferId = transferId;
        this.index = index;
        this.total = total;
        this.title = title == null ? "" : title;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        transferId = buf.readInt();
        index = buf.readInt();
        total = buf.readInt();
        int titleLen = buf.readUnsignedShort();
        byte[] titleBytes = new byte[Math.min(titleLen, 512)];
        buf.readBytes(titleBytes);
        title = new String(titleBytes, java.nio.charset.StandardCharsets.UTF_8);
        int len = buf.readInt();
        if (len < 0 || len > MAX_DATA) {
            data = new byte[0];
            return;
        }
        data = new byte[len];
        buf.readBytes(data);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(transferId);
        buf.writeInt(index);
        buf.writeInt(total);
        byte[] titleBytes = title.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int tLen = Math.min(titleBytes.length, 512);
        buf.writeShort(tLen);
        buf.writeBytes(titleBytes, 0, tLen);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public int transferId() {
        return transferId;
    }

    public int index() {
        return index;
    }

    public int total() {
        return total;
    }

    public String title() {
        return title;
    }

    public byte[] data() {
        return data;
    }

    public static class Handler implements IMessageHandler<MsgBlueprintChunk, IMessage> {

        @Override
        public IMessage onMessage(MsgBlueprintChunk msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            // 网络线程 → 服务端主线程
            player.getServer().addScheduledTask(() -> BlueprintReceiver.accept(player, msg));
            return null;
        }
    }
}
