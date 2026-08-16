package com.cowboymod;

import com.cowboymod.entity.CowboyPuppetEntity;
import com.cowboymod.network.CowboyDuelPacket;
import com.cowboymod.network.SkinIconUploadPacket;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CowboyMod implements ModInitializer {
    public static final String MOD_ID = "western_cowboy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ResourceLocation COWBOY_ROLE_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "western_cowboy");
    public static final ResourceLocation KILLER_DUELIST_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "killer_duelist");
    public static final ResourceLocation NEUTRAL_DUELIST_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "neutral_duelist");
    public static SRERole WESTERN_COWBOY, KILLER_DUELIST, NEUTRAL_DUELIST;

    public static final EntityType<CowboyPuppetEntity> COWBOY_PUPPET =
        EntityType.Builder.of(CowboyPuppetEntity::new, MobCategory.MISC)
            .sized(0.6f, 1.8f).clientTrackingRange(64).updateInterval(20).build("cowboy_puppet");

    @Override
    public void onInitialize() {
        LOGGER.info("Western Cowboy Addon initializing...");
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cowboy_puppet"), COWBOY_PUPPET);
        FabricDefaultAttributeRegistry.register(COWBOY_PUPPET, CowboyPuppetEntity.createLivingAttributes());
        CowboyDuelPacket.register();
        SkinIconUploadPacket.register();

        WESTERN_COWBOY = new WesternCowboyRole(COWBOY_ROLE_ID, 0xFFC4A46C, true, false, SRERole.MoodType.REAL, 100, false)
            .setCanPickUpRevolver(true).setCanSeeCoin(true).setCanUseInstinct(false)
            .setDefaultMax(1).setDefaultEnableChance(20).setDefaultEnableNeededPlayerCount(6).setCanBeRandomedByOtherRoles(true);

        TMMRoles.registerRole(WESTERN_COWBOY);

        // 让西部牛仔和列车普通角色使用相同的默认概率：不设置概率门槛，也不限制最低人数
        // 仅保留每局最多 1 人的上限，决斗相关角色不受此影响
        WESTERN_COWBOY.setDefaultEnableChance(-1).setDefaultEnableNeededPlayerCount(-1);

        // Killer-faction duelist — for killers entering duel
        KILLER_DUELIST = new WesternCowboyRole(KILLER_DUELIST_ID, 0xFF_884444, false, true, SRERole.MoodType.NONE, -1, false)
            .setDefaultMax(0).setDefaultEnableChance(0).setDefaultEnableNeededPlayerCount(0).setCanBeRandomedByOtherRoles(false);
        TMMRoles.registerRole(KILLER_DUELIST);

        // Neutral duelist — for non-killers
        NEUTRAL_DUELIST = new WesternCowboyRole(NEUTRAL_DUELIST_ID, 0xFF_888888, false, false, SRERole.MoodType.NONE, -1, false)
            .setDefaultMax(0).setDefaultEnableChance(0).setDefaultEnableNeededPlayerCount(0).setCanBeRandomedByOtherRoles(false);
        TMMRoles.registerRole(NEUTRAL_DUELIST);

        // Register empty shop entries for duelist roles
        try {
            Class<?> shopContent = Class.forName("io.wifi.starrailexpress.game.ShopContent");
            @SuppressWarnings("unchecked")
            var customEntries = (java.util.Map<ResourceLocation, java.util.List<?>>)
                shopContent.getDeclaredField("customEntries").get(null);
            // Put a 10000-gold lockpick so it's effectively unbuyable (blocks defaultKnifeEntries)
            Class<?> se = Class.forName("io.wifi.starrailexpress.util.ShopEntry");
            Class<?> st = Class.forName("dev.doctor4t.wathe.util.ShopEntry$Type");
            Object tool = Enum.valueOf((Class<Enum>) st, "TOOL");
            var lp = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("noellesroles", "lockpick"));
            var dummy = se.getConstructor(net.minecraft.world.item.ItemStack.class, int.class, st)
                .newInstance(new net.minecraft.world.item.ItemStack(lp), 10000, tool);
            var list = java.util.List.of(dummy);
            customEntries.put(KILLER_DUELIST_ID, list);
            customEntries.put(NEUTRAL_DUELIST_ID, list);
        } catch (Exception e) { LOGGER.warn("Failed to register duelist shop", e); }

        CowboyConfig.load();
        logStructureTemplatePresence();

        // Prevent duel revolver from dropping when shooting in arena
        io.wifi.starrailexpress.event.AllowShootRevolverDrop.EVENT.register((shooter, target) -> {
            for (var comp : WesternCowboyComponent.getAllActive()) {
                if (comp.isInArena() && comp.isDuelParticipant(shooter.getUUID()))
                    return io.wifi.starrailexpress.util.TrueFalseResult.FALSE;
            }
            return io.wifi.starrailexpress.util.TrueFalseResult.PASS;
        });

        // Intercept arena death (fires BEFORE body spawns) — we handle result ourselves
        // This is the PRIMARY duel death handler: prevents death in arena and resolves the duel.
        io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller.EVENT.register((victim, killer, cause) -> {
            if (victim instanceof net.minecraft.server.level.ServerPlayer v
                    && killer instanceof net.minecraft.server.level.ServerPlayer k) {
                for (var comp : WesternCowboyComponent.getAllActive()) {
                    if (comp.isInArena() && comp.isDuelParticipant(v.getUUID())) {
                        LOGGER.info("AllowPlayerDeathWithKiller intercepted for duel victim {}", v.getName().getString());
                        comp.onDuelDeath(v, k);
                        return false; // Block death in arena — onDuelDeath resolves the duel
                    }
                }
            }
            return true;
        });

        // Safety net: also intercept AFTER shield (in case earlier SRE handlers
        // short-circuit the pre-shield AllowPlayerDeathWithKiller event)
        io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, cause) -> {
            if (victim instanceof net.minecraft.server.level.ServerPlayer v
                    && killer instanceof net.minecraft.server.level.ServerPlayer k) {
                for (var comp : WesternCowboyComponent.getAllActive()) {
                    if (comp.isInArena() && comp.isDuelParticipant(v.getUUID())) {
                        LOGGER.info("AfterShieldAllowPlayerDeathWithKiller intercepted for duel victim {}", v.getName().getString());
                        comp.onDuelDeath(v, k);
                        return false;
                    }
                }
            }
            return true;
        });

        // Reset all cowboy state when a new game starts / ends
        io.wifi.starrailexpress.event.OnGameStarted.EVENT.register(world -> {
            WesternCowboyComponent.resetAll();
            LOGGER.info("Western Cowboy: state reset on game started");
        });
        io.wifi.starrailexpress.event.OnGameTrueStarted.EVENT.register(world -> {
            WesternCowboyComponent.resetAll();
            LOGGER.info("Western Cowboy: state reset on game true started");
        });
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register((world, gameWorldComp) -> {
            WesternCowboyComponent.resetAll();
            LOGGER.info("Western Cowboy: state reset on game end");
        });

        // Stackable duel shield: intercept non-duel deaths and consume one layer
        io.wifi.starrailexpress.event.AllowPlayerDeath.EVENT.register((player, deathReason) -> {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp
                    && WesternCowboyComponent.getShieldLayers(sp.getUUID()) > 0
                    && !isDuelParticipant(sp)) {
                WesternCowboyComponent.consumeShield(sp.getUUID());
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§e护盾抵消了这次死亡"), true);
                sp.playNotifySound(net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                        net.minecraft.sounds.SoundSource.MASTER, 0.5F, 1.0F);
                return false;
            }
            return true;
        });
        io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp
                    && WesternCowboyComponent.getShieldLayers(sp.getUUID()) > 0
                    && !isDuelParticipant(sp)) {
                WesternCowboyComponent.consumeShield(sp.getUUID());
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§e护盾抵消了这次死亡"), true);
                sp.playNotifySound(net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                        net.minecraft.sounds.SoundSource.MASTER, 0.5F, 1.0F);
                return false;
            }
            return true;
        });

        LOGGER.info("Western Cowboy Addon initialized!");

        // Initialize Vengeance Agent role (HuaRoleMods addon) as a separate role
        new xiao.hua.Huarolemods().onInitialize();
    }

    private static boolean isDuelParticipant(net.minecraft.server.level.ServerPlayer sp) {
        for (var comp : WesternCowboyComponent.getAllActive()) {
            if (comp.isDuelParticipant(sp.getUUID()) && comp.isInArena()) return true;
        }
        return false;
    }

    private static void logStructureTemplatePresence() {
        if (CowboyConfig.structureTemplateParts.isEmpty()) {
            LOGGER.info("Western Cowboy: no multi-part structure template configured");
            return;
        }
        int found = 0;
        for (int i = 0; i < CowboyConfig.structureTemplateParts.size(); i++) {
            String path = "data/western_cowboy/structures/duel_arena_" + (i + 1) + ".nbt";
            try (var is = CowboyMod.class.getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    found++;
                    LOGGER.info("Western Cowboy: found structure template part {}", path);
                } else {
                    LOGGER.warn("Western Cowboy: missing structure template part {}", path);
                }
            } catch (Exception e) {
                LOGGER.warn("Western Cowboy: failed to check structure template part {}", path, e);
            }
        }
        LOGGER.info("Western Cowboy: structure template parts found {}/{}", found, CowboyConfig.structureTemplateParts.size());
    }
}
