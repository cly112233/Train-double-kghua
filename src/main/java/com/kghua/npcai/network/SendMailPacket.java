package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SendMailPacket(
    String title,
    String content,
    int[] cards, // 4种身份卡数量（杀手/平民/独赢中立/杀手中立）
    int sendMode, // 0=all, 1=whitelist, 2=blacklist
    List<String> playerNames,
    long startAt,
    long expiresAt,
    int lotteryCount
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "send_mail");
    public static final Type<SendMailPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SendMailPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.title);
                buf.writeUtf(pkt.content);
                for (int i = 0; i < 4; i++) {
                    buf.writeVarInt(pkt.cards != null && i < pkt.cards.length ? pkt.cards[i] : 0);
                }
                buf.writeVarInt(pkt.sendMode);
                buf.writeVarInt(pkt.playerNames.size());
                for (String name : pkt.playerNames) {
                    buf.writeUtf(name);
                }
                buf.writeLong(pkt.startAt);
                buf.writeLong(pkt.expiresAt);
                buf.writeVarInt(pkt.lotteryCount);
            },
            buf -> {
                String title = buf.readUtf();
                String content = buf.readUtf();
                int[] cards = new int[4];
                for (int i = 0; i < 4; i++) {
                    cards[i] = buf.readVarInt();
                }
                int mode = buf.readVarInt();
                int pCount = buf.readVarInt();
                List<String> names = new ArrayList<>();
                for (int i = 0; i < pCount; i++) {
                    names.add(buf.readUtf());
                }
                long startAt = buf.readLong();
                long expiresAt = buf.readLong();
                int lotteryCount = buf.readVarInt();
                return new SendMailPacket(title, content, cards, mode, names, startAt, expiresAt, lotteryCount);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
