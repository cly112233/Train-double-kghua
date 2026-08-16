package com.kghua.npcai.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.data.TitleStorage;
import com.kghua.npcai.network.SaveTitlePacket;
import com.kghua.npcai.network.SyncTitlePacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.UUID;

/**
 * 称号系统服务端逻辑。
 *
 * 称号以「玩家名字命名的队伍」形式存在：有称号 = 加入该队伍（前缀 = 被【】框住的称号）。
 * 铁律：只创建/修改/删除「队伍名 == 玩家当前游戏名」的队伍，绝不触碰其他队伍
 * （旧称号系统曾删除所有队伍导致全员称号消失，本实现从根上杜绝）。
 */
public class TitleManager {

    /** MC 原生 16 色（顺序即 UI 循环顺序） */
    public static final String[] COLOR_NAMES = {
        "black", "dark_blue", "dark_green", "dark_aqua",
        "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua",
        "red", "light_purple", "yellow", "white"
    };
    /** 对应中文名 */
    public static final String[] COLOR_CN = {
        "黑色", "深蓝", "深绿", "深青",
        "深红", "深紫", "金色", "灰色",
        "深灰", "蓝色", "绿色", "青色",
        "红色", "粉色", "黄色", "白色"
    };

    /** 称号最多字数（汉字=1字，两个字母/半角字符=1字）：最多 6 汉字或 12 字母 */
    private static final int MAX_TITLE_UNITS = 6;
    private static final int MAX_FRAGMENT_RAW = 200;
    private static final int MAX_FRAGMENT_ELEMS = 6;

    /** 颜色英文名 → ChatFormatting；非法返回 null */
    public static ChatFormatting colorByName(String name) {
        if (name == null || name.isEmpty()) return null;
        ChatFormatting cf = ChatFormatting.getByName(name);
        if (cf == null || !cf.isColor()) return null;
        return cf;
    }

    // ===== 每秒/上线兜底 =====

    /** 每秒：遍历在线玩家，有称号的确保队伍存在/前缀正确/颜色正确/已入队 */
    public static void ensureAllTitles(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ensurePlayerTitle(p);
        }
    }

    /** 玩家上线/每秒检查：把称号设置应用到队伍（幂等） */
    public static void ensurePlayerTitle(ServerPlayer player) {
        TitleStorage.TitleSetting s = TitleStorage.get(player.getUUID());
        if (s == null) return; // 无称号 = 绝不触碰任何队伍
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Scoreboard sb = server.getScoreboard();
        String name = player.getScoreboardName();

        // 改名检测：旧名下空队伍删除（所有权校验：必须是 appliedTeamName 才删）
        if (s.appliedTeamName != null && !s.appliedTeamName.equals(name)) {
            PlayerTeam old = sb.getPlayerTeam(s.appliedTeamName);
            if (old != null && old.getPlayers().isEmpty()) {
                sb.removePlayerTeam(old);
                NpcAiMod.LOGGER.info("称号：清理改名残留空队伍 {}", s.appliedTeamName);
            }
            s.appliedTeamName = null;
            TitleStorage.save();
        }

        // 建队（若已存在则复用，绝不复建）
        PlayerTeam team = sb.getPlayerTeam(name);
        if (team == null) {
            team = sb.addPlayerTeam(name);
            team.setDisplayName(Component.literal(name));
            s.appliedTeamName = name;
            TitleStorage.save();
        }

        // 前缀 / 名字颜色幂等刷新
        Component want = buildPrefix(s, server.registryAccess());
        if (!team.getPlayerPrefix().equals(want)) {
            team.setPlayerPrefix(want);
        }
        ChatFormatting cf = colorByName(s.nameColor);
        if (cf != null && team.getColor() != cf) {
            team.setColor(cf);
        }

        // 入队：只在「不在任何队伍」时加入；在别的队伍（游戏队）时跳过——绝不干预其他队伍
        PlayerTeam current = sb.getPlayersTeam(name);
        if (current == null) {
            try {
                sb.addPlayerToTeam(name, team);
            } catch (IllegalArgumentException e) {
                NpcAiMod.LOGGER.warn("称号：玩家 {} 加入队伍失败: {}", name, e.getMessage());
            }
        }
    }

    /** SERVER_STARTING：清理名下空队伍（离线改名残留/中途崩溃残留） */
    public static void cleanupOrphanTeams(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        for (TitleStorage.TitleSetting s : TitleStorage.all()) {
            if (s.appliedTeamName == null) continue;
            PlayerTeam t = sb.getPlayerTeam(s.appliedTeamName);
            // 只删「所有权凭证吻合 且 空」的队伍
            if (t != null && t.getPlayers().isEmpty()) {
                sb.removePlayerTeam(t);
                NpcAiMod.LOGGER.info("称号：启动清理空队伍 {}", s.appliedTeamName);
            }
            s.appliedTeamName = null;
        }
        TitleStorage.save();
    }

    // ===== 网络处理 =====

    /** RequestTitlePacket：回发当前完整状态 */
    public static void handleRequest(ServerPlayer player) {
        ServerPlayNetworking.send(player, buildSync(player));
    }

    /** SaveTitlePacket：校验 → 落盘 → 立即生效 → 回包 */
    public static void handleSave(ServerPlayer player, SaveTitlePacket payload) {
        String name = player.getScoreboardName();
        UUID uuid = player.getUUID();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Scoreboard sb = server.getScoreboard();

        int mode = payload.mode();
        if (mode != 0 && mode != 1) return; // 非法模式直接忽略

        String simple = payload.simpleTitle() == null ? "" : payload.simpleTitle().trim();
        String complex = payload.complexPrefixJson() == null ? "" : payload.complexPrefixJson().trim();
        boolean wantTitle = (mode == 0) ? !simple.isEmpty() : !complex.isEmpty();

        // —— 空 = 用户不想要称号 = 清除 ——
        if (!wantTitle) {
            clearTitle(player, sb, name, uuid, "§a已清除称号（不再加入队伍）");
            return;
        }

        // —— 校验并构建前缀 ——
        Component prefix;
        if (mode == 0) {
            if (countUnits(simple) > MAX_TITLE_UNITS) {
                player.sendSystemMessage(Component.literal("§c称号过长（最多6个汉字或12个字母，两个字母或字符算一个字）"));
                return;
            }
            ChatFormatting tc = colorByName(payload.titleColor());
            prefix = Component.literal("【" + simple + "】")
                .withStyle(tc != null ? tc : ChatFormatting.WHITE);
        } else {
            JsonArray arr;
            try {
                arr = parseFragment(complex);
            } catch (IllegalArgumentException e) {
                player.sendSystemMessage(Component.literal(e.getMessage()));
                return;
            }
            // 复杂模式字数：对每个 text 片段按同样规则求和（【】不计）
            int units = 0;
            for (JsonElement e : arr) {
                units += countUnits(e.getAsJsonObject().get("text").getAsString());
            }
            if (units > MAX_TITLE_UNITS) {
                player.sendSystemMessage(Component.literal("§c称号过长（最多6个汉字或12个字母，两个字母或字符算一个字，【】不计）"));
                return;
            }
            prefix = buildComplexPrefix(arr, payload.frameColor(), server.registryAccess());
        }

        // —— 落盘 + 立即生效（不等每秒兜底） ——
        TitleStorage.TitleSetting s = new TitleStorage.TitleSetting();
        s.mode = mode == 0 ? "simple" : "complex";
        s.simpleTitle = simple;
        s.titleColor = payload.titleColor() == null ? "" : payload.titleColor();
        s.complexPrefixJson = complex;
        s.frameColor = payload.frameColor() == null ? "" : payload.frameColor();
        s.nameColor = payload.nameColor() == null ? "" : payload.nameColor();
        s.appliedTeamName = null;
        TitleStorage.put(uuid, s);

        applyNow(player, sb, name, s, prefix);

        ServerPlayNetworking.send(player, buildSync(player));
        player.sendSystemMessage(Component.literal("§a称号已生效！"));
    }

    // ===== 内部工具 =====

    /** 清除称号：移出自己队伍 → 删自己名下空队伍 → 移除存储 */
    private static void clearTitle(ServerPlayer player, Scoreboard sb, String name, UUID uuid, String msg) {
        PlayerTeam team = sb.getPlayerTeam(name);
        if (team != null) {
            PlayerTeam cur = sb.getPlayersTeam(name);
            if (cur == team) {
                sb.removePlayerFromTeam(name, team);
            }
            // 只删「自己名下」且已空的队伍（所有权双重校验）
            TitleStorage.TitleSetting old = TitleStorage.get(uuid);
            if (team.getPlayers().isEmpty()
                && old != null && team.getName().equals(old.appliedTeamName)) {
                sb.removePlayerTeam(team);
            }
        }
        TitleStorage.remove(uuid);
        ServerPlayNetworking.send(player, buildSync(player));
        player.sendSystemMessage(Component.literal(msg));
    }

    /** 立即应用（确认/清除时直接调，不等每秒 tick） */
    private static void applyNow(ServerPlayer player, Scoreboard sb, String name,
                                 TitleStorage.TitleSetting s, Component prefix) {
        PlayerTeam team = sb.getPlayerTeam(name);
        if (team == null) {
            team = sb.addPlayerTeam(name);
            team.setDisplayName(Component.literal(name));
        }
        team.setPlayerPrefix(prefix);
        ChatFormatting cf = colorByName(s.nameColor);
        if (cf != null) {
            team.setColor(cf);
        }
        s.appliedTeamName = name;
        TitleStorage.save();

        PlayerTeam current = sb.getPlayersTeam(name);
        if (current == null) {
            try {
                sb.addPlayerToTeam(name, team);
            } catch (IllegalArgumentException e) {
                NpcAiMod.LOGGER.warn("称号：玩家 {} 立即加入队伍失败: {}", name, e.getMessage());
            }
        }
    }

    /** 构建 SyncTitlePacket：存储状态（预填编辑框）+ 实际队伍 prefix JSON（展示） */
    private static SyncTitlePacket buildSync(ServerPlayer player) {
        UUID uuid = player.getUUID();
        MinecraftServer server = player.getServer();
        String prefixJson = "";
        String nameColorName = "";
        if (server != null) {
            PlayerTeam team = server.getScoreboard().getPlayerTeam(player.getScoreboardName());
            if (team != null) {
                try {
                    prefixJson = Component.Serializer.toJson(team.getPlayerPrefix(), server.registryAccess());
                } catch (Exception ignored) {}
                ChatFormatting c = team.getColor();
                if (c != null && c.isColor()) {
                    nameColorName = c.getName();
                }
            }
        }
        TitleStorage.TitleSetting s = TitleStorage.get(uuid);
        return new SyncTitlePacket(
            s == null ? 0 : (s.isComplex() ? 1 : 0),
            s == null ? "" : s.simpleTitle,
            s == null ? "" : s.titleColor,
            s == null ? "" : s.complexPrefixJson,
            s == null ? "" : s.frameColor,
            s == null ? "" : s.nameColor,
            prefixJson,
            nameColorName
        );
    }

    /** 根据存储设置构建期望前缀 */
    private static Component buildPrefix(TitleStorage.TitleSetting s, HolderLookup.Provider provider) {
        if (s.isComplex()) {
            try {
                JsonArray arr = parseFragment(s.complexPrefixJson);
                return buildComplexPrefix(arr, s.frameColor, provider);
            } catch (IllegalArgumentException e) {
                return Component.literal("【】");
            }
        }
        ChatFormatting tc = colorByName(s.titleColor);
        return Component.literal("【" + s.simpleTitle + "】")
            .withStyle(tc != null ? tc : ChatFormatting.WHITE);
    }

    /**
     * 复杂模式 JSON 校验：允许 [ ... ] 数组或裸对象列表（需求示例是裸对象列表，不带方括号）。
     * 只放行 text/color 字段，防 translate/clickEvent 等注入。
     */
    private static JsonArray parseFragment(String raw) {
        String t = raw.trim();
        if (t.length() > MAX_FRAGMENT_RAW) {
            throw new IllegalArgumentException("§cJSON过长（最多" + MAX_FRAGMENT_RAW + "字符）");
        }
        String wrapped = t.startsWith("[") ? t : "[" + t + "]";
        JsonElement el;
        try {
            el = JsonParser.parseString(wrapped);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("§cJSON格式错误");
        }
        if (!el.isJsonArray()) {
            throw new IllegalArgumentException("§cJSON必须是对象数组");
        }
        JsonArray arr = el.getAsJsonArray();
        if (arr.isEmpty() || arr.size() > MAX_FRAGMENT_ELEMS) {
            throw new IllegalArgumentException("§c元素数量需在1-" + MAX_FRAGMENT_ELEMS + "之间");
        }
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("§c每个元素必须是JSON对象");
            }
            JsonObject o = e.getAsJsonObject();
            if (!o.has("text") || !o.get("text").isJsonPrimitive()
                || !o.get("text").getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("§c每个元素必须含 text 字符串");
            }
            for (String key : o.keySet()) {
                if (!key.equals("text") && !key.equals("color")) {
                    throw new IllegalArgumentException("§c只允许 text 和 color 字段");
                }
            }
        }
        return arr;
    }

    /** 拼装复杂前缀：[{"text":"【","color":frame},...用户...,{"text":"】","color":frame}] */
    private static Component buildComplexPrefix(JsonArray user, String frameColor, HolderLookup.Provider provider) {
        JsonArray full = new JsonArray();
        JsonObject open = new JsonObject();
        open.addProperty("text", "【");
        if (frameColor != null && !frameColor.isEmpty()) open.addProperty("color", frameColor);
        full.add(open);
        for (JsonElement e : user) full.add(e);
        JsonObject close = new JsonObject();
        close.addProperty("text", "】");
        if (frameColor != null && !frameColor.isEmpty()) close.addProperty("color", frameColor);
        full.add(close);
        try {
            return Component.Serializer.fromJson(full.toString(), provider);
        } catch (Exception e) {
            return Component.literal("【】");
        }
    }

    /**
     * 字数计算：汉字（非 ASCII）= 1 字；字母/数字/半角字符两个算 1 字（向上取整）。
     * 例：帅哥=2字，abc=2字（3÷2=1.5→2），abcde=3字（5÷2=2.5→3）。
     */
    public static int countUnits(String s) {
        if (s == null || s.isEmpty()) return 0;
        int full = 0, half = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 0x80) half++;
            else full++;
        }
        return full + (half + 1) / 2;
    }
}
