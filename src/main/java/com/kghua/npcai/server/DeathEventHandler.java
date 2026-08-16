package com.kghua.npcai.server;

import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.data.CerebellumDetailStore;
import com.kghua.npcai.data.CerebellumSettings;
import com.kghua.npcai.data.CerebellumStorage;
import com.kghua.npcai.data.CompensationRule;
import com.kghua.npcai.data.CompensationStorage;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;

import java.util.UUID;

public class DeathEventHandler {

    public static void register() {
        OnPlayerDeath.EVENT.register(DeathEventHandler::onPlayerDeath);
        OnPlayerKilledPlayer.EVENT.register(DeathEventHandler::onPlayerKilledPlayer);
        // 游戏结束时结算小脑惩罚：按 UUID 给在线真实玩家累计"待执行惩罚"，
        // 不立即执行，等玩家下次成为杀手阵营角色时消耗
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register(DeathEventHandler::onGameEnd);
        // 游戏初始化（早于游戏状态 ACTIVE，所有模式通用）：清空本局触发守卫
        org.agmas.harpymodloader.events.GameInitializeEvent.EVENT.register(DeathEventHandler::onGameInitialize);
        // 角色分配确认前（addRole/给物品之前）：把待执行惩罚玩家的杀手身份直接替换成强盗，
        // 让开局物品/修饰符直接按强盗身份给（标准模式/躲猫猫走此事件）
        org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm.EVENT.register(DeathEventHandler::onBeforeAssignRole);
        // 兜底：未走分配确认事件的其他模式（funny 模式等），在玩家被分配角色时强制强盗 + 修饰符
        org.agmas.harpymodloader.events.ModdedRoleAssigned.EVENT.register(DeathEventHandler::onModdedRoleAssigned);
        // 本局已受小脑惩罚的玩家：基座随机分配的修饰符一出现就移除，并补齐缺失的强制修饰符
        org.agmas.harpymodloader.events.ModifierAssigned.EVENT.register(DeathEventHandler::onModifierAssigned);
        // 持续确保：每 20 tick 检查一次惩罚玩家的修饰符集合 = 勾选集合（覆盖随机分配0个/时序等所有情况）
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CEREBELLUM_TRIGGERED_THIS_GAME.isEmpty()) return;
            if (server.getTickCount() % 20 != 0) return;
            for (UUID uuid : CEREBELLUM_TRIGGERED_THIS_GAME) {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp != null) ensureForcedModifiers(sp);
            }
        });
    }

    // 被队友误杀的玩家（victim UUID → 死亡时用于补偿匹配"被队友误杀"）
    private static final java.util.Set<UUID> TEAMMATE_KILLS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // 一局游戏内已触发小脑惩罚的玩家（防止同局多次角色分配重复消耗惩罚）
    private static final java.util.Set<UUID> CEREBELLUM_TRIGGERED_THIS_GAME = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 游戏结束时结算小脑惩罚：
     * 只遍历在线真实玩家（按 UUID 结算，不再扫计分板全部条目，根除假玩家/改名错罚），
     * 小脑次数 ≥ 所需次数 → 扣分、惩罚次数 +1、待执行次数 +1（存 cerebellum.json，可叠加），
     * 不在下局立即执行，等玩家下次成为杀手阵营角色时消耗。
     * 离线玩家的分数留在计分板，下次在线时某局结束自动结算。
     */
    private static void onGameEnd(net.minecraft.server.level.ServerLevel serverLevel, Object gameWorldComponent) {
        try {
            MinecraftServer server = serverLevel.getServer();
            CerebellumSettings settings = CerebellumStorage.getSettings();
            int required = settings.getRequiredDeaths();
            if (required <= 0) return;

            Scoreboard scoreboard = server.getScoreboard();
            Objective currentObj = scoreboard.getObjective("kgxnbang");
            Objective punishObj = scoreboard.getObjective("kgxnbang_punish");
            if (currentObj == null) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(player, currentObj);
                    int score = scoreAccess.get();
                    if (score < required) continue;

                    // 结算：减 required 次，惩罚次数 +1，待执行次数 +1
                    scoreAccess.set(score - required);
                    if (punishObj != null) {
                        ScoreAccess p = scoreboard.getOrCreatePlayerScore(player, punishObj);
                        p.set(p.get() + 1);
                    }
                    CerebellumStorage.settlePenalty(player.getUUID(), required);
                    NpcAiMod.LOGGER.info("小脑惩罚结算：{} 小脑次数 {} -> {}，惩罚次数+1，待执行+1",
                        player.getScoreboardName(), score, score - required);
                } catch (Exception ignored) {
                }
            }
            TEAMMATE_KILLS.clear();
            CEREBELLUM_TRIGGERED_THIS_GAME.clear(); // 下局重新可触发
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to settle cerebellum penalty at game end", e);
        }
    }

    /**
     * 游戏初始化（任何模式开局前必触发）：清空本局触发守卫，覆盖异常重启场景。
     */
    private static void onGameInitialize(net.minecraft.server.level.ServerLevel serverLevel,
                                         SREGameWorldComponent gameWorldComponent,
                                         java.util.List<ServerPlayer> readyPlayerList) {
        CEREBELLUM_TRIGGERED_THIS_GAME.clear();
    }

    /**
     * 角色分配确认前（addRole/给物品/给修饰符之前）：
     * 把待执行惩罚玩家的杀手身份在分配表里直接替换成强盗，
     * 使开局物品/修饰符直接按强盗身份给，不再先给原身份的物品。
     * 强制修饰符延后到分配流程结束后施加（基座 assignModifiers 会先清空全部修饰符再按概率随机给）。
     */
    private static void onBeforeAssignRole(net.minecraft.server.level.ServerLevel level,
                                           java.util.Map<net.minecraft.world.entity.player.Player,
                                               io.wifi.starrailexpress.api.SRERole> roleMap) {
        try {
            if (roleMap == null) return;
            for (var it = roleMap.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                net.minecraft.world.entity.player.Player p = entry.getKey();
                io.wifi.starrailexpress.api.SRERole role = entry.getValue();
                if (!(p instanceof ServerPlayer sp)) continue;
                // 仅杀手阵营角色触发（杀手中立/独赢中立不算）
                if (role == null || !SREGameWorldComponent.isKillerTeamRoleStatic(role)) continue;
                if (CerebellumStorage.getPendingCount(sp.getUUID()) <= 0) continue;
                // 本局已触发过则跳过
                if (!CEREBELLUM_TRIGGERED_THIS_GAME.add(sp.getUUID())) continue;

                CerebellumStorage.consumePending(sp.getUUID());
                io.wifi.starrailexpress.api.SRERole bandit = resolveBanditRole();
                if (bandit != null) {
                    entry.setValue(bandit); // 分配表直接替换成强盗，后续 addRole/物品按强盗给
                    NpcAiMod.LOGGER.info("小脑惩罚：{} 分配前直接将身份替换为强盗（原 {}）",
                        sp.getName().getString(), role.identifier());
                } else {
                    NpcAiMod.LOGGER.warn("小脑惩罚：{} 强盗角色获取失败，本次不替换身份", sp.getName().getString());
                }
                // 施加强制修饰符：基座随机修饰符分配（assignModifiers）会先清空全部修饰符再按概率随机给，
                // 因此延后到分配流程结束后执行（此时清除残留并全量施加勾选的修饰符）
                MinecraftServer server = sp.getServer();
                if (server != null) {
                    server.execute(() -> applyForcedModifiers(sp));
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to override role before assignment", e);
        }
    }

    /**
     * 兜底路径（未走分配确认事件的其他模式，如部分 funny 模式）：
     * 玩家被分配杀手阵营角色时，立即强制强盗 + 清除已有修饰符 + 施加勾选修饰符。
     */
    private static void onModdedRoleAssigned(ServerPlayer player, io.wifi.starrailexpress.api.SRERole role) {
        try {
            if (player == null || role == null) return;
            // 仅杀手阵营角色触发（杀手中立/独赢中立不算）
            if (!SREGameWorldComponent.isKillerTeamRoleStatic(role)) return;
            if (CerebellumStorage.getPendingCount(player.getUUID()) <= 0) return;
            // 本局已触发过则跳过（分配前主路径已处理，或 changeRole 重入本事件）
            if (!CEREBELLUM_TRIGGERED_THIS_GAME.add(player.getUUID())) return;

            // 先消耗待执行惩罚（再执行惩罚内容），防止重入时重复消耗
            CerebellumStorage.consumePending(player.getUUID());

            MinecraftServer server = player.getServer();
            if (server == null) return;
            // 玩家已离线则跳过
            if (server.getPlayerList().getPlayer(player.getUUID()) == null) {
                NpcAiMod.LOGGER.warn("小脑惩罚：{} 执行时已离线，惩罚内容跳过", player.getName().getString());
                return;
            }

            // 强制强盗角色（多重兜底获取 + 命令兜底）
            io.wifi.starrailexpress.api.SRERole bandit = resolveBanditRole();
            boolean roleChanged = false;
            if (bandit != null) {
                try {
                    org.agmas.noellesroles.utils.RoleUtils.changeRole(player, bandit);
                    roleChanged = true;
                    NpcAiMod.LOGGER.info("小脑惩罚：{} changeRole -> {}", player.getName().getString(), bandit.identifier());
                } catch (Exception e) {
                    NpcAiMod.LOGGER.error("小脑惩罚：{} changeRole 失败", player.getName().getString(), e);
                }
            }
            if (!roleChanged) {
                // 命令兜底（管理端 changeRole <玩家> bandit 同路径）
                try {
                    executeCommand(server, "changeRole " + player.getName().getString() + " bandit",
                        player.getName().getString());
                    roleChanged = true;
                    NpcAiMod.LOGGER.info("小脑惩罚：{} 通过命令强制强盗成功", player.getName().getString());
                } catch (Exception e) {
                    NpcAiMod.LOGGER.error("小脑惩罚：{} 命令强制强盗失败", player.getName().getString(), e);
                }
            }
            if (!roleChanged) {
                NpcAiMod.LOGGER.error("小脑惩罚：{} 强盗角色获取与命令均失败，仅施加修饰符", player.getName().getString());
            }

            // 清除已有修饰符并施加勾选修饰符（延后到分配流程结束后，基座 assignModifiers 会先清空全部修饰符）
            server.execute(() -> applyForcedModifiers(player));
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to apply cerebellum punishment", e);
        }
    }

    /**
     * 清除玩家已有修饰符（覆盖原身份的修饰符）并施加勾选的惩罚修饰符。
     * 每环节独立 try-catch，一个失败不影响其他环节，并记录详细日志便于排查。
     */
    private static void applyForcedModifiers(ServerPlayer player) {
        try {
            org.agmas.harpymodloader.component.WorldModifierComponent wmc =
                org.agmas.harpymodloader.component.WorldModifierComponent.KEY.get(player.serverLevel());
            CerebellumSettings settings = CerebellumStorage.getSettings();
            // 记录当前惩罚设置，便于排查勾选是否已保存到服务端
            NpcAiMod.LOGGER.info("小脑惩罚执行：{} 当前惩罚设置 [诅咒={}, 高大={}, 晕血症={}, 纳税={}, 偏执={}, 沙哑={}]",
                player.getName().getString(),
                settings.isCursedEnabled(), settings.isTallEnabled(), settings.isHemophobiaEnabled(),
                settings.isTaxedEnabled(), settings.isParanoidEnabled(), settings.isHoarseEnabled());

            // 清除玩家身上已有的所有修饰符（覆盖原身份开局修饰符）
            try {
                java.util.Set<org.agmas.harpymodloader.modifiers.SREModifier> existing =
                    wmc.getModifiers(player.getUUID());
                for (org.agmas.harpymodloader.modifiers.SREModifier m :
                        new java.util.HashSet<>(existing)) {
                    wmc.removeModifier(player.getUUID(), m);
                    NpcAiMod.LOGGER.info("小脑惩罚：{} 清除原有修饰符 {}", player.getName().getString(), m.identifier());
                }
            } catch (Exception e) {
                NpcAiMod.LOGGER.error("小脑惩罚：{} 清除原有修饰符失败", player.getName().getString(), e);
            }

            int applied = 0;
            if (settings.isCursedEnabled() && applyModifier(wmc, player, CerebellumSettings.MOD_CURSED)) applied++;
            if (settings.isTallEnabled() && applyModifier(wmc, player, CerebellumSettings.MOD_TALL)) applied++;
            if (settings.isHemophobiaEnabled() && applyModifier(wmc, player, CerebellumSettings.MOD_HEMOPHOBIA)) applied++;
            if (settings.isTaxedEnabled() && applyModifier(wmc, player, CerebellumSettings.MOD_TAXED)) applied++;
            if (settings.isParanoidEnabled() && applyModifier(wmc, player, CerebellumSettings.MOD_PARANOID)) applied++;
            if (settings.isHoarseEnabled() && applyModifier(wmc, player, CerebellumSettings.MOD_HOARSE)) applied++;

            player.sendSystemMessage(Component.literal("§c小脑惩罚执行：你已被变为杀手阵营角色"
                + (applied > 0 ? "，并受到惩罚修饰" : "")));
            NpcAiMod.LOGGER.info("小脑惩罚执行：{} 修饰符施加 {} 个，剩余待执行 {}",
                player.getName().getString(), applied,
                CerebellumStorage.getPendingCount(player.getUUID()));
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("小脑惩罚：{} 修饰符施加失败", player.getName().getString(), e);
        }
    }

    /**
     * 本局已受小脑惩罚的玩家：基座随机分配的修饰符（ModifierAssigned 事件）出现时立即移除，
     * 并补齐缺失的强制修饰符。施加强制修饰符也会触发本事件，但强制集合内的放行。
     */
    private static void onModifierAssigned(net.minecraft.world.entity.player.Player player,
                                           org.agmas.harpymodloader.modifiers.SREModifier modifier) {
        try {
            if (player == null || modifier == null || modifier.identifier() == null) return;
            if (!(player instanceof ServerPlayer sp)) return;
            if (!CEREBELLUM_TRIGGERED_THIS_GAME.contains(sp.getUUID())) return;
            if (isForcedModifier(modifier.identifier())) return; // 强制修饰符放行
            // 随机分配的修饰符立即移除，并确保强制修饰符完整
            org.agmas.harpymodloader.component.WorldModifierComponent wmc =
                org.agmas.harpymodloader.component.WorldModifierComponent.KEY.get(sp.level());
            wmc.removeModifier(sp.getUUID(), modifier);
            NpcAiMod.LOGGER.info("小脑惩罚：{} 移除随机分配的修饰符 {}", sp.getName().getString(), modifier.identifier());
            ensureForcedModifiers(sp);
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("小脑惩罚：拦截随机修饰符异常", e);
        }
    }

    /**
     * 持续确保玩家的修饰符集合 = 当前勾选的惩罚修饰符集合：
     * 移除所有不在勾选集合内的修饰符，补上缺失的勾选修饰符。
     * 无提示消息，由事件与周期 tick 调用，保证最终状态正确。
     */
    private static void ensureForcedModifiers(ServerPlayer sp) {
        try {
            if (sp == null || sp.getServer() == null) return;
            org.agmas.harpymodloader.component.WorldModifierComponent wmc =
                org.agmas.harpymodloader.component.WorldModifierComponent.KEY.get(sp.serverLevel());
            CerebellumSettings settings = CerebellumStorage.getSettings();
            java.util.Set<org.agmas.harpymodloader.modifiers.SREModifier> current =
                wmc.getModifiers(sp.getUUID());
            // 移除不在勾选集合内的修饰符（随机分配的残留）
            for (org.agmas.harpymodloader.modifiers.SREModifier m : new java.util.HashSet<>(current)) {
                if (m.identifier() == null) continue;
                if (!isForcedModifier(m.identifier())) {
                    wmc.removeModifier(sp.getUUID(), m);
                    NpcAiMod.LOGGER.info("小脑惩罚：{} 清理非强制修饰符 {}", sp.getName().getString(), m.identifier());
                }
            }
            // 补上缺失的勾选修饰符（全量确保）
            applyModifier(wmc, sp, CerebellumSettings.MOD_CURSED, settings.isCursedEnabled());
            applyModifier(wmc, sp, CerebellumSettings.MOD_TALL, settings.isTallEnabled());
            applyModifier(wmc, sp, CerebellumSettings.MOD_HEMOPHOBIA, settings.isHemophobiaEnabled());
            applyModifier(wmc, sp, CerebellumSettings.MOD_TAXED, settings.isTaxedEnabled());
            applyModifier(wmc, sp, CerebellumSettings.MOD_PARANOID, settings.isParanoidEnabled());
            applyModifier(wmc, sp, CerebellumSettings.MOD_HOARSE, settings.isHoarseEnabled());
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("小脑惩罚：确保强制修饰符异常", e);
        }
    }

    /** 该修饰符 ID 是否为当前勾选的惩罚修饰符 */
    private static boolean isForcedModifier(ResourceLocation id) {
        CerebellumSettings settings = CerebellumStorage.getSettings();
        return (settings.isCursedEnabled() && CerebellumSettings.MOD_CURSED.equals(id))
            || (settings.isTallEnabled() && CerebellumSettings.MOD_TALL.equals(id))
            || (settings.isHemophobiaEnabled() && CerebellumSettings.MOD_HEMOPHOBIA.equals(id))
            || (settings.isTaxedEnabled() && CerebellumSettings.MOD_TAXED.equals(id))
            || (settings.isParanoidEnabled() && CerebellumSettings.MOD_PARANOID.equals(id))
            || (settings.isHoarseEnabled() && CerebellumSettings.MOD_HOARSE.equals(id));
    }

    /** 施加单个修饰符（若启用），未注册时跳过并记录日志 */
    private static void applyModifier(org.agmas.harpymodloader.component.WorldModifierComponent wmc,
                                      ServerPlayer player, ResourceLocation id, boolean enabled) {
        if (!enabled) return;
        try {
            org.agmas.harpymodloader.modifiers.SREModifier mod =
                org.agmas.harpymodloader.modifiers.HMLModifiers.getModifier(id);
            if (mod == null) {
                NpcAiMod.LOGGER.warn("小脑惩罚：修饰符 {} 未注册，已跳过", id);
                return;
            }
            wmc.addModifier(player.getUUID(), mod);
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("小脑惩罚：修饰符 {} 施加异常", id, e);
        }
    }

    /**
     * 获取强盗角色（多重兜底）：
     * 1. 注册表按完整 ID 查询（noellesroles:bandit）
     * 2. 遍历注册表匹配 path 为 bandit 的角色
     * 3. ModRoles.BANDIT 静态字段（可能被其他 mod 替换，放最后）
     */
    private static io.wifi.starrailexpress.api.SRERole resolveBanditRole() {
        try {
            io.wifi.starrailexpress.api.SRERole role = org.agmas.noellesroles.utils.RoleUtils.getRole(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("noellesroles", "bandit"));
            if (role != null) return role;
        } catch (Exception e) {
            NpcAiMod.LOGGER.warn("小脑惩罚：注册表查询强盗角色异常", e);
        }
        try {
            for (io.wifi.starrailexpress.api.SRERole r : io.wifi.starrailexpress.api.TMMRoles.ROLES.values()) {
                if (r.identifier() != null
                    && ("bandit".equalsIgnoreCase(r.identifier().getPath())
                        || "noellesroles:bandit".equalsIgnoreCase(r.identifier().toString()))) {
                    return r;
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.warn("小脑惩罚：遍历注册表查找强盗角色异常", e);
        }
        try {
            return org.agmas.noellesroles.role.ModRoles.BANDIT;
        } catch (Exception e) {
            NpcAiMod.LOGGER.warn("小脑惩罚：ModRoles.BANDIT 获取失败", e);
        }
        return null;
    }

    /** 施加单个修饰符（未注册时跳过并记录日志），返回是否成功施加 */
    private static boolean applyModifier(org.agmas.harpymodloader.component.WorldModifierComponent wmc,
                                         ServerPlayer player, ResourceLocation id) {
        try {
            org.agmas.harpymodloader.modifiers.SREModifier mod =
                org.agmas.harpymodloader.modifiers.HMLModifiers.getModifier(id);
            if (mod == null) {
                NpcAiMod.LOGGER.warn("小脑惩罚：修饰符 {} 未注册，已跳过", id);
                return false;
            }
            wmc.addModifier(player.getUUID(), mod);
            return true;
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("小脑惩罚：修饰符 {} 施加异常", id, e);
            return false;
        }
    }

    private static void onPlayerDeath(Player victim, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer serverVictim)) return;
        MinecraftServer server = serverVictim.getServer();
        if (server == null) return;

        String reasonStr = deathReason != null ? deathReason.toString() : "";

        // 被队友误杀：由 OnPlayerKilledPlayer 标记，死亡时匹配"被队友误杀"补偿规则
        if (TEAMMATE_KILLS.remove(serverVictim.getUUID())) {
            reasonStr = "western_cowboy:teammate_kill";
        }

        // 补偿规则
        for (CompensationRule rule : CompensationStorage.getAll()) {
            if (!reasonStr.equals(rule.getDeathReason())) continue;

            CompensationStorage.incrementDeathCount(serverVictim.getUUID());
            int count = CompensationStorage.getDeathCount(serverVictim.getUUID());
            if (count >= rule.getRequiredDeaths()) {
                for (CompensationRule.CommandEntry entry : rule.getCommands()) {
                    executeCommand(server, entry.command, serverVictim.getName().getString());
                }
                CompensationStorage.resetDeathCount(serverVictim.getUUID());
            }
        }

        // 小脑死亡（错杀好人）：SRE 将误杀者以 shot_innocent 处死，
        // 因此因小脑死亡的 victim 就是小脑玩家 → 小脑次数 +1
        CerebellumSettings settings = CerebellumStorage.getSettings();
        if (settings.isWrongKillInnocentEnabled()
            && ("noellesroles:shot_innocent".equals(reasonStr) || "starrailexpress:shot_innocent".equals(reasonStr))) {
            CerebellumStorage.incrementDeathCount(serverVictim.getUUID());
            incrementCerebellumScore(server, serverVictim);
            CerebellumDetailStore.add(serverVictim.getUUID(), serverVictim.getName().getString(),
                CerebellumDetailStore.KIND_CIVILIAN_SELF_KILL, null);
            NpcAiMod.LOGGER.info("小脑榜：{} 因小脑死亡（shot_innocent），小脑次数 +1", serverVictim.getName().getString());
        } else if (settings.isWrongKillInnocentEnabled() && reasonStr.contains("innocent")) {
            NpcAiMod.LOGGER.info("小脑榜：检测到 innocent 死亡原因但未匹配：{}（victim={}）", reasonStr, serverVictim.getName().getString());
        }
    }

    // 注意：SRE 事件签名是 (victim, killer, reason) —— killer 是击杀者，victim 是死者
    private static void onPlayerKilledPlayer(ServerPlayer victim, ServerPlayer killer, OnPlayerKilledPlayer.DeathReason reason) {
        if (killer == null || victim == null) return;
        MinecraftServer server = killer.getServer();
        if (server == null) return;

        CerebellumSettings settings = CerebellumStorage.getSettings();
        if (killer == victim) return;

        SREGameWorldComponent world = SREGameWorldComponent.KEY.get(killer.level());

        // 队友误杀判定：好人杀好人 或 杀手杀杀手（中立不算）
        boolean killerIsKiller = world.isKillerTeam(killer);
        boolean victimIsKiller = world.isKillerTeam(victim);
        boolean killerIsInnocent = world.isInnocent(killer);
        boolean victimIsInnocent = world.isInnocent(victim);
        boolean sameSide = (killerIsKiller && victimIsKiller) || (killerIsInnocent && victimIsInnocent);
        if (sameSide) {
            TEAMMATE_KILLS.add(victim.getUUID());
        }

        if (!killerIsKiller || !victimIsKiller) return;

        boolean shouldCount = false;
        if (settings.isKillerTeamKillNoGrenadeEnabled() && reason != OnPlayerKilledPlayer.DeathReason.GRENADE) {
            shouldCount = true;
        }
        if (settings.isKillerTeamKillGrenadeOnlyEnabled() && reason == OnPlayerKilledPlayer.DeathReason.GRENADE) {
            shouldCount = true;
        }

        if (shouldCount) {
            // 杀手互杀/手雷互杀：击杀者（killer）小脑次数 +1
            CerebellumStorage.incrementDeathCount(killer.getUUID());
            incrementCerebellumScore(server, killer);
            String kind = reason == OnPlayerKilledPlayer.DeathReason.GRENADE
                ? CerebellumDetailStore.KIND_KILLER_TEAMKILL_GRENADE
                : CerebellumDetailStore.KIND_KILLER_TEAMKILL;
            CerebellumDetailStore.add(killer.getUUID(), killer.getName().getString(), kind, victim.getName().getString());
            NpcAiMod.LOGGER.info("小脑榜：杀手互杀 {} 击杀 {}，killer 小脑 +1", killer.getName().getString(), victim.getName().getString());
        }
    }

    /**
     * 小脑次数 +1（以计分板当前分数为基准，不再覆盖计分板）：
     * 游戏开始前的惩罚扣分不会被这里覆盖回去。
     */
    private static void incrementCerebellumScore(MinecraftServer server, Player player) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective("kgxnbang");
        if (objective == null) {
            objective = scoreboard.addObjective(
                "kgxnbang",
                net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                Component.literal("小脑榜"),
                net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                false,
                null
            );
        }
        ScoreAccess access = scoreboard.getOrCreatePlayerScore(
            net.minecraft.world.scores.ScoreHolder.forNameOnly(player.getScoreboardName()),
            objective
        );
        access.set(access.get() + 1); // 计分板权威：读当前分数 +1，惩罚扣分保留
        // 惩罚次数不动（计分板权威）
    }

    private static void executeCommand(MinecraftServer server, String command, String playerName) {
        if (server == null || command == null || command.trim().isEmpty()) return;
        String processed = command.replace("{player}", playerName);
        net.minecraft.commands.CommandSourceStack source = server.createCommandSourceStack()
            .withSuppressedOutput()
            .withPermission(4);
        server.getCommands().performPrefixedCommand(source, processed);
    }
}
