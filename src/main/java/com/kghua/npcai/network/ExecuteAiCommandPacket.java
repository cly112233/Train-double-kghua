package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 玩家在AI对话中输入yes确认后，将待执行的指令发送给服务端。
 * 服务端会按玩家身份再次校验权限（防绕过），通过后才执行。
 */
public record ExecuteAiCommandPacket(String command) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "execute_ai_command");
    public static final Type<ExecuteAiCommandPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExecuteAiCommandPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> buf.writeUtf(pkt.command),
            buf -> new ExecuteAiCommandPacket(buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
