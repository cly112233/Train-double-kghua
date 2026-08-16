package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端把导出内容发回客户端（客户端保存到自己本地的游戏文件夹）。
 * subDir: npctalltome 下的子目录（fankui/diaocha/juesetougao/xiaonao）
 */
public record ExportResultPacket(String subDir, String fileName, String content) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "export_result");
    public static final Type<ExportResultPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExportResultPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.subDir);
                buf.writeUtf(pkt.fileName);
                buf.writeUtf(pkt.content);
            },
            buf -> new ExportResultPacket(buf.readUtf(), buf.readUtf(), buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
