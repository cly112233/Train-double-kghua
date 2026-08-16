package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 点赞/取消点赞投稿。
 */
public record LikeContributionPacket(UUID contributionId) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "like_contribution");
    public static final Type<LikeContributionPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, LikeContributionPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> buf.writeUUID(pkt.contributionId),
            buf -> new LikeContributionPacket(buf.readUUID())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
