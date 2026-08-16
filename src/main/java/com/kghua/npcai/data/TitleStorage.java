package com.kghua.npcai.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家称号设置持久化存储（以玩家UUID为键）。
 * 存储的是「期望的称号配置」；实际生效状态以计分板队伍为准。
 * 铁律：只创建/修改/删除「队伍名==玩家当前游戏名」的队伍，绝不触碰其他队伍。
 */
public class TitleStorage {
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("player_titles.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 单个玩家的称号设置 */
    public static class TitleSetting {
        /** 模式：simple / complex */
        public String mode = "simple";
        /** 简单模式：称号名（无 = ""） */
        public String simpleTitle = "";
        /** 简单模式：称号颜色（ChatFormatting 名，如 dark_purple） */
        public String titleColor = "";
        /** 复杂模式：玩家输入的 prefix JSON 片段原文（trim 后） */
        public String complexPrefixJson = "";
        /** 复杂模式：【】框颜色 */
        public String frameColor = "";
        /** 两模式共用：玩家名字颜色 */
        public String nameColor = "";
        /** 当前生效队伍名（=应用时的玩家游戏名；改名后由 tick 检测更新）——删除路径的所有权凭证 */
        public String appliedTeamName = null;

        public boolean isComplex() {
            return "complex".equals(mode);
        }
    }

    private static Map<UUID, TitleSetting> titles = new HashMap<>();
    private static boolean dirty = false;

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                titles = new HashMap<>();
                return;
            }
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            Map<UUID, TitleSetting> loaded = GSON.fromJson(text, new TypeToken<Map<UUID, TitleSetting>>(){}.getType());
            titles = loaded != null ? new HashMap<>(loaded) : new HashMap<>();
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to load player title storage", e);
            titles = new HashMap<>();
        }
    }

    public static void save() {
        if (!dirty) return;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(titles), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save player title storage", e);
        }
    }

    public static TitleSetting get(UUID playerId) {
        return titles.get(playerId);
    }

    public static boolean hasTitle(UUID playerId) {
        TitleSetting s = titles.get(playerId);
        if (s == null) return false;
        if (s.isComplex()) return !s.complexPrefixJson.isEmpty();
        return !s.simpleTitle.isEmpty();
    }

    /** 设置称号（调用方先置好 appliedTeamName），变更立即落盘 */
    public static void put(UUID playerId, TitleSetting setting) {
        titles.put(playerId, setting);
        dirty = true;
        save();
    }

    /** 清除称号，变更立即落盘 */
    public static void remove(UUID playerId) {
        if (titles.remove(playerId) != null) {
            dirty = true;
            save();
        }
    }

    /** 全部设置（SERVER_STARTING 清理空队伍用） */
    public static Collection<TitleSetting> all() {
        return titles.values();
    }
}
