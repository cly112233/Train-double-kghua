package com.kghua.npcai.network;

import com.kghua.npcai.data.MailRecord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncMailsPacket(List<MailRecord> mails) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_mails");
    public static final Type<SyncMailsPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMailsPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.mails.size());
                for (MailRecord m : pkt.mails) {
                    buf.writeUUID(m.getId());
                    buf.writeUtf(m.getTitle());
                    buf.writeUtf(m.getContent());
                    for (int c : m.getCards()) buf.writeVarInt(c);
                    buf.writeVarInt(m.getLotteryCount());
                    buf.writeVarInt(m.getSendMode());
                    buf.writeVarInt(m.getPlayerNames().size());
                    for (String n : m.getPlayerNames()) buf.writeUtf(n);
                    buf.writeLong(m.getStartAt());
                    buf.writeLong(m.getEndAt());
                    buf.writeLong(m.getSentAt());
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<MailRecord> list = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    MailRecord m = new MailRecord(buf.readUUID());
                    m.setTitle(buf.readUtf());
                    m.setContent(buf.readUtf());
                    int[] cards = new int[4];
                    for (int j = 0; j < 4; j++) cards[j] = buf.readVarInt();
                    m.setCards(cards);
                    m.setLotteryCount(buf.readVarInt());
                    m.setSendMode(buf.readVarInt());
                    int nameCount = buf.readVarInt();
                    List<String> names = new ArrayList<>();
                    for (int j = 0; j < nameCount; j++) names.add(buf.readUtf());
                    m.setPlayerNames(names);
                    m.setStartAt(buf.readLong());
                    m.setEndAt(buf.readLong());
                    m.setSentAt(buf.readLong());
                    list.add(m);
                }
                return new SyncMailsPacket(list);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
