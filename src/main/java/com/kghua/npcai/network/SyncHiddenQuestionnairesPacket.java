package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端同步玩家已隐藏的问卷 UUID 集合给客户端。
 */
public record SyncHiddenQuestionnairesPacket(Set<UUID> hiddenIds) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_hidden_questionnaires");
    public static final Type<SyncHiddenQuestionnairesPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncHiddenQuestionnairesPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.hiddenIds.size());
                for (UUID id : pkt.hiddenIds) {
                    buf.writeUUID(id);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                Set<UUID> set = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    set.add(buf.readUUID());
                }
                return new SyncHiddenQuestionnairesPacket(set);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
