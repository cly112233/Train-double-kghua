package com.kghua.npcai.network;

import com.kghua.npcai.data.CerebellumSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncCerebellumSettingsPacket(CerebellumSettings settings, List<CerebellumEntry> leaderboard) implements CustomPacketPayload {

    public record CerebellumEntry(UUID playerUuid, String playerName, int currentCount, int punishmentCount, int pendingCount) {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_cerebellum_settings");
    public static final Type<SyncCerebellumSettingsPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCerebellumSettingsPacket> CODEC =
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
                // 排行榜数据
                buf.writeVarInt(pkt.leaderboard.size());
                for (CerebellumEntry e : pkt.leaderboard) {
                    buf.writeUUID(e.playerUuid);
                    buf.writeUtf(e.playerName);
                    buf.writeVarInt(e.currentCount);
                    buf.writeVarInt(e.punishmentCount);
                    buf.writeVarInt(e.pendingCount);
                }
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
                int lbCount = buf.readVarInt();
                List<CerebellumEntry> lb = new ArrayList<>();
                for (int i = 0; i < lbCount; i++) {
                    lb.add(new CerebellumEntry(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                }
                return new SyncCerebellumSettingsPacket(settings, lb);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
