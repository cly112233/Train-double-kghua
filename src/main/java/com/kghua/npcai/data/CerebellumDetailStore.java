package com.kghua.npcai.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 小脑明细记录（供网站小脑榜"最新30条"与管理端导出使用）。
 * 存 npctalltome/cerebellum_details.json，保留最近 {@value #MAX_ENTRIES} 条。
 * 明细只增不减、只读不写回（写回仅追加），与计分板/惩罚次数无关。
 */
public class CerebellumDetailStore {
    public static final int MAX_ENTRIES = 200;

    /** 明细方式（与计分板 +1 同位记录，见 DeathEventHandler） */
    public static final String KIND_CIVILIAN_SELF_KILL = "CIVILIAN_SELF_KILL";       // 错杀好人被 shot_innocent 处死（小脑者=死者）
    public static final String KIND_KILLER_TEAMKILL = "KILLER_TEAMKILL";             // 杀手互杀（小脑者=击杀者）
    public static final String KIND_KILLER_TEAMKILL_GRENADE = "KILLER_TEAMKILL_GRENADE"; // 杀手手雷互杀

    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("cerebellum_details.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<JsonObject> entries = null;
    private static int nextSeq = 1;

    private CerebellumDetailStore() {}

    /** 追加一条明细：小脑玩家 player + 方式 kind + 被小脑玩家 victimName（可为空） */
    public static synchronized void add(UUID playerUuid, String playerName, String kind, String victimName) {
        ensureLoaded();
        JsonObject e = new JsonObject();
        e.addProperty("seq", nextSeq++);
        e.addProperty("at", System.currentTimeMillis());
        e.addProperty("playerUuid", playerUuid.toString());
        e.addProperty("playerName", playerName);
        e.addProperty("kind", kind);
        e.addProperty("victimName", victimName == null ? "" : victimName);
        entries.add(e);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        save();
    }

    /** 全部明细（旧→新），不可变拷贝 */
    public static synchronized List<JsonObject> getAll() {
        ensureLoaded();
        List<JsonObject> copy = new ArrayList<>(entries.size());
        for (JsonObject e : entries) {
            copy.add(e.deepCopy());
        }
        return Collections.unmodifiableList(copy);
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("nextSeq", nextSeq);
            JsonArray arr = new JsonArray();
            for (JsonObject e : entries) {
                arr.add(e);
            }
            root.add("entries", arr);
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save cerebellum details", e);
        }
    }

    private static void ensureLoaded() {
        if (entries != null) return;
        entries = new ArrayList<>();
        try {
            if (Files.exists(FILE)) {
                JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                nextSeq = root.has("nextSeq") ? root.get("nextSeq").getAsInt() : 1;
                if (root.has("entries")) {
                    for (JsonElement el : root.getAsJsonArray("entries")) {
                        if (el.isJsonObject()) entries.add(el.getAsJsonObject());
                    }
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to load cerebellum details", e);
        }
    }
}
