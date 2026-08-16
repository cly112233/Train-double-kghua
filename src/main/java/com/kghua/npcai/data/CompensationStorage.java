package com.kghua.npcai.data;

import com.google.gson.*;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 补偿机制规则存储。
 */
public class CompensationStorage {
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("compensation_rules.json");
    private static final List<CompensationRule> CACHE = new ArrayList<>();
    private static final Map<UUID, Integer> DEATH_COUNTS = new HashMap<>();
    private static boolean loaded = false;

    public static List<CompensationRule> getAll() {
        ensureLoaded();
        return new ArrayList<>(CACHE);
    }

    public static void setAll(List<CompensationRule> rules) {
        ensureLoaded();
        CACHE.clear();
        CACHE.addAll(rules);
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

    public static void resetDeathCount(UUID player) {
        ensureLoaded();
        DEATH_COUNTS.remove(player);
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonArray arr = new JsonArray();
            for (CompensationRule rule : CACHE) {
                arr.add(rule.toJson());
            }
            JsonObject counts = new JsonObject();
            for (Map.Entry<UUID, Integer> e : DEATH_COUNTS.entrySet()) {
                counts.addProperty(e.getKey().toString(), e.getValue());
            }
            JsonObject root = new JsonObject();
            root.add("rules", arr);
            root.add("counts", counts);
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save compensation rules", e);
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        load();
        loaded = true;
    }

    private static void load() {
        CACHE.clear();
        DEATH_COUNTS.clear();
        try {
            if (Files.exists(FILE)) {
                String text = Files.readString(FILE);
                JsonObject root = JsonParser.parseString(text).getAsJsonObject();
                if (root.has("rules")) {
                    for (JsonElement e : root.getAsJsonArray("rules")) {
                        CACHE.add(CompensationRule.fromJson(e.getAsJsonObject()));
                    }
                }
                if (root.has("counts")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("counts").entrySet()) {
                        DEATH_COUNTS.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                    }
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to load compensation rules", e);
        }
    }
}
