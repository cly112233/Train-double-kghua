package com.kghua.npcai.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 同步装备请求（C2S，空包）：服务端读取玩家当前主副手+全部装备复制到NPC。
 */
public record SyncEquipmentPacket() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_equipment");
    public static final Type<SyncEquipmentPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEquipmentPacket> CODEC =
        StreamCodec.of((buf, pkt) -> {}, buf -> new SyncEquipmentPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
