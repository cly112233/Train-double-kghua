package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 玩家提交投稿（角色/修饰符）。
 */
public record SubmitContributionPacket(String contributionType, String title, String shortDesc,
                                        String description, String shop, String background,
                                        String faction) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "submit_contribution");
    public static final Type<SubmitContributionPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitContributionPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.contributionType);
                buf.writeUtf(pkt.title);
                buf.writeUtf(pkt.shortDesc);
                buf.writeUtf(pkt.description);
                buf.writeUtf(pkt.shop);
                buf.writeUtf(pkt.background);
                buf.writeUtf(pkt.faction);
            },
            buf -> new SubmitContributionPacket(
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
