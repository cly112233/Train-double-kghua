package com.kghua.npcai.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.webbridge.OfflineUuid;
import io.wifi.starrailexpress.stats.PlayerStats;
import io.wifi.starrailexpress.stats.PlayerStatsManager;
import io.wifi.starrailexpress.util.PlayerStatsSerializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 战绩读取（网站 stats.get/stats.mine 与游戏 O 键同源）：
 * - 在线玩家走 PlayerStatsManager（O 键同源内存态）；
 * - 离线玩家直接读 config/play_stats/&lt;uuid&gt;.json（SRE 退服时落盘）。
 *
 * 注意：PlayerStatsManager.get(UUID) 对未知 UUID 会 computeIfAbsent 创建空对象
 *       并污染内存表 —— 离线玩家绝不能走该路径，必须读盘。
 */
public final class StatsService {
    private StatsService() {}

    /** 战绩全字段（JSON 键名与 play_stats 文件完全一致，含 totalPlayTime=ticks） */
    public static JsonObject getStats(MinecraftServer server, String gameId) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(gameId);
        if (player != null) {
            try {
                PlayerStats stats = PlayerStatsManager.get(player);
                return JsonParser.parseString(PlayerStatsSerializer.toJson(stats)).getAsJsonObject();
            } catch (Exception e) {
                NpcAiMod.LOGGER.error("StatsService: failed to read online stats for {}", gameId, e);
                return emptyStats();
            }
        }
        UUID uuid = OfflineUuid.of(gameId);
        Path file = FabricLoader.getInstance().getConfigDir().resolve("play_stats").resolve(uuid + ".json");
        if (Files.exists(file)) {
            try {
                return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            } catch (Exception e) {
                NpcAiMod.LOGGER.error("StatsService: failed to parse offline stats for {}", gameId, e);
            }
        }
        return emptyStats();
    }

    /** 从未打过游戏的玩家：全零战绩（与 SRE PlayerStatsData 默认字段一致） */
    private static JsonObject emptyStats() {
        JsonObject o = new JsonObject();
        o.addProperty("totalPlayTime", 0L);
        String[] ints = {
            "totalGamesPlayed", "totalKills", "totalDeaths", "totalWins", "totalLosses",
            "totalTeamKills", "totalLoversWins",
            "totalCivilianGames", "totalCivilianWins", "totalCivilianKills", "totalCivilianDeaths",
            "totalKillerGames", "totalKillerWins", "totalKillerKills", "totalKillerDeaths",
            "totalNeutralGames", "totalNeutralWins", "totalNeutralKills", "totalNeutralDeaths",
            "totalSheriffGames", "totalSheriffWins", "totalSheriffKills", "totalSheriffDeaths"
        };
        for (String k : ints) {
            o.addProperty(k, 0);
        }
        o.add("roleStats", new JsonObject());
        return o;
    }
}
