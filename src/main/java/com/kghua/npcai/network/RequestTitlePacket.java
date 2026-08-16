package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端打开称号页时请求当前称号状态（C2S 空包）。
 * 服务端回发 {@link SyncTitlePacket} 预填编辑框 + 展示当前实际生效的称号。
 */
public record RequestTitlePacket() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "request_title");
    public static final Type<RequestTitlePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTitlePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {},
            buf -> new RequestTitlePacket()
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
