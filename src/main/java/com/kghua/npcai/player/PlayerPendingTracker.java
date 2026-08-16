package com.kghua.npcai.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.data.ContributionStorage;
import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.data.QuestionnaireStorage;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnTeammateKilledTeammate;
import com.kghua.npcai.mailbridge.MailBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪「红点玩家」：有未读邮件、小脑惩罚、未填反馈/问卷的玩家。
 */
public class PlayerPendingTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("player_pending.json");

    //  persisted flags（以字符串存储 UUID，避免 Gson 序列化问题）
    private static final Set<String> FEEDBACK_PENDING = ConcurrentHashMap.newKeySet();

    //  in-memory counters for the current game
    private static final Set<UUID> XIAONAO_PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Deque<Long>> XIAONAO_RECORDS = new ConcurrentHashMap<>();

    private static boolean loaded = false;

    public static void registerEvents() {
        OnTeammateKilledTeammate.EVENT.register((victim, killer, isInnocent, deathReason) -> {
            if (!isInnocent) return;
            // 六十秒模式已随新版基座移除（旧版在此判断不记小脑，此处不再需要）

            long now = System.currentTimeMillis();
            UUID uuid = killer.getUUID();
            SREConfig config = SREConfig.instance();
            long windowMs = Math.max(1, config.teamKillViolationWindowSeconds) * 1000L;

            Deque<Long> records = XIAONAO_RECORDS.computeIfAbsent(uuid, k -> new ArrayDeque<>());
            records.addLast(now);
            while (!records.isEmpty() && records.peekFirst() < now - windowMs) {
                records.pollFirst();
            }

            if (records.size() >= config.teamKillViolationThreshold) {
                XIAONAO_PENDING.add(uuid);
                records.clear();
                NpcAiMod.LOGGER.info("NPC AI: 玩家 {} 达到小脑惩罚阈值，标记为红点玩家", killer.getName().getString());
            }
        });

        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            XIAONAO_PENDING.clear();
            XIAONAO_RECORDS.clear();
            NpcAiMod.LOGGER.info("NPC AI: 游戏结束，清空小脑惩罚标记");
        });
    }

    public static boolean hasPending(ServerPlayer player) {
        if (MailBridge.getUnreadCount(player) > 0) return true;
        UUID uuid = player.getUUID();
        if (XIAONAO_PENDING.contains(uuid)) return true;
        if (FEEDBACK_PENDING.contains(uuid.toString())) return true;
        if (hasQuestionnairePending(player)) return true;
        if (hasLikePending(player)) return true;
        return false;
    }

    /**
     * 投稿点赞红点：今日还有点赞次数没点完，且当期作品还点得动。
     * 若剩余次数 > 当期可点作品数（作品点完了次数还有剩，无法再点），不算红点。
     */
    public static boolean hasLikePending(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int remaining = ContributionStorage.getRemainingLikes(uuid);
        if (remaining <= 0) return false;
        int likeableLeft = ContributionStorage.getLikeableLeft(uuid);
        return likeableLeft > 0 && remaining <= likeableLeft;
    }

    private static boolean hasQuestionnairePending(ServerPlayer player) {
        String name = player.getName().getString();
        for (Questionnaire q : QuestionnaireStorage.loadAll()) {
            if (q.isActive() && !q.hasResponded(name)) {
                return true;
            }
        }
        return false;
    }

    public static void clearQuestionnairePending(UUID uuid, UUID questionnaireId) {
        // 问卷是否待填由 hasQuestionnairePending 实时计算，无需额外状态
    }

    public static void markFeedbackPending(UUID uuid) {
        loadIfNeeded();
        if (FEEDBACK_PENDING.add(uuid.toString())) {
            save();
        }
    }

    public static void clearFeedbackPending(UUID uuid) {
        loadIfNeeded();
        if (FEEDBACK_PENDING.remove(uuid.toString())) {
            save();
        }
    }

    public static void clearXiaoNaoPending(UUID uuid) {
        XIAONAO_PENDING.remove(uuid);
        XIAONAO_RECORDS.remove(uuid);
    }

    public static Set<UUID> getPendingUuids() {
        loadIfNeeded();
        Set<UUID> result = new HashSet<>(XIAONAO_PENDING);
        for (String s : FEEDBACK_PENDING) {
            try {
                result.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    public static String getPendingReason(ServerPlayer player) {
        List<String> reasons = new ArrayList<>();
        if (MailBridge.getUnreadCount(player) > 0) reasons.add("未读邮件");
        if (XIAONAO_PENDING.contains(player.getUUID())) reasons.add("小脑惩罚");
        if (FEEDBACK_PENDING.contains(player.getUUID().toString())) reasons.add("待填反馈");
        if (hasQuestionnairePending(player)) reasons.add("未填问卷");
        if (hasLikePending(player)) reasons.add("投稿点赞");
        return String.join("、", reasons);
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                loaded = true;
                return;
            }
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loadedSet = GSON.fromJson(json, type);
            FEEDBACK_PENDING.clear();
            if (loadedSet != null) FEEDBACK_PENDING.addAll(loadedSet);
            loaded = true;
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to load player pending flags", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(FEEDBACK_PENDING), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save player pending flags", e);
        }
    }

    private static void loadIfNeeded() {
        if (!loaded) {
            load();
        }
    }
}
