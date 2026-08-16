package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record DeleteQuestionnairePacket(UUID questionnaireId) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "delete_questionnaire");
    public static final Type<DeleteQuestionnairePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteQuestionnairePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> buf.writeUUID(pkt.questionnaireId),
            buf -> new DeleteQuestionnairePacket(buf.readUUID())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
