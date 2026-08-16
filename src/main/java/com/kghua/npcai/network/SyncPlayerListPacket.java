package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncPlayerListPacket(List<PlayerInfo> players) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_player_list");
    public static final Type<SyncPlayerListPacket> TYPE = new Type<>(ID);

    public record PlayerInfo(UUID id, String name, boolean op, String teamName, String teamColor, String playerColor, boolean mapGroup, boolean npcAdmin) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerListPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.players.size());
                for (PlayerInfo p : pkt.players) {
                    buf.writeUUID(p.id());
                    buf.writeUtf(p.name());
                    buf.writeBoolean(p.op());
                    buf.writeUtf(p.teamName());
                    buf.writeUtf(p.teamColor());
                    buf.writeUtf(p.playerColor());
                    buf.writeBoolean(p.mapGroup());
                    buf.writeBoolean(p.npcAdmin());
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<PlayerInfo> list = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    list.add(new PlayerInfo(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
                }
                return new SyncPlayerListPacket(list);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
