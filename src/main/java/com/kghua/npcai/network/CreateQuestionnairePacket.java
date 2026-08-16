package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CreateQuestionnairePacket(UUID id, String title, List<String> questions, List<String> hints, long startAt, long endAt) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "create_questionnaire");
    public static final Type<CreateQuestionnairePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateQuestionnairePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUUID(pkt.id);
                buf.writeUtf(pkt.title);
                buf.writeVarInt(pkt.questions.size());
                for (String q : pkt.questions) {
                    buf.writeUtf(q);
                }
                buf.writeVarInt(pkt.hints.size());
                for (String h : pkt.hints) {
                    buf.writeUtf(h);
                }
                buf.writeLong(pkt.startAt);
                buf.writeLong(pkt.endAt);
            },
            buf -> {
                UUID id = buf.readUUID();
                String title = buf.readUtf();
                int count = buf.readVarInt();
                List<String> questions = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    questions.add(buf.readUtf());
                }
                int hintCount = buf.readVarInt();
                List<String> hints = new ArrayList<>();
                for (int i = 0; i < hintCount; i++) {
                    hints.add(buf.readUtf());
                }
                long startAt = buf.readLong();
                long endAt = buf.readLong();
                return new CreateQuestionnairePacket(id, title, questions, hints, startAt, endAt);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
