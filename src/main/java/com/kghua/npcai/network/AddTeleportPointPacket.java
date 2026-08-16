package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AddTeleportPointPacket(int entityId, String name, double x, double y, double z, String category) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "add_teleport_point");
    public static final Type<AddTeleportPointPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, AddTeleportPointPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.entityId);
                buf.writeUtf(pkt.name);
                buf.writeDouble(pkt.x);
                buf.writeDouble(pkt.y);
                buf.writeDouble(pkt.z);
                buf.writeUtf(pkt.category);
            },
            buf -> new AddTeleportPointPacket(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readUtf()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
