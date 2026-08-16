package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestFeedbackPacket(long startAt, long endAt) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "request_feedback");
    public static final Type<RequestFeedbackPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFeedbackPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeLong(pkt.startAt);
                buf.writeLong(pkt.endAt);
            },
            buf -> new RequestFeedbackPacket(buf.readLong(), buf.readLong())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
