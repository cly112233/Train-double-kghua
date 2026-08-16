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
import java.util.Comparator;
import java.util.List;

/**
 * 全局传送点存储（所有 NPC 共享，互通可见）。
 * 文件：npctalltome/teleport_points.json
 * <p>
 * 2026-08-07 起从「每 NPC 独立存储」迁移为「全局共享」：
 * - 首次加载时自动把历史 NPC 数据（npctalltome/npc_data/*.nbt）中的传送点合并进来（按名称去重），
 *   之后全局文件为唯一数据源（migrated 标记防止重复合并导致已删除的点复活）。
 */
public class TeleportPointStorage {
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("teleport_points.json");

    private static final List<TeleportPoint> POINTS = new ArrayList<>();
    private static boolean loaded = false;
    private static boolean migrated = false;

    /** 全部传送点（按修改时间倒序，最新的在最前面） */
    public static List<TeleportPoint> getPoints() {
        ensureLoaded();
        return POINTS.stream()
            .sorted(Comparator.comparingLong(TeleportPoint::updatedAt).reversed())
            .toList();
    }

    /** 同名传送点直接替换，避免重复 */
    public static void addPoint(TeleportPoint point) {
        ensureLoaded();
        POINTS.removeIf(p -> p.name().equals(point.name()));
        POINTS.add(point);
        save();
    }

    public static void removePoint(String name) {
        ensureLoaded();
        POINTS.removeIf(p -> p.name().equals(name));
        save();
    }

    public static void load() {
        loaded = true;
        POINTS.clear();
        if (!Files.exists(FILE)) {
            migrateFromNpcData();
            return;
        }
        try {
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            migrated = root.has("migrated") && root.get("migrated").getAsBoolean();
            if (root.has("points")) {
                JsonArray arr = root.getAsJsonArray("points");
                for (var el : arr) {
                    try {
                        POINTS.add(TeleportPoint.fromJson(el.getAsJsonObject()));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to load teleport points", e);
            migrateFromNpcData();
        }
    }

    /** 一次性迁移：把历史 NPC 数据里的传送点合并进全局存储（按名称去重） */
    private static void migrateFromNpcData() {
        if (migrated) return;
        try {
            for (NpcData data : NpcDataManager.loadAll()) {
                for (TeleportPoint point : data.getTeleportPoints()) {
                    if (point.name() == null || point.name().isEmpty()) continue;
                    POINTS.removeIf(p -> p.name().equals(point.name()));
                    POINTS.add(point);
                }
            }
            migrated = true;
            save();
            NpcAiMod.LOGGER.info("Teleport points migrated to global storage: {}", POINTS.size());
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to migrate teleport points", e);
        }
    }

    private static void ensureLoaded() {
        if (!loaded) load();
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("migrated", migrated);
            JsonArray arr = new JsonArray();
            for (TeleportPoint p : POINTS) arr.add(p.toJson());
            root.add("points", arr);
            Files.writeString(FILE, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save teleport points", e);
        }
    }
}
