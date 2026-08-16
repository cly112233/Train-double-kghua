package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ManagePlayerPacket(
    UUID playerId,
    String action, // "op", "role", "mapgroup", "npcadmin"
    String value   // role name, mapgroup info, or empty for op toggle
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "manage_player");
    public static final Type<ManagePlayerPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ManagePlayerPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUUID(pkt.playerId);
                buf.writeUtf(pkt.action);
                buf.writeUtf(pkt.value);
            },
            buf -> new ManagePlayerPacket(buf.readUUID(), buf.readUtf(), buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
