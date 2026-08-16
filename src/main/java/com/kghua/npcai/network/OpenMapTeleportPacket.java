package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端确认地图组成员并返回 NPC 信息（S2C）。
 * isMember=false 或 npcEntityId=-1 → 客户端不做任何事。
 */
public record OpenMapTeleportPacket(boolean isMember, int npcEntityId, String npcName) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "open_map_teleport");
    public static final Type<OpenMapTeleportPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMapTeleportPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBoolean(pkt.isMember);
                buf.writeVarInt(pkt.npcEntityId);
                buf.writeUtf(pkt.npcName);
            },
            buf -> new OpenMapTeleportPacket(buf.readBoolean(), buf.readVarInt(), buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
