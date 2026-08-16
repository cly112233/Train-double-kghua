package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 管理端绑定/解除问卷→邮件。携带绑定瞬间的邮件模板快照
 * （标题/内容/身份卡奖励/抽奖/有效期），玩家首次提交该问卷时自动发送。
 * questionnaireId 为空表示解除绑定。
 */
public record BindMailQuestionnairePacket(
    String questionnaireId,
    String title,
    String content,
    int[] cards, // 4种身份卡数量（杀手/平民/独赢中立/杀手中立）
    int lotteryCount,
    long endAt
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "bind_mail_questionnaire");
    public static final Type<BindMailQuestionnairePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, BindMailQuestionnairePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.questionnaireId);
                buf.writeUtf(pkt.title);
                buf.writeUtf(pkt.content);
                for (int i = 0; i < 4; i++) {
                    buf.writeVarInt(pkt.cards != null && i < pkt.cards.length ? pkt.cards[i] : 0);
                }
                buf.writeVarInt(pkt.lotteryCount);
                buf.writeLong(pkt.endAt);
            },
            buf -> {
                String qid = buf.readUtf();
                String title = buf.readUtf();
                String content = buf.readUtf();
                int[] cards = new int[4];
                for (int i = 0; i < 4; i++) {
                    cards[i] = buf.readVarInt();
                }
                int lottery = buf.readVarInt();
                long endAt = buf.readLong();
                return new BindMailQuestionnairePacket(qid, title, content, cards, lottery, endAt);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
