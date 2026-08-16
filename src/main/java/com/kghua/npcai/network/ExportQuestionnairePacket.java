package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ExportQuestionnairePacket(UUID questionnaireId) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "export_questionnaire");
    public static final Type<ExportQuestionnairePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExportQuestionnairePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> buf.writeUUID(pkt.questionnaireId),
            buf -> new ExportQuestionnairePacket(buf.readUUID())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
