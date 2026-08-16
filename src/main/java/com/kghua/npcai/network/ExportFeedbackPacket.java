package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ExportFeedbackPacket(List<String> fileNames) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "export_feedback");
    public static final Type<ExportFeedbackPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExportFeedbackPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.fileNames.size());
                for (String name : pkt.fileNames) {
                    buf.writeUtf(name);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<String> list = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    list.add(buf.readUtf());
                }
                return new ExportFeedbackPacket(list);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
