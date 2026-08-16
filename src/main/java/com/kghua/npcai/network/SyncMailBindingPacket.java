package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端→客户端：当前问卷绑定状态（questionnaireId 为空表示未绑定）。
 */
public record SyncMailBindingPacket(
    String questionnaireId,
    String questionnaireTitle
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_mail_binding");
    public static final Type<SyncMailBindingPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMailBindingPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.questionnaireId);
                buf.writeUtf(pkt.questionnaireTitle);
            },
            buf -> new SyncMailBindingPacket(buf.readUtf(), buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
