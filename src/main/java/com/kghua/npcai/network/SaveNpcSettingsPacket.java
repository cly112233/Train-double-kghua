package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveNpcSettingsPacket(
    int entityId,
    String displayName,
    String skinName,
    double x, double y, double z,
    int followMode,
    int viewMode,
    float scale,
    String heldItem,
    double roamX, double roamY, double roamZ, double roamRadius
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "save_npc_settings");
    public static final Type<SaveNpcSettingsPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveNpcSettingsPacket> CODEC =
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
                buf.writeDouble(pkt.roamX);
                buf.writeDouble(pkt.roamY);
                buf.writeDouble(pkt.roamZ);
                buf.writeDouble(pkt.roamRadius);
            },
            buf -> new SaveNpcSettingsPacket(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
