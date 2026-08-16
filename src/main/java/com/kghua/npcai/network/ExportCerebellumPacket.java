package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 导出小脑榜请求（C2S，空包）：服务端生成md到 npctalltome/xiaonao/。
 */
public record ExportCerebellumPacket() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "export_cerebellum");
    public static final Type<ExportCerebellumPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExportCerebellumPacket> CODEC =
        StreamCodec.of((buf, pkt) -> {}, buf -> new ExportCerebellumPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
