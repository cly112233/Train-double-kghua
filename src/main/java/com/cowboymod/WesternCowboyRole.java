package com.cowboymod;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class WesternCowboyRole extends SRERole {
    private final ResourceLocation roleId;

    public WesternCowboyRole(ResourceLocation id, int color, boolean innocent, boolean canUseKiller,
                              MoodType moodType, int maxSprintTime, boolean gambler) {
        super(id, color, innocent, canUseKiller, moodType, maxSprintTime, gambler);
        this.roleId = id;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        // Duelists have no tick logic
        if (roleId.equals(CowboyMod.KILLER_DUELIST_ID) || roleId.equals(CowboyMod.NEUTRAL_DUELIST_ID)) return;
        WesternCowboyComponent comp = WesternCowboyComponent.get(player);
        if (comp != null) comp.serverTick(player);
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer player) {}

    /** Duelist has empty shop. Cowboy uses default shop from starrailexpress. */
    public List<io.wifi.starrailexpress.util.ShopEntry> getShopEntries() {
        if (roleId.equals(CowboyMod.KILLER_DUELIST_ID) || roleId.equals(CowboyMod.NEUTRAL_DUELIST_ID)) return List.of();
        return super.getShopEntries();
    }
}
