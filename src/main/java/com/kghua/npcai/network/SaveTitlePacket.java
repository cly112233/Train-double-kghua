package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 玩家在称号页点击「确认」后，将称号设置发送给服务端（C2S）。
 * mode: 0=简单模式 1=复杂模式；称号/颜色字段任意组合，称号为空=清除称号。
 */
public record SaveTitlePacket(int mode, String simpleTitle, String titleColor,
                              String complexPrefixJson, String frameColor, String nameColor)
        implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "save_title");
    public static final Type<SaveTitlePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveTitlePacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.mode);
                buf.writeUtf(pkt.simpleTitle);
                buf.writeUtf(pkt.titleColor);
                buf.writeUtf(pkt.complexPrefixJson);
                buf.writeUtf(pkt.frameColor);
                buf.writeUtf(pkt.nameColor);
            },
            buf -> new SaveTitlePacket(
                buf.readVarInt(),
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
