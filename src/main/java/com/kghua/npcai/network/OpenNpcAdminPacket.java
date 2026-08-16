package com.kghua.npcai.network;

import com.kghua.npcai.data.TeleportPoint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record OpenNpcAdminPacket(
    int entityId,
    String displayName,
    String skinName,
    double x, double y, double z,
    int followMode,
    int viewMode,
    float scale,
    String heldItem,
    List<TeleportPoint> teleportPoints,
    double roamX, double roamY, double roamZ, double roamRadius
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "open_npc_admin");
    public static final Type<OpenNpcAdminPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcAdminPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.entityId);
                buf.writeUtf(pkt.displayName);
                buf.writeUtf(pkt.skinName);
                buf.writeDouble(pkt.x);
                buf.writeDouble(pkt.y);
                buf.writeDouble(pkt.z);
                buf.writeVarInt(pkt.followMode);
                buf.writeVarInt(pkt.viewMode);
                buf.writeFloat(pkt.scale);
                buf.writeUtf(pkt.heldItem);
                buf.writeVarInt(pkt.teleportPoints.size());
                for (TeleportPoint p : pkt.teleportPoints) {
                    buf.writeUtf(p.name());
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    buf.writeLong(p.updatedAt());
                    buf.writeUtf(p.category() != null ? p.category() : "其他");
                }
                buf.writeDouble(pkt.roamX);
                buf.writeDouble(pkt.roamY);
                buf.writeDouble(pkt.roamZ);
                buf.writeDouble(pkt.roamRadius);
            },
            buf -> {
                int entityId = buf.readVarInt();
                String displayName = buf.readUtf();
                String skinName = buf.readUtf();
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                int followMode = buf.readVarInt();
                int viewMode = buf.readVarInt();
                float scale = buf.readFloat();
                String heldItem = buf.readUtf();
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
                double roamX = buf.readDouble();
                double roamY = buf.readDouble();
                double roamZ = buf.readDouble();
                double roamRadius = buf.readDouble();
                return new OpenNpcAdminPacket(entityId, displayName, skinName, x, y, z, followMode, viewMode, scale, heldItem, points, roamX, roamY, roamZ, roamRadius);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
