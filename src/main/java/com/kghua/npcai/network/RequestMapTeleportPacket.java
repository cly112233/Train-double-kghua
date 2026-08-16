package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 地图组成员按 X 键时请求打开传送面板（C2S 空包）：
 * 服务端确认成员身份 + 查找 NPC 后回 OpenMapTeleportPacket。
 */
public record RequestMapTeleportPacket() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "request_map_teleport");
    public static final Type<RequestMapTeleportPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMapTeleportPacket> CODEC =
        StreamCodec.of((buf, pkt) -> {}, buf -> new RequestMapTeleportPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
