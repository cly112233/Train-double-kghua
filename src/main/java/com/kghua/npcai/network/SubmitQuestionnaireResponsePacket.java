package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SubmitQuestionnaireResponsePacket(UUID questionnaireId, List<String> answers) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "submit_questionnaire");
    public static final Type<SubmitQuestionnaireResponsePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitQuestionnaireResponsePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUUID(pkt.questionnaireId);
                buf.writeVarInt(pkt.answers.size());
                for (String a : pkt.answers) {
                    buf.writeUtf(a);
                }
            },
            buf -> {
                UUID id = buf.readUUID();
                int count = buf.readVarInt();
                List<String> answers = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    answers.add(buf.readUtf());
                }
                return new SubmitQuestionnaireResponsePacket(id, answers);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
