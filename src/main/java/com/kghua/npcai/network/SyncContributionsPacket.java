package com.kghua.npcai.network;

import com.kghua.npcai.data.Contribution;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 同步投稿列表给客户端（含该玩家今日剩余点赞次数与已点赞ID）。
 */
public record SyncContributionsPacket(List<Contribution> contributions, int remainingLikes, List<String> likedIds) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_contributions");
    public static final Type<SyncContributionsPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncContributionsPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.remainingLikes);
                buf.writeVarInt(pkt.likedIds.size());
                for (String s : pkt.likedIds) buf.writeUtf(s);
                buf.writeVarInt(pkt.contributions.size());
                for (Contribution c : pkt.contributions) {
                    buf.writeUUID(c.getId());
                    buf.writeUtf(c.getType());
                    buf.writeUtf(c.getTitle());
                    buf.writeUtf(c.getShortDesc());
                    buf.writeUtf(c.getDescription());
                    buf.writeUtf(c.getShop());
                    buf.writeUtf(c.getBackground());
                    buf.writeUtf(c.getFaction());
                    buf.writeUtf(c.getAuthorName());
                    buf.writeUUID(c.getAuthorId() != null ? c.getAuthorId() : UUID.randomUUID());
                    buf.writeLong(c.getCreatedAt());
                    buf.writeVarInt(c.getLikes());
                    buf.writeBoolean(c.isApproved());
                    buf.writeVarInt(c.getPeriod());
                }
            },
            buf -> {
                int remaining = buf.readVarInt();
                int likedCount = buf.readVarInt();
                List<String> likedIds = new ArrayList<>();
                for (int i = 0; i < likedCount; i++) likedIds.add(buf.readUtf());
                int count = buf.readVarInt();
                List<Contribution> list = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    UUID id = buf.readUUID();
                    Contribution c = new Contribution(id);
                    c.setType(buf.readUtf());
                    c.setTitle(buf.readUtf());
                    c.setShortDesc(buf.readUtf());
                    c.setDescription(buf.readUtf());
                    c.setShop(buf.readUtf());
                    c.setBackground(buf.readUtf());
                    c.setFaction(buf.readUtf());
                    c.setAuthorName(buf.readUtf());
                    c.setAuthorId(buf.readUUID());
                    c.setCreatedAt(buf.readLong());
                    c.setLikes(buf.readVarInt());
                    c.setApproved(buf.readBoolean());
                    c.setPeriod(buf.readVarInt());
                    list.add(c);
                }
                return new SyncContributionsPacket(list, remaining, likedIds);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
