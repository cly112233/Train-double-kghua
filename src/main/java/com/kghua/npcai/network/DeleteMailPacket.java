package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record DeleteMailPacket(UUID mailId) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "delete_mail");
    public static final Type<DeleteMailPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteMailPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> buf.writeUUID(pkt.mailId),
            buf -> new DeleteMailPacket(buf.readUUID())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
