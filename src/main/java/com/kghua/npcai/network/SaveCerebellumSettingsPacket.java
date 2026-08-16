package com.kghua.npcai.network;

import com.kghua.npcai.data.CerebellumSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveCerebellumSettingsPacket(CerebellumSettings settings) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "save_cerebellum_settings");
    public static final Type<SaveCerebellumSettingsPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveCerebellumSettingsPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBoolean(pkt.settings.isWrongKillInnocentEnabled());
                buf.writeBoolean(pkt.settings.isKillerTeamKillNoGrenadeEnabled());
                buf.writeBoolean(pkt.settings.isKillerTeamKillGrenadeOnlyEnabled());
                buf.writeVarInt(pkt.settings.getRequiredDeaths());
                buf.writeBoolean(pkt.settings.isCursedEnabled());
                buf.writeBoolean(pkt.settings.isTallEnabled());
                buf.writeBoolean(pkt.settings.isHemophobiaEnabled());
                buf.writeBoolean(pkt.settings.isTaxedEnabled());
                buf.writeBoolean(pkt.settings.isParanoidEnabled());
                buf.writeBoolean(pkt.settings.isHoarseEnabled());
            },
            buf -> {
                CerebellumSettings settings = new CerebellumSettings();
                settings.setWrongKillInnocentEnabled(buf.readBoolean());
                settings.setKillerTeamKillNoGrenadeEnabled(buf.readBoolean());
                settings.setKillerTeamKillGrenadeOnlyEnabled(buf.readBoolean());
                settings.setRequiredDeaths(buf.readVarInt());
                settings.setCursedEnabled(buf.readBoolean());
                settings.setTallEnabled(buf.readBoolean());
                settings.setHemophobiaEnabled(buf.readBoolean());
                settings.setTaxedEnabled(buf.readBoolean());
                settings.setParanoidEnabled(buf.readBoolean());
                settings.setHoarseEnabled(buf.readBoolean());
                return new SaveCerebellumSettingsPacket(settings);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
