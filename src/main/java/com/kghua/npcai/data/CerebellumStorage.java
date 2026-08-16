package com.kghua.npcai.data;

import com.google.gson.*;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 小脑设置与玩家计数存储。
 */
public class CerebellumStorage {
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("cerebellum.json");
    private static CerebellumSettings settings = new CerebellumSettings();
    private static final Map<UUID, Integer> DEATH_COUNTS = new HashMap<>();
    private static final Map<UUID, Integer> PUNISHMENT_COUNTS = new HashMap<>();
    /** 已达标待执行的惩罚次数：玩家下次成为杀手阵营角色时消耗 1 次（可叠加） */
    private static final Map<UUID, Integer> PENDING_PUNISHMENTS = new HashMap<>();
    private static boolean loaded = false;

    public static CerebellumSettings getSettings() {
        ensureLoaded();
        return settings;
    }

    public static void setSettings(CerebellumSettings s) {
        ensureLoaded();
        settings = s;
        save();
    }

    public static int getDeathCount(UUID player) {
        ensureLoaded();
        return DEATH_COUNTS.getOrDefault(player, 0);
    }

    public static void incrementDeathCount(UUID player) {
        ensureLoaded();
        DEATH_COUNTS.put(player, getDeathCount(player) + 1);
        save();
    }

    public static int getPunishmentCount(UUID player) {
        ensureLoaded();
        return PUNISHMENT_COUNTS.getOrDefault(player, 0);
    }

    public static Map<UUID, Integer> getAllPunishmentCounts() {
        ensureLoaded();
        return new HashMap<>(PUNISHMENT_COUNTS);
    }

    public static Map<UUID, Integer> getAllCounts() {
        ensureLoaded();
        return new HashMap<>(DEATH_COUNTS);
    }

    /**
     * 游戏结束结算：扣小脑次数 required、累计惩罚次数 +1、待执行次数 +1。
     * 待执行惩罚不在下局立即执行，而是等玩家下次成为杀手阵营角色时消耗。
     */
    public static void settlePenalty(UUID player, int required) {
        ensureLoaded();
        int current = DEATH_COUNTS.getOrDefault(player, 0);
        DEATH_COUNTS.put(player, Math.max(0, current - required));
        PUNISHMENT_COUNTS.put(player, PUNISHMENT_COUNTS.getOrDefault(player, 0) + 1);
        PENDING_PUNISHMENTS.put(player, PENDING_PUNISHMENTS.getOrDefault(player, 0) + 1);
        save();
    }

    /** 待执行惩罚次数 */
    public static int getPendingCount(UUID player) {
        ensureLoaded();
        return PENDING_PUNISHMENTS.getOrDefault(player, 0);
    }

    /** 消耗一次待执行惩罚（归零时移除条目） */
    public static void consumePending(UUID player) {
        ensureLoaded();
        int c = PENDING_PUNISHMENTS.getOrDefault(player, 0);
        if (c > 1) {
            PENDING_PUNISHMENTS.put(player, c - 1);
        } else {
            PENDING_PUNISHMENTS.remove(player);
        }
        save();
    }

    public static Map<UUID, Integer> getAllPendingCounts() {
        ensureLoaded();
        return new HashMap<>(PENDING_PUNISHMENTS);
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.add("settings", settings.toJson());
            JsonObject counts = new JsonObject();
            for (Map.Entry<UUID, Integer> e : DEATH_COUNTS.entrySet()) {
                counts.addProperty(e.getKey().toString(), e.getValue());
            }
            root.add("counts", counts);
            JsonObject punishmentCounts = new JsonObject();
            for (Map.Entry<UUID, Integer> e : PUNISHMENT_COUNTS.entrySet()) {
                punishmentCounts.addProperty(e.getKey().toString(), e.getValue());
            }
            root.add("punishmentCounts", punishmentCounts);
            JsonObject pendingPunishments = new JsonObject();
            for (Map.Entry<UUID, Integer> e : PENDING_PUNISHMENTS.entrySet()) {
                pendingPunishments.addProperty(e.getKey().toString(), e.getValue());
            }
            root.add("pendingPunishments", pendingPunishments);
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save cerebellum settings", e);
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        load();
        loaded = true;
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) {
                String text = Files.readString(FILE);
                JsonObject root = JsonParser.parseString(text).getAsJsonObject();
                if (root.has("settings")) {
                    settings = CerebellumSettings.fromJson(root.getAsJsonObject("settings"));
                }
                if (root.has("counts")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("counts").entrySet()) {
                        DEATH_COUNTS.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                    }
                }
                if (root.has("punishmentCounts")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("punishmentCounts").entrySet()) {
                        PUNISHMENT_COUNTS.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                    }
                }
                if (root.has("pendingPunishments")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("pendingPunishments").entrySet()) {
                        PENDING_PUNISHMENTS.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                    }
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to load cerebellum settings", e);
        }
    }
}
