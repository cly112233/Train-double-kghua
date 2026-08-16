package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncAiConfigPacket(String defaultAiApiUrl, boolean mapGroupMember, boolean npcAdmin, boolean isOp) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_ai_config");
    public static final Type<SyncAiConfigPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAiConfigPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.defaultAiApiUrl);
                buf.writeBoolean(pkt.mapGroupMember);
                buf.writeBoolean(pkt.npcAdmin);
                buf.writeBoolean(pkt.isOp);
            },
            buf -> new SyncAiConfigPacket(buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
