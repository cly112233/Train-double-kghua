package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 玩家隐藏（屏蔽）某个已填写问卷，服务端不删除问卷本身。
 */
public record HideQuestionnairePacket(UUID questionnaireId) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "hide_questionnaire");
    public static final Type<HideQuestionnairePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, HideQuestionnairePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> buf.writeUUID(pkt.questionnaireId),
            buf -> new HideQuestionnairePacket(buf.readUUID())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
