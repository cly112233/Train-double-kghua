package com.kghua.npcai.network;

import com.kghua.npcai.data.ContributionRewardSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 管理端保存投稿奖励设置（每次投稿奖励 + 每期前三名奖励）。
 */
public record SaveContributionRewardsPacket(ContributionRewardSettings settings) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "save_contribution_rewards");
    public static final Type<SaveContributionRewardsPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveContributionRewardsPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                ContributionRewardSettings s = pkt.settings;
                for (int i = 0; i < 4; i++) buf.writeVarInt(s.getPerSubmitCard(i));
                buf.writeVarInt(s.getPerSubmitLottery());
                for (int p = 0; p < 3; p++) {
                    for (int i = 0; i < 4; i++) buf.writeVarInt(s.getPlaceCard(p, i));
                    buf.writeVarInt(s.getPlaceLottery(p));
                }
            },
            buf -> {
                ContributionRewardSettings s = new ContributionRewardSettings();
                for (int i = 0; i < 4; i++) s.setPerSubmitCard(i, buf.readVarInt());
                s.setPerSubmitLottery(buf.readVarInt());
                for (int p = 0; p < 3; p++) {
                    for (int i = 0; i < 4; i++) s.setPlaceCard(p, i, buf.readVarInt());
                    s.setPlaceLottery(p, buf.readVarInt());
                }
                return new SaveContributionRewardsPacket(s);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
