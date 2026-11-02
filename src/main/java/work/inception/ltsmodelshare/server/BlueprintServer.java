package work.inception.ltsmodelshare.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import team.creative.littletiles.LittleTilesRegistry;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import team.creative.littletiles.common.item.ItemLittleBlueprint;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.network.MsgBlueprintChunk;
import work.inception.ltsmodelshare.network.MsgBlueprintResult;
import work.inception.ltsmodelshare.network.Network;
import work.inception.ltsmodelshare.network.SnbtCodec;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side assembly and authoritative blueprint creation. */
public final class BlueprintServer {
    private static final long TIMEOUT_MS = 2 * 60 * 1000L;
    private static final int MAX_CHUNKS = 64;
    private static final Map<String, Transfer> ACTIVE = new HashMap<>();

    private BlueprintServer() { }

    public static void accept(ServerPlayer player, MsgBlueprintChunk msg) {
        if (player == null || msg == null) return;
        evictStale();
        String key = player.getUUID() + "#" + msg.transferId();
        if (msg.total() <= 0 || msg.total() > MAX_CHUNKS || msg.index() < 0 || msg.index() >= msg.total()) {
            LtsModelShare.LOG.warn("收到非法分片：total={} index={}", msg.total(), msg.index());
            return;
        }
        Transfer transfer = ACTIVE.computeIfAbsent(key, k -> new Transfer(msg.total()));
        if (transfer.total != msg.total()) {
            transfer = new Transfer(msg.total());
            ACTIVE.put(key, transfer);
        }
        if (!msg.title().isEmpty()) transfer.title = msg.title();
        transfer.parts[msg.index()] = msg.data();
        transfer.received++;
        if (transfer.received < transfer.total) return;
        ACTIVE.remove(key);

        try {
            int size = 0;
            for (byte[] part : transfer.parts) {
                if (part == null) {
                    reply(player, false, Component.translatable("ltsmodelshare.server.empty"));
                    return;
                }
                size += part.length;
            }
            byte[] all = new byte[size];
            int offset = 0;
            for (byte[] part : transfer.parts) {
                System.arraycopy(part, 0, all, offset, part.length);
                offset += part.length;
            }
            give(player, SnbtCodec.decompress(all), transfer.title);
        } catch (Throwable error) {
            LtsModelShare.LOG.warn("拼装结构失败：{}", error.toString());
            reply(player, false, Component.translatable("ltsmodelshare.server.failed", error.toString()));
        }
    }

    /** Parse modern (1.19+) LittleTiles SNBT and insert a blueprint item. */
    public static void give(ServerPlayer player, String snbt, String title) {
        if (player == null) return;
        try {
            if (snbt == null || snbt.trim().isEmpty()) {
                reply(player, false, Component.translatable("ltsmodelshare.server.empty"));
                return;
            }
            if (snbt.length() > SnbtCodec.MAX_CHARS) {
                reply(player, false, Component.translatable("ltsmodelshare.server.too_large", snbt.length()));
                return;
            }
            CompoundTag tag = TagParser.parseTag(snbt);
            // A converted 1.12 door/axis still says e.g. id:"door" but has no "states";
            // LittleTiles would crash the client when placing it. Check every structure
            // (including nested children), not just the top-level one.
            if (hasStatelessStateStructure(tag)) {
                reply(player, false, Component.translatable("ltsmodelshare.blueprint.legacy_door"));
                return;
            }
            LittleGroup group = LittleGroup.load(tag);
            if (group == null || group.size() == 0) {
                reply(player, false, Component.translatable("ltsmodelshare.common.parse_incompatible"));
                return;
            }
            ItemStack stack = new ItemStack(LittleTilesRegistry.BLUEPRINT.get());
            if (!(stack.getItem() instanceof ItemLittleBlueprint blueprint)) {
                reply(player, false, Component.translatable("ltsmodelshare.common.parse_incompatible"));
                return;
            }
            blueprint.saveTiles(stack, group);
            String cleanTitle = title == null ? "" : title.trim();
            if (!cleanTitle.isEmpty()) stack.setHoverName(Component.literal(cleanTitle));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
                reply(player, true, Component.translatable("ltsmodelshare.server.dropped", stack.getDisplayName()));
            } else {
                player.getInventory().setChanged();
                reply(player, true, Component.translatable("ltsmodelshare.server.created",
                        stack.getDisplayName(), snbt.length()));
            }
            LtsModelShare.LOG.info("服务端生成蓝图：player={} title={} 字符数={}",
                    player.getName().getString(), title, snbt.length());
        } catch (Throwable error) {
            LtsModelShare.LOG.warn("服务端生成蓝图失败：{}", error.toString());
            reply(player, false, Component.translatable("ltsmodelshare.server.failed", error.toString()));
        }
    }

    /** 1.20 animation structures all require a non-empty "states" list. */
    private static final java.util.Set<String> STATE_IDS = java.util.Set.of(
            "door", "advancedDoor", "slidingDoor", "sliding", "doorActivator", "activator", "axis");

    private static boolean hasStatelessStateStructure(net.minecraft.nbt.Tag tag) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("id", net.minecraft.nbt.Tag.TAG_STRING)
                    && STATE_IDS.contains(compound.getString("id"))
                    && !compound.contains("states")) {
                return true;
            }
            for (String key : compound.getAllKeys()) {
                if (hasStatelessStateStructure(compound.get(key))) return true;
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (net.minecraft.nbt.Tag child : list) {
                if (hasStatelessStateStructure(child)) return true;
            }
        }
        return false;
    }

    private static void reply(ServerPlayer player, boolean ok, Component message) {
        try {
            Network.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new MsgBlueprintResult(ok, Component.Serializer.toJson(message)));
        } catch (Throwable error) {
            LtsModelShare.LOG.warn("回执发送失败：{}", error.toString());
        }
    }

    private static void evictStale() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Transfer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().startedAt > TIMEOUT_MS) it.remove();
        }
    }

    public static void drop(UUID playerId) {
        ACTIVE.keySet().removeIf(key -> key.startsWith(playerId.toString() + "#"));
    }

    private static final class Transfer {
        final byte[][] parts;
        final int total;
        final long startedAt = System.currentTimeMillis();
        int received;
        String title = "";

        Transfer(int total) {
            this.total = total;
            this.parts = new byte[total][];
        }
    }
}
