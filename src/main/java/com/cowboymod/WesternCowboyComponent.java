package com.cowboymod;

import com.cowboymod.entity.CowboyPuppetEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class WesternCowboyComponent {
    private static final Map<UUID, WesternCowboyComponent> STORE = new HashMap<>();
    private static final Map<UUID, Integer> SHIELD_MAP = new HashMap<>();

    public static WesternCowboyComponent get(Player p) {
        return STORE.computeIfAbsent(p.getUUID(), u -> new WesternCowboyComponent(p));
    }
    public static List<WesternCowboyComponent> getAllActive() { return new ArrayList<>(STORE.values()); }
    public static void resetAll() { STORE.clear(); SHIELD_MAP.clear(); }
    public static int getShieldLayers(UUID uuid) { return SHIELD_MAP.getOrDefault(uuid, 0); }
    public static boolean consumeShield(UUID uuid) {
        int layers = SHIELD_MAP.getOrDefault(uuid, 0);
        if (layers <= 0) return false;
        if (layers == 1) SHIELD_MAP.remove(uuid);
        else SHIELD_MAP.put(uuid, layers - 1);
        return true;
    }

    /** 检查玩家是否处于安全时间（安全时间内不能购买物品，也不能开启决斗） */
    public static boolean isSafeTime(ServerPlayer sp) {
        if (sp == null) return false;
        try {
            Class<?> modEffects = Class.forName("org.agmas.noellesroles.init.ModEffects");
            Object safeTime = modEffects.getField("SAFE_TIME").get(null);
            if (safeTime == null) return false;
            return sp.hasEffect((net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) safeTime);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 仿照傀儡师：枪/刀命中瞬间直接结束决斗。
     * 如果 shooter 和 victim 是同一组牛仔决斗的双方，则取消这次攻击并把 victim 视为被击中者。
     */
    public static boolean onDuelHit(ServerPlayer shooter, ServerPlayer victim) {
        if (shooter == null || victim == null) return false;
        for (var comp : getAllActive()) {
            if (comp.duelState != DuelState.FIGHTING || comp.duelResolved) continue;
            UUID cowboyUuid = comp.player.getUUID();
            UUID targetUuid = comp.duelTargetUuid;
            boolean shooterIsCowboy = cowboyUuid.equals(shooter.getUUID());
            boolean shooterIsTarget = targetUuid != null && targetUuid.equals(shooter.getUUID());
            boolean victimIsCowboy = cowboyUuid.equals(victim.getUUID());
            boolean victimIsTarget = targetUuid != null && targetUuid.equals(victim.getUUID());
            if ((shooterIsCowboy && victimIsTarget) || (shooterIsTarget && victimIsCowboy)) {
                CowboyMod.LOGGER.info("Duel hit intercepted: shooter={}, victim={}", shooter.getName().getString(), victim.getName().getString());
                comp.onDuelDeath(victim, shooter);
                return true;
            }
        }
        return false;
    }

    private final Player player;
    public enum DuelState { IDLE, COUNTDOWN, FIGHTING, ENDING }
    private DuelState duelState = DuelState.IDLE;
    private UUID duelTargetUuid;
    private int duelCooldownTicks = 0;
    private int countdownTicks = 0, duelTimerTicks = 0;
    private boolean isInArena = false;
    private Vec3 cowPos, tgtPos;
    private float cowYaw, cowPitch, tgtYaw, tgtPitch;
    private int cowPuppet = -1, tgtPuppet = -1;
    private final Map<UUID, List<ItemStack>> saved = new HashMap<>();
    private List<?> savedCowboyMods, savedTargetMods;
    private Object worldModComp;
    private UUID pendingDeath; private int pendingTicks;
    private boolean duelResolved = false;
    private DuelState snapshotState;
    private int snapshotCountdown, snapshotTimer;

    private static final int CD = 240*20, CNT = 3*20, TIME = 35*20;
    private static final double DIST = 18.0;

    private WesternCowboyComponent(Player p) { this.player = p; }

    // ===== Tick =====
    public void serverTick(ServerPlayer sp) {
        checkRefugeeRestore(sp);
        if (duelCooldownTicks > 0 && duelState == DuelState.IDLE) duelCooldownTicks--;

        // Delayed巫毒death for loser
        if (pendingDeath != null) {
            pendingTicks--;
            if (pendingTicks <= 0) {
                var v = sp.getServer().getPlayerList().getPlayer(pendingDeath);
                if (v != null && v.isAlive()) killByVoodoo(v);
                pendingDeath = null; pendingTicks = 0;
            }
        }

        if (duelState == DuelState.COUNTDOWN) {
            countdownTicks--;
            ServerPlayer tsp = getTargetPlayer(sp.getServer());
            // Lock both players
            sp.teleportTo(sp.serverLevel(), CowboyConfig.spawnAX, CowboyConfig.spawnAY, CowboyConfig.spawnAZ,
                    Collections.emptySet(), CowboyConfig.spawnAYaw, 0.0f);
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 255, false, false));
            if (tsp != null) {
                tsp.teleportTo(tsp.serverLevel(), CowboyConfig.spawnBX, CowboyConfig.spawnBY, CowboyConfig.spawnBZ,
                        Collections.emptySet(), CowboyConfig.spawnBYaw, 0.0f);
                tsp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 255, false, false));
            }
            if (countdownTicks <= 0) {
                duelState = DuelState.FIGHTING;
                sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                if (tsp != null) tsp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }

        if (duelState == DuelState.FIGHTING) {
            duelTimerTicks--;
            ServerPlayer tsp = getTargetPlayer(sp.getServer());
            // Fallback detection: target became spectator or disconnected (death wasn't intercepted by our handler)
            if (tsp != null && (tsp.isSpectator() || !tsp.isAlive())) {
                if (!duelResolved) resolveDuel(sp, sp.getUUID());
            }
            else if (sp.isSpectator() || !sp.isAlive()) {
                if (!duelResolved) resolveDuel(sp, duelTargetUuid);
            }
            else if (tsp == null) {
                // Target disconnected
                if (!duelResolved) resolveDuel(sp, sp.getUUID());
            }
            else if (!isInArenaBounds(sp)) cancelDuelLeave(sp, tsp);
            else if (!isInArenaBounds(tsp)) cancelDuelLeave(sp, tsp);
            // 目标在决斗中开启里世界（布袋鬼）——里世界状态下传送/无敌逻辑与决斗冲突，立即取消决斗
            else if (isOtherworldActive(tsp)) {
                CowboyMod.LOGGER.info("Duel cancelled: target {} entered otherworld during duel", tsp.getName().getString());
                cancelDuelLeave(sp, tsp);
            }
            else if (duelTimerTicks <= 0) resolveDuel(sp, null);
        }
    }

    // ===== Start Duel =====
    public String tryStartDuel(ServerPlayer sp, UUID targetUuid) {
        ServerLevel w = sp.serverLevel();
        if (duelCooldownTicks > 0) return "error.cooldown";
        if (isSafeTime(sp)) return "error.safe_time";
        if (isFoolMeetingActive()) return "error.cooldown";
        if (isRefugeeMomentActive(sp)) return "error.cooldown";
        Player t = w.getServer().getPlayerList().getPlayer(targetUuid);
        if (t == null || !t.isAlive()) return "error.target";
        if (!(t instanceof ServerPlayer tsp)) return "error.target";
        if (sp.distanceToSqr(t) > DIST*DIST) return "error.too_far";
        // 疯魔中的玩家不可被拉入决斗
        if (isPsychoActive(tsp)) return "error.madness";
        // 布袋鬼开启里世界时不可被拉入决斗（里世界状态下传送进决斗场会直接死亡）
        if (isOtherworldActive(tsp)) return "error.otherworld";
        if (!deductGold(sp)) return "error.not_enough_gold";

        duelCooldownTicks = CD;
        duelTargetUuid = targetUuid;
        duelResolved = false;
        snapshotState = null;

        // 在专属高空坐标重建决斗场地，避免依赖地图原有建筑或危险区
        DuelArenaBuilder.build(w);

        // Expand safe zone
        expandPlayArea(w);

        // Save positions
        cowPos = sp.position(); cowYaw = sp.getYRot(); cowPitch = sp.getXRot();
        tgtPos = tsp.position(); tgtYaw = tsp.getYRot(); tgtPitch = tsp.getXRot();

        // Save inventories
        saveInv(sp); saveInv(tsp);

        // Spawn puppets at original positions
        cowPuppet = spawnPuppet(sp, cowPos, cowYaw);
        tgtPuppet = spawnPuppet(tsp, tgtPos, tgtYaw);

        // Save and clear all modifiers for both players during duel
        saveAndClearMods(sp, true);
        saveAndClearMods(tsp, false);

        // 注意：不再切换目标角色（旧实现先切换成决斗者、结束再换回 = 重新分配身份，
        // 会清空角色组件进度，如布袋鬼阶段、吉良吉影击杀次数）。
        // 保留原角色进决斗，进度天然保留；modifier 已在上方清空，技能干扰有限。

        // Clear inventories, give revolvers
        clearAndGun(sp); clearAndGun(tsp);

        // Teleport to arena
        tp(sp, CowboyConfig.spawnAX, CowboyConfig.spawnAY, CowboyConfig.spawnAZ, CowboyConfig.spawnAYaw);
        tp(tsp, CowboyConfig.spawnBX, CowboyConfig.spawnBY, CowboyConfig.spawnBZ, CowboyConfig.spawnBYaw);

        duelState = DuelState.COUNTDOWN;
        countdownTicks = CNT;
        duelTimerTicks = TIME;
        isInArena = true;
        return null;
    }

    // ===== Leave Arena Detection =====
    private boolean isInArenaBounds(ServerPlayer sp) {
        double x = sp.getX(), y = sp.getY(), z = sp.getZ();
        return x >= CowboyConfig.arenaMinX && x <= CowboyConfig.arenaMaxX
            && y >= CowboyConfig.arenaMinY && y <= CowboyConfig.arenaMaxY
            && z >= CowboyConfig.arenaMinZ && z <= CowboyConfig.arenaMaxZ;
    }

    private void cancelDuelLeave(ServerPlayer sp, ServerPlayer tsp) {
        // Someone left the arena (e.g. Fool meeting) — cancel duel, refund, reset CD
        duelState = DuelState.IDLE;
        duelCooldownTicks = 0; // reset CD
        duelTimerTicks = 0; countdownTicks = 0;
        duelResolved = true; duelTargetUuid = null; isInArena = false;

        // Restore items and roles（角色始终未切换，无需 restoreRole）
        removeRevolver(sp); restoreInv(sp);
        removeRevolver(tsp); restoreInv(tsp);
        restoreMods(sp, true); restoreMods(tsp, false);

        // Teleport both back to original puppet positions
        tpBack(sp, cowPos, cowYaw, cowPitch);
        tpBack(tsp, tgtPos, tgtYaw, tgtPitch);

        // Remove puppets
        rmPuppet(sp, cowPuppet); cowPuppet = -1;
        rmPuppet(sp, tgtPuppet); tgtPuppet = -1;

        // 清理场地上残留的尸体、掉落物、经验球与箭矢
        if (sp.getServer() != null) cleanupArenaEntities(sp.getServer().overworld());

        // Refund gold
        refundGold(sp);

        sp.sendSystemMessage(Component.literal("§e决斗取消 — 有人离开了决斗场地"), false);
        tsp.sendSystemMessage(Component.literal("§e决斗取消 — 有人离开了决斗场地"), false);
    }

    private void refundGold(ServerPlayer sp) {
        try {
            Class<?> shopComp = Class.forName("io.wifi.starrailexpress.cca.SREPlayerShopComponent");
            var key = shopComp.getField("KEY").get(null);
            Class<?> ccaKey = Class.forName("org.ladysnake.cca.api.v3.component.ComponentKey");
            var comp = ccaKey.getMethod("get", Object.class).invoke(key, sp);
            if (comp != null)
                comp.getClass().getMethod("addToBalance", int.class).invoke(comp, 200);
        } catch (Exception ignored) {}
    }

    // ===== Refugee Moment =====
    /** External cancel (Fool meeting, refugee) — refund gold, reset CD, both go home */
    public void cancelDuelForRefugee() {
        if (!isInArena || duelState == DuelState.IDLE) return;
        try {
            // Get integrated server (works in singleplayer)
            var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
            if (server == null) return;
            ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
            ServerPlayer t = duelTargetUuid != null ?
                server.getPlayerList().getPlayer(duelTargetUuid) : null;
            if (sp == null) return;

            // Restore items, roles, modifiers（角色始终未切换，无需 restoreRole）
            removeRevolver(sp); restoreInv(sp);
            if (t != null) { removeRevolver(t); restoreInv(t); }
            restoreMods(sp, true); if (t != null) restoreMods(t, false);

            // Teleport both back to original puppet positions
            tpBack(sp, cowPos, cowYaw, cowPitch);
            if (t != null) tpBack(t, tgtPos, tgtYaw, tgtPitch);

            // Remove puppets
            rmPuppet(sp, cowPuppet); cowPuppet = -1;
            rmPuppet(sp, tgtPuppet); tgtPuppet = -1;

            // 清理场地上残留的尸体、掉落物、经验球与箭矢
            cleanupArenaEntities(server.overworld());

            // Refund gold + reset CD
            refundGold(sp);
            duelCooldownTicks = 0;
            refugeeCanceled = true;
        } catch (Exception e) { CowboyMod.LOGGER.warn("cancelDuelForRefugee error", e); }
        duelState = DuelState.IDLE;
        duelTargetUuid = null; isInArena = false;
    }

    private boolean refugeeCanceled = false;
    private int refugeeRecheckTicks = 0;

    public void saveDuelSnapshot() {
        if (!isInArena || duelState == DuelState.IDLE) return;
        snapshotState = duelState;
        snapshotCountdown = countdownTicks;
        snapshotTimer = duelTimerTicks;
    }

    /** Restore duel state after refugee moment ended. Called from serverTick. */
    private void checkRefugeeRestore(ServerPlayer sp) {
        if (snapshotState == null || !isInArena) return;
        double dist = sp.distanceToSqr(cowPos.x, cowPos.y, cowPos.z);
        if (dist < 4) {
            duelState = snapshotState;
            countdownTicks = snapshotCountdown;
            duelTimerTicks = snapshotTimer;
            ServerPlayer tsp = getTargetPlayer(sp.getServer());
            tp(sp, CowboyConfig.spawnAX, CowboyConfig.spawnAY, CowboyConfig.spawnAZ, CowboyConfig.spawnAYaw);
            if (tsp != null) tp(tsp, CowboyConfig.spawnBX, CowboyConfig.spawnBY, CowboyConfig.spawnBZ, CowboyConfig.spawnBYaw);
            snapshotState = null;
        }
    }

    // ===== Death Intercepted =====
    public void onDuelDeath(ServerPlayer victim, ServerPlayer killer) {
        CowboyMod.LOGGER.info("onDuelDeath called: victim={}, killer={}, state={}, resolved={}",
                victim.getName().getString(), killer.getName().getString(), duelState, duelResolved);
        if (duelState != DuelState.FIGHTING || duelResolved) {
            CowboyMod.LOGGER.info("onDuelDeath skipped (not fighting or already resolved)");
            return;
        }
        duelResolved = true;
        // Heal victim (death was cancelled)
        victim.setHealth(victim.getMaxHealth());
        victim.setAbsorptionAmount(0);
        // Always resolve with cowboy as sp
        var server = victim.getServer();
        if (server == null) return;
        ServerPlayer cowboy = server.getPlayerList().getPlayer(this.player.getUUID());
        if (cowboy != null) {
            CowboyMod.LOGGER.info("Resolving duel with winner={}", killer.getUUID());
            resolveDuel(cowboy, killer.getUUID());
        } else {
            // Fallback: cowboy offline — just reset state
            duelState = DuelState.IDLE;
            duelTargetUuid = null;
            isInArena = false;
        }
    }

    // ===== Resolve Duel (hit or timeout) =====
    private void resolveDuel(ServerPlayer sp, UUID winnerUuid) {
        if (duelState == DuelState.IDLE || duelState == DuelState.ENDING) return;
        duelState = DuelState.ENDING;
        var server = sp.getServer();
        if (server == null) { duelState = DuelState.IDLE; return; }
        ServerPlayer t = (duelTargetUuid != null) ?
                server.getPlayerList().getPlayer(duelTargetUuid) : null;

        // Clear revolvers
        removeRevolver(sp);
        if (t != null) removeRevolver(t);

        // Restore modifiers（角色始终未切换，无需 restoreRole）
        restoreMods(sp, true);
        if (t != null) restoreMods(t, false);

        // Clear any starter items from role restore, then restore saved items
        sp.getInventory().clearContent();
        if (t != null) t.getInventory().clearContent();
        restoreInv(sp);
        if (t != null) restoreInv(t);

        // Remove puppets
        rmPuppet(sp, cowPuppet); cowPuppet = -1;
        rmPuppet(sp, tgtPuppet); tgtPuppet = -1;

        // Process result — both back to original puppet positions
        if (winnerUuid == null) {
            // Timeout — both alive
            tpBack(sp, cowPos, cowYaw, cowPitch);
            if (t != null && t.isAlive()) tpBack(t, tgtPos, tgtYaw, tgtPitch);
        } else if (winnerUuid.equals(player.getUUID())) {
            // Cowboy wins → back to puppet pos + shield. Loser → puppet pos +巫毒death
            tpBack(sp, cowPos, cowYaw, cowPitch);
            addShield(sp);
            sp.sendSystemMessage(Component.literal("§a决斗胜利！获得一层护盾"), false);
            if (t != null && t.isAlive()) {
                tpBack(t, tgtPos, tgtYaw, tgtPitch);
                pendingDeath = t.getUUID(); pendingTicks = 20;
            }
        } else {
            // Target wins → puppet pos + shield. Cowboy → puppet pos +巫毒death
            if (t != null && t.isAlive()) {
                tpBack(t, tgtPos, tgtYaw, tgtPitch);
                addShield(t);
                t.sendSystemMessage(Component.literal("§a决斗胜利！获得一层护盾"), false);
            }
            tpBack(sp, cowPos, cowYaw, cowPitch);
            pendingDeath = sp.getUUID(); pendingTicks = 20;
        }

        // 清理场地上残留的尸体、掉落物、经验球与箭矢
        cleanupArenaEntities(server.overworld());

        duelState = DuelState.IDLE;
        duelTargetUuid = null;
        isInArena = false;
    }

    private void cleanupArenaEntities(ServerLevel level) {
        AABB bounds = new AABB(
                CowboyConfig.arenaMinX, CowboyConfig.arenaMinY, CowboyConfig.arenaMinZ,
                CowboyConfig.arenaMaxX, CowboyConfig.arenaMaxY, CowboyConfig.arenaMaxZ
        );
        for (Entity entity : level.getEntities(null, bounds)) {
            if (entity instanceof PlayerBodyEntity ||      // 玩家尸体
                entity instanceof ItemEntity ||            // 掉落物
                entity instanceof ExperienceOrb ||         // 经验球
                entity instanceof AbstractArrow ||         // 箭
                entity instanceof Projectile ||            // 所有弹射物（三叉戟、火球、雪球、药水等）
                entity instanceof AreaEffectCloud ||       // 滞留药水云
                entity instanceof PrimedTnt) {             // 点燃的 TNT
                entity.discard();
            }
        }
    }

    // ===== Shields (custom stackable shield, since SREHumanoidArmorPlayerComponent is gone) =====
    private void addShield(ServerPlayer sp) {
        SHIELD_MAP.merge(sp.getUUID(), 1, Integer::sum);
    }

    public int getShieldLayers() {
        return SHIELD_MAP.getOrDefault(player.getUUID(), 0);
    }

    // =====巫毒Death =====
    private void killByVoodoo(Player v) {
        try {
            Class<?> gu = Class.forName("io.wifi.starrailexpress.game.GameUtils");
            gu.getMethod("forceKillPlayer",
                    Player.class, boolean.class,
                    Player.class,
                    ResourceLocation.class)
                    .invoke(null, v, true, null,
                            ResourceLocation.fromNamespaceAndPath("noellesroles", "voodoo"));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ===== Gold =====
    private boolean deductGold(ServerPlayer sp) {
        try {
            Class<?> shopComp = Class.forName("io.wifi.starrailexpress.cca.SREPlayerShopComponent");
            var key = shopComp.getField("KEY").get(null);
            Class<?> ccaKey = Class.forName("org.ladysnake.cca.api.v3.component.ComponentKey");
            var comp = ccaKey.getMethod("get", Object.class).invoke(key, sp);
            if (comp == null) return false;
            int balance = shopComp.getField("balance").getInt(comp);
            if (balance < 200) return false;
            comp.getClass().getMethod("addToBalance", int.class).invoke(comp, -200);
            return true;
        } catch (Exception e) { CowboyMod.LOGGER.warn("Gold deduct failed", e); return false; }
    }

    // ===== Safe Zone =====
    private void expandPlayArea(ServerLevel w) {
        try {
            Class<?> areasCls = Class.forName("io.wifi.starrailexpress.cca.AreasWorldComponent");
            var key = areasCls.getField("KEY").get(null);
            Class<?> ccaKey = Class.forName("org.ladysnake.cca.api.v3.component.ComponentKey");
            var comp = ccaKey.getMethod("get", Object.class).invoke(key, w);
            if (comp == null) return;
            var orig = comp.getClass().getMethod("getPlayArea").invoke(comp);
            if (orig == null) return;
            net.minecraft.world.phys.AABB box = (net.minecraft.world.phys.AABB) orig;
            double nx = Math.min(box.minX, CowboyConfig.arenaMinX);
            double ny = Math.min(box.minY, CowboyConfig.arenaMinY);
            double nz = Math.min(box.minZ, CowboyConfig.arenaMinZ);
            double mx = Math.max(box.maxX, CowboyConfig.arenaMaxX);
            double my = Math.max(box.maxY, CowboyConfig.arenaMaxY);
            double mz = Math.max(box.maxZ, CowboyConfig.arenaMaxZ);
            var nb = new net.minecraft.world.phys.AABB(nx, ny, nz, mx, my, mz);
            comp.getClass().getMethod("setPlayArea", net.minecraft.world.phys.AABB.class).invoke(comp, nb);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ===== Helpers =====
    /** 目标是否处于疯魔（Psycho）状态——疯魔中不可被拉入决斗 */
    private static boolean isPsychoActive(ServerPlayer sp) {
        try {
            Class<?> compCls = Class.forName("io.wifi.starrailexpress.cca.SREPlayerPsychoComponent");
            var key = compCls.getField("KEY").get(null);
            Class<?> ccaKey = Class.forName("org.ladysnake.cca.api.v3.component.ComponentKey");
            var comp = ccaKey.getMethod("get", Object.class).invoke(key, sp);
            if (comp == null) return false;
            int ticks = (int) comp.getClass().getMethod("getPsychoTicks").invoke(comp);
            return ticks >= 0;
        } catch (Exception e) { return false; }
    }

    /** 布袋鬼是否开启了里世界（otherworldActive）——里世界中不可被拉入决斗，决斗中开启则取消 */
    private static boolean isOtherworldActive(ServerPlayer sp) {
        try {
            Class<?> compCls = Class.forName("org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent");
            var key = compCls.getField("KEY").get(null);
            Class<?> ccaKey = Class.forName("org.ladysnake.cca.api.v3.component.ComponentKey");
            var comp = ccaKey.getMethod("get", Object.class).invoke(key, sp);
            if (comp == null) return false;
            return (boolean) compCls.getField("otherworldActive").get(comp);
        } catch (Exception e) { return false; }
    }

    private static boolean isFoolMeetingActive() {
        try {
            Class<?> tam = Class.forName(
                "org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager");
            return tam.getField("havingMeeting").getBoolean(null);
        } catch (Exception e) { return false; }
    }

    private static boolean isRefugeeMomentActive(ServerPlayer sp) {
        try {
            Class<?> rc = Class.forName(
                "pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent");
            var key = rc.getField("KEY").get(null);
            Class<?> ccaKey = Class.forName("org.ladysnake.cca.api.v3.component.ComponentKey");
            var comp = ccaKey.getMethod("get", Object.class).invoke(key, sp.serverLevel());
            if (comp == null) return false;
            return (boolean) comp.getClass().getField("isAnyRevivals").get(comp)
                || (boolean) comp.getClass().getField("isPendingRestore").get(comp);
        } catch (Exception e) { return false; }
    }

    public boolean isDuelParticipant(UUID uuid) {
        return uuid.equals(player.getUUID()) || uuid.equals(duelTargetUuid);
    }

    private void saveAndClearMods(ServerPlayer sp, boolean isCowboy) {
        try {
            Class<?> wmc = Class.forName("org.agmas.harpymodloader.component.WorldModifierComponent");
            var key = wmc.getField("KEY").get(null);
            Class<?> ccaKey = Class.forName("org.ladysnake.cca.api.v3.component.ComponentKey");
            if (worldModComp == null) worldModComp = ccaKey.getMethod("get", Object.class).invoke(key, sp.serverLevel());
            if (worldModComp == null) return;
            var mods = (java.util.HashSet<?>) worldModComp.getClass().getMethod("getModifiers",
                    Player.class).invoke(worldModComp, sp);
            if (mods == null || mods.isEmpty()) return;
            if (isCowboy) savedCowboyMods = new ArrayList<>(mods);
            else savedTargetMods = new ArrayList<>(mods);
            for (Object mod : new ArrayList<>(mods)) {
                String name = mod.getClass().getSimpleName().toLowerCase();
                // Keep SplitPersonality and Lovers (multi-player bound modifiers)
                if (name.contains("split") || name.contains("lover")) continue;
                worldModComp.getClass().getMethod("removeModifier", UUID.class,
                        Class.forName("org.agmas.harpymodloader.modifiers.SREModifier"))
                        .invoke(worldModComp, sp.getUUID(), mod);
            }
        } catch (Exception ignored) {}
    }

    private void restoreMods(ServerPlayer sp, boolean isCowboy) {
        try {
            var saved = isCowboy ? savedCowboyMods : savedTargetMods;
            if (saved == null || saved.isEmpty() || worldModComp == null) return;
            for (Object mod : saved) {
                try {
                    worldModComp.getClass().getMethod("addModifier", UUID.class,
                            Class.forName("org.agmas.harpymodloader.modifiers.SREModifier"))
                            .invoke(worldModComp, sp.getUUID(), mod);
                } catch (Exception ignored) {}
            }
            if (isCowboy) savedCowboyMods = null;
            else savedTargetMods = null;
        } catch (Exception ignored) {}
    }

    private void tp(ServerPlayer sp, double x, double y, double z, float yaw) {
        sp.stopSleeping();
        sp.stopRiding();
        sp.teleportTo(sp.serverLevel(), x, y, z, Collections.emptySet(), yaw, 0.0f);
        sp.setDeltaMovement(0.0D, 0.0D, 0.0D);
        sp.fallDistance = 0.0F;
    }
    private void tpBack(Player p, Vec3 pos, float yaw, float pitch) {
        if (p instanceof ServerPlayer sp)
            sp.teleportTo(sp.serverLevel(), pos.x, pos.y, pos.z, Collections.emptySet(), yaw, pitch);
    }
    private int spawnPuppet(ServerPlayer o, Vec3 pos, float yaw) {
        var p = new CowboyPuppetEntity(CowboyMod.COWBOY_PUPPET, o.serverLevel());
        p.setOwner(o); p.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        o.serverLevel().addFreshEntity(p);
        return p.getId();
    }
    private void rmPuppet(ServerPlayer sp, int id) {
        if (id <= 0) return;
        var e = sp.serverLevel().getEntity(id);
        if (e instanceof CowboyPuppetEntity) e.discard();
    }
    private void saveInv(Player p) {
        List<ItemStack> b = new ArrayList<>();
        var inv = p.getInventory(); for (int i = 0; i < inv.getContainerSize(); i++) b.add(inv.getItem(i).copy());
        saved.put(p.getUUID(), b);
    }
    private void restoreInv(Player p) {
        var b = saved.get(p.getUUID()); if (b == null) return;
        var inv = p.getInventory(); inv.clearContent();
        for (int i = 0; i < Math.min(b.size(), inv.getContainerSize()); i++) inv.setItem(i, b.get(i).copy());
    }
    private void removeRevolver(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            // Only remove the duel revolver (has custom name "牛仔左轮")
            if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                if (name != null && name.getString().contains("牛仔左轮"))
                    inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private void clearAndGun(Player p) {
        var inv = p.getInventory(); inv.clearContent();
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("trainmurdermystery", "revolver"));
        if (item != null) {
            var g = new ItemStack(item);
            g.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("牛仔左轮"));
            inv.setItem(0, g);
        }
    }
    private ServerPlayer getTargetPlayer(net.minecraft.server.MinecraftServer server) {
        if (duelTargetUuid == null) return null;
        Player p = server.getPlayerList().getPlayer(duelTargetUuid);
        return p instanceof ServerPlayer sp ? sp : null;
    }

    // ===== Getters =====
    public DuelState getDuelState() { return duelState; }
    public int getCooldownTicks() { return duelCooldownTicks; }
    public int getCountdownTicks() { return countdownTicks; }
    public int getDuelTimerTicks() { return duelTimerTicks; }
    public Vec3 getCowPos() { return cowPos; }
    public boolean isRefugeeCanceled() { return refugeeCanceled; }
    public void clearRefugeeCanceled() { refugeeCanceled = false; }
    public UUID getDuelTargetUuid() { return duelTargetUuid; }
    public boolean isInArena() { return isInArena; }
}
