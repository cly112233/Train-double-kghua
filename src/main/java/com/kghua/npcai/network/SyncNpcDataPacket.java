package com.kghua.npcai.network;

import com.kghua.npcai.data.TeleportPoint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncNpcDataPacket(int entityId, String displayName, String skinName, List<TeleportPoint> teleportPoints) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_npc_data");
    public static final Type<SyncNpcDataPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncNpcDataPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.entityId);
                buf.writeUtf(pkt.displayName);
                buf.writeUtf(pkt.skinName);
                buf.writeVarInt(pkt.teleportPoints.size());
                for (TeleportPoint p : pkt.teleportPoints) {
                    buf.writeUtf(p.name());
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    buf.writeLong(p.updatedAt());
                    buf.writeUtf(p.category() != null ? p.category() : "其他");
                }
            },
            buf -> {
                int entityId = buf.readVarInt();
                String displayName = buf.readUtf();
                String skinName = buf.readUtf();
                int count = buf.readVarInt();
                List<TeleportPoint> points = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    points.add(new TeleportPoint(
                        buf.readUtf(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readLong(),
                        buf.readUtf()
                    ));
                }
                return new SyncNpcDataPacket(entityId, displayName, skinName, points);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
