package com.kghua.npcai.network;

import com.kghua.npcai.data.FeedbackEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncFeedbackPacket(List<FeedbackEntry> entries) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_feedback");
    public static final Type<SyncFeedbackPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFeedbackPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.entries.size());
                for (FeedbackEntry e : pkt.entries) {
                    buf.writeUtf(e.fileName());
                    buf.writeUtf(e.playerName());
                    buf.writeBoolean(e.anonymous());
                    buf.writeUtf(e.content());
                    buf.writeLong(e.timestamp());
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<FeedbackEntry> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    entries.add(new FeedbackEntry(
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readBoolean(),
                        buf.readUtf(),
                        buf.readLong()
                    ));
                }
                return new SyncFeedbackPacket(entries);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
