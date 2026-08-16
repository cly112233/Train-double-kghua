package com.kghua.npcai.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 投稿奖励存储。
 * 文件：npctalltome/contribution_rewards.json
 * - 奖励设置（每次投稿 + 每期前三名）
 * - 已结算期数（防止重复结算）
 * - 离线玩家待领奖励（上线时补发）
 */
public class ContributionRewardStorage {
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("contribution_rewards.json");

    private static ContributionRewardSettings settings = new ContributionRewardSettings();
    /** 已结算过的期数 */
    private static final Set<Integer> SETTLED_PERIODS = new HashSet<>();
    /** 离线玩家待领奖励：玩家UUID → 待领内容 */
    private static final Map<UUID, PendingReward> PENDING = new HashMap<>();
    private static boolean loaded = false;

    /** 离线待领奖励：4种卡数量 + 抽奖次数 */
    public static class PendingReward {
        public int[] cards = new int[4];
        public int lottery = 0;

        public PendingReward() {
        }

        public PendingReward(int[] cards, int lottery) {
            if (cards != null) {
                for (int i = 0; i < 4 && i < cards.length; i++) {
                    this.cards[i] = Math.max(0, cards[i]);
                }
            }
            this.lottery = Math.max(0, lottery);
        }

        public void merge(int[] cards, int lottery) {
            for (int i = 0; i < 4 && i < cards.length; i++) {
                this.cards[i] += Math.max(0, cards[i]);
            }
            this.lottery += Math.max(0, lottery);
        }
    }

    public static ContributionRewardSettings getSettings() {
        ensureLoaded();
        return settings;
    }

    public static void setSettings(ContributionRewardSettings s) {
        ensureLoaded();
        settings = s != null ? s : new ContributionRewardSettings();
        save();
    }

    public static void load() {
        loaded = true;
        SETTLED_PERIODS.clear();
        PENDING.clear();
        if (!Files.exists(FILE)) return;
        try {
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (root.has("settings")) {
                settings = ContributionRewardSettings.fromJson(root.getAsJsonObject("settings"));
            }
            if (root.has("settledPeriods")) {
                for (var el : root.getAsJsonArray("settledPeriods")) {
                    try {
                        SETTLED_PERIODS.add(el.getAsInt());
                    } catch (Exception ignored) {
                    }
                }
            }
            if (root.has("pending")) {
                JsonObject pendingObj = root.getAsJsonObject("pending");
                for (Map.Entry<String, com.google.gson.JsonElement> entry : pendingObj.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        JsonObject po = entry.getValue().getAsJsonObject();
                        int[] cards = new int[4];
                        if (po.has("cards")) {
                            JsonArray arr = po.getAsJsonArray("cards");
                            for (int i = 0; i < 4 && i < arr.size(); i++) {
                                cards[i] = Math.max(0, arr.get(i).getAsInt());
                            }
                        }
                        int lottery = po.has("lottery") ? po.get("lottery").getAsInt() : 0;
                        PENDING.put(uuid, new PendingReward(cards, lottery));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to load contribution rewards", e);
        }
    }

    private static void ensureLoaded() {
        if (!loaded) load();
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.add("settings", settings.toJson());
            JsonArray settledArr = new JsonArray();
            List<Integer> sorted = new ArrayList<>(SETTLED_PERIODS);
            sorted.sort(Integer::compareTo);
            for (int p : sorted) settledArr.add(p);
            root.add("settledPeriods", settledArr);
            JsonObject pendingObj = new JsonObject();
            for (Map.Entry<UUID, PendingReward> entry : PENDING.entrySet()) {
                JsonObject po = new JsonObject();
                JsonArray cardsArr = new JsonArray();
                for (int v : entry.getValue().cards) cardsArr.add(v);
                po.add("cards", cardsArr);
                po.addProperty("lottery", entry.getValue().lottery);
                pendingObj.add(entry.getKey().toString(), po);
            }
            root.add("pending", pendingObj);
            Files.writeString(FILE, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save contribution rewards", e);
        }
    }

    /** 标记某期已结算，返回是否标记成功（该期未结算过） */
    public static boolean markSettled(int period) {
        ensureLoaded();
        if (SETTLED_PERIODS.contains(period)) return false;
        SETTLED_PERIODS.add(period);
        save();
        return true;
    }

    public static boolean isSettled(int period) {
        ensureLoaded();
        return SETTLED_PERIODS.contains(period);
    }

    /** 累计离线待领奖励 */
    public static void addPending(UUID playerId, int[] cards, int lottery) {
        ensureLoaded();
        if (ContributionRewardSettings.isAllZero(cards, lottery)) return;
        PENDING.computeIfAbsent(playerId, k -> new PendingReward()).merge(cards, lottery);
        save();
    }

    /** 是否还有待领奖励 */
    public static boolean hasPending(UUID playerId) {
        ensureLoaded();
        return PENDING.containsKey(playerId);
    }

    /** 领取离线待领奖励，返回奖励摘要（无待领返回null） */
    public static PendingReward takePending(UUID playerId) {
        ensureLoaded();
        PendingReward reward = PENDING.remove(playerId);
        if (reward != null) save();
        return reward;
    }
}
