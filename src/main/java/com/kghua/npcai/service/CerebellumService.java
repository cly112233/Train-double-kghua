package com.kghua.npcai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.data.CerebellumDetailStore;
import com.kghua.npcai.data.CerebellumStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.ReadOnlyScoreInfo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小脑服务（网站 cerebellum.board/details/export 与游戏内导出同源）：
 * 榜以计分板为权威（kgxnbang=当前次数, kgxnbang_punish=惩罚次数），
 * 排序规则：惩罚降序 → 当前降序；明细来自 CerebellumDetailStore。
 */
public final class CerebellumService {
    public static final String OBJ_CURRENT = "kgxnbang";
    public static final String OBJ_PUNISH = "kgxnbang_punish";

    private CerebellumService() {}

    /** 小脑榜：JSON 数组，已按 惩罚降序 → 当前降序 排好 */
    public static JsonArray leaderboard(MinecraftServer server) {
        JsonArray arr = new JsonArray();
        for (CerebellumEntry e : entries(server)) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name);
            o.addProperty("uuid", e.uuid.toString());
            o.addProperty("current", e.current);
            o.addProperty("punish", e.punish);
            o.addProperty("pending", e.pending);
            arr.add(o);
        }
        return arr;
    }

    /** 导出 markdown：{boardMd: 榜表格, detailsMd: 明细表格}（格式与游戏内导出一致） */
    public static JsonObject export(MinecraftServer server) {
        JsonObject r = new JsonObject();
        r.addProperty("boardMd", exportBoardMarkdown(server));
        r.addProperty("detailsMd", exportDetailsMarkdown());
        return r;
    }

    /** 明细导出：序号/时间/小脑玩家/方式/被小脑玩家，新→旧 */
    public static String exportDetailsMarkdown() {
        List<JsonObject> details = CerebellumDetailStore.getAll();
        StringBuilder sb = new StringBuilder();
        sb.append("# 小脑明细\n\n");
        sb.append("| 序号 | 时间 | 小脑玩家 | 方式 | 被小脑玩家 |\n");
        sb.append("|---|---|---|---|---|\n");
        for (int i = details.size() - 1; i >= 0; i--) {
            JsonObject d = details.get(i);
            sb.append("| ").append(d.get("seq").getAsLong())
                .append(" | ").append(java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(d.get("at").getAsLong()),
                    java.time.ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append(" | ").append(d.get("playerName").getAsString())
                .append(" | ").append(kindName(d.get("kind").getAsString()))
                .append(" | ").append(d.get("victimName").getAsString())
                .append(" |\n");
        }
        return sb.toString();
    }

    public static String kindName(String kind) {
        return switch (kind) {
            case CerebellumDetailStore.KIND_CIVILIAN_SELF_KILL -> "错杀好人";
            case CerebellumDetailStore.KIND_KILLER_TEAMKILL -> "杀手互杀";
            case CerebellumDetailStore.KIND_KILLER_TEAMKILL_GRENADE -> "杀手手雷互杀";
            default -> kind;
        };
    }

    private static String exportBoardMarkdown(MinecraftServer server) {
        int requiredDeaths = CerebellumStorage.getSettings().getRequiredDeaths();
        StringBuilder sb = new StringBuilder();
        sb.append("# 小脑榜\n\n");
        sb.append("| 序号 | 玩家 | 小脑次数(").append(requiredDeaths).append("次) | 惩罚次数 | 待执行 |\n");
        sb.append("|---|---|---|---|---|\n");
        int idx = 1;
        for (CerebellumEntry e : entries(server)) {
            sb.append("| ").append(idx++).append(" | ").append(e.name)
                .append(" | ").append(e.current)
                .append(" | ").append(e.punish)
                .append(" | ").append(e.pending).append(" |\n");
        }
        return sb.toString();
    }

    /** 与 NpcAiMod.buildCerebellumLeaderboard 同构（计分板权威 + 排序） */
    private static List<CerebellumEntry> entries(MinecraftServer server) {
        List<CerebellumEntry> result = new ArrayList<>();
        try {
            var scoreboard = server.getScoreboard();
            var currentObj = scoreboard.getObjective(OBJ_CURRENT);
            var punishObj = scoreboard.getObjective(OBJ_PUNISH);

            // 玩家名 → UUID（在线优先，其次档案缓存）
            Map<String, UUID> uuidByName = new HashMap<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                uuidByName.put(player.getName().getString(), player.getUUID());
            }

            Map<String, Integer> currentScores = new HashMap<>();
            Map<String, Integer> punishScores = new HashMap<>();
            for (var holder : scoreboard.getTrackedPlayers()) {
                String holderName = holder.getScoreboardName();
                if (holderName == null || holderName.isEmpty()) continue;
                try {
                    if (currentObj != null) {
                        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, currentObj);
                        if (info != null && info.value() != 0) currentScores.put(holderName, info.value());
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (punishObj != null) {
                        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, punishObj);
                        if (info != null && info.value() != 0) punishScores.put(holderName, info.value());
                    }
                } catch (Exception ignored) {
                }
            }

            Set<String> allNames = new HashSet<>();
            allNames.addAll(currentScores.keySet());
            allNames.addAll(punishScores.keySet());

            for (String name : allNames) {
                UUID uuid = uuidByName.get(name);
                if (uuid == null) {
                    try {
                        var profileOpt = server.getProfileCache().get(name);
                        if (profileOpt.isPresent()) uuid = profileOpt.get().getId();
                    } catch (Exception ignored) {
                    }
                }
                if (uuid == null) {
                    uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
                }
                result.add(new CerebellumEntry(name, uuid,
                    currentScores.getOrDefault(name, 0),
                    punishScores.getOrDefault(name, 0),
                    CerebellumStorage.getPendingCount(uuid)));
            }
            result.sort((a, b) -> {
                int cmp = Integer.compare(b.punish, a.punish);
                if (cmp != 0) return cmp;
                return Integer.compare(b.current, a.current);
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return result;
    }

    private record CerebellumEntry(String name, UUID uuid, int current, int punish, int pending) {}
}
