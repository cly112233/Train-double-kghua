package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * AI指令执行结果回传给客户端：对话界面打开时显示在界面内，否则落到游戏聊天。
 */
public record ExecuteAiCommandResultPacket(String message) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "execute_ai_command_result");
    public static final Type<ExecuteAiCommandResultPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExecuteAiCommandResultPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> buf.writeUtf(pkt.message),
            buf -> new ExecuteAiCommandResultPacket(buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
