package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 管理端审核投稿：审核通过（发送投稿奖励邮件+发放奖励，作品归属期数改为审核通过当期）
 * 或审核不通过（删除作品+发送驳回邮件，无奖励）。
 */
public record ApproveContributionPacket(UUID contributionId, boolean approved) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "approve_contribution");
    public static final Type<ApproveContributionPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ApproveContributionPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUUID(pkt.contributionId);
                buf.writeBoolean(pkt.approved);
            },
            buf -> new ApproveContributionPacket(buf.readUUID(), buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
