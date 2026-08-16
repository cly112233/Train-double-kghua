package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端回发玩家当前称号状态（S2C）。
 * 前 6 个字段 = 存储中的称号设置（预填编辑框）；
 * displayPrefixJson = 实际队伍前缀 Component JSON（空串=无称号）；
 * nameColorName = 实际队伍玩家名字颜色名（空=默认白色）。
 */
public record SyncTitlePacket(int mode, String simpleTitle, String titleColor,
                              String complexPrefixJson, String frameColor, String nameColor,
                              String displayPrefixJson, String nameColorName)
        implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_title");
    public static final Type<SyncTitlePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTitlePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.mode);
                buf.writeUtf(pkt.simpleTitle);
                buf.writeUtf(pkt.titleColor);
                buf.writeUtf(pkt.complexPrefixJson);
                buf.writeUtf(pkt.frameColor);
                buf.writeUtf(pkt.nameColor);
                buf.writeUtf(pkt.displayPrefixJson);
                buf.writeUtf(pkt.nameColorName);
            },
            buf -> new SyncTitlePacket(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
