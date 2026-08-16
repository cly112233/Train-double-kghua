package com.kghua.npcai.network;

import com.kghua.npcai.data.Questionnaire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncQuestionnairesPacket(List<Questionnaire> questionnaires) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_questionnaires");
    public static final Type<SyncQuestionnairesPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncQuestionnairesPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.questionnaires.size());
                for (Questionnaire q : pkt.questionnaires) {
                    buf.writeUUID(q.getId());
                    buf.writeUtf(q.getTitle());
                    buf.writeVarInt(q.getQuestions().size());
                    for (String question : q.getQuestions()) {
                        buf.writeUtf(question);
                    }
                    buf.writeVarInt(q.getHints().size());
                    for (String hint : q.getHints()) {
                        buf.writeUtf(hint);
                    }
                    buf.writeLong(q.getStartAt());
                    buf.writeLong(q.getEndAt());
                    buf.writeLong(q.getCreatedAt());
                    buf.writeVarInt(q.getResponses().size());
                    for (Questionnaire.Response r : q.getResponses()) {
                        buf.writeUtf(r.playerName);
                        buf.writeLong(r.respondedAt);
                        buf.writeVarInt(r.answers.size());
                        for (String a : r.answers) {
                            buf.writeUtf(a);
                        }
                    }
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<Questionnaire> list = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Questionnaire q = new Questionnaire(buf.readUUID());
                    q.setTitle(buf.readUtf());
                    int qCount = buf.readVarInt();
                    List<String> questions = new ArrayList<>();
                    for (int j = 0; j < qCount; j++) {
                        questions.add(buf.readUtf());
                    }
                    q.setQuestions(questions);
                    int hintCount = buf.readVarInt();
                    List<String> hints = new ArrayList<>();
                    for (int j = 0; j < hintCount; j++) {
                        hints.add(buf.readUtf());
                    }
                    q.setHints(hints);
                    q.setStartAt(buf.readLong());
                    q.setEndAt(buf.readLong());
                    q.setCreatedAt(buf.readLong());
                    int respCount = buf.readVarInt();
                    for (int j = 0; j < respCount; j++) {
                        String playerName = buf.readUtf();
                        long respondedAt = buf.readLong();
                        int answerCount = buf.readVarInt();
                        List<String> answers = new ArrayList<>();
                        for (int k = 0; k < answerCount; k++) {
                            answers.add(buf.readUtf());
                        }
                        q.getResponses().add(new Questionnaire.Response(playerName, respondedAt, answers));
                    }
                    list.add(q);
                }
                return new SyncQuestionnairesPacket(list);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
