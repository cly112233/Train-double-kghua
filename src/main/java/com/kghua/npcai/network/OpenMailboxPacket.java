package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端请求服务器打开列车邮箱界面。
 */
public record OpenMailboxPacket() implements CustomPacketPayload {
    public static final Type<OpenMailboxPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("western_cowboy", "open_mailbox"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMailboxPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {},
            buf -> new OpenMailboxPacket()
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
