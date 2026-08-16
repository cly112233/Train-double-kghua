package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端通知客户端打开 NPC 聊天界面。
 * 携带 NPC 名称、实体 ID、玩家 UUID（作为 Coze 的 user_id）、地图组成员状态以及未读通知计数。
 * isOp：玩家是否拥有 OP 权限（hasPermissions(2)），用于 AI 对话指令身份注入。
 */
public record OpenNpcChatPacket(String npcName, int entityId, String playerUuid, boolean mapGroupMember,
                                 int unreadMailCount, int unfilledQuestionnaireCount,
                                 boolean isNpcAdmin, boolean isOp) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "open_npc_chat");
    public static final Type<OpenNpcChatPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcChatPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.npcName);
                buf.writeVarInt(pkt.entityId);
                buf.writeUtf(pkt.playerUuid);
                buf.writeBoolean(pkt.mapGroupMember);
                buf.writeVarInt(pkt.unreadMailCount);
                buf.writeVarInt(pkt.unfilledQuestionnaireCount);
                buf.writeBoolean(pkt.isNpcAdmin);
                buf.writeBoolean(pkt.isOp);
            },
            buf -> new OpenNpcChatPacket(buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
