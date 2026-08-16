package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SubmitFeedbackPacket(int entityId, boolean anonymous, String content) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "submit_feedback");
    public static final Type<SubmitFeedbackPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitFeedbackPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.entityId);
                buf.writeBoolean(pkt.anonymous);
                buf.writeUtf(pkt.content);
            },
            buf -> new SubmitFeedbackPacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
