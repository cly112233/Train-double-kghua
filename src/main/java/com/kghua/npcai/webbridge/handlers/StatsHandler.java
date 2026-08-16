package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonObject;
import com.kghua.npcai.service.PlayerDataService;
import com.kghua.npcai.service.StatsService;
import com.kghua.npcai.webbridge.CommandDispatcher;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;

/**
 * 战绩与个人数据：
 * - stats.get  战绩全字段（O 键同源，离线读盘）
 * - stats.mine 个人数据聚合（战绩+小脑+身份卡+金币+抽奖+皮肤）
 * - skins.mine 四类皮肤（玩家需在线，否则 E_OFFLINE）
 */
public class StatsHandler implements CommandDispatcher.Handler {
    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception {
        String gameId = requireGameId(args);
        return switch (cmd) {
            case "stats.mine" -> PlayerDataService.mine(server, gameId);
            case "skins.mine" -> skinsMine(server, gameId);
            default -> StatsService.getStats(server, gameId);
        };
    }

    private JsonObject skinsMine(MinecraftServer server, String gameId) throws WebException {
        JsonObject skins = PlayerDataService.skinsOnly(server, gameId);
        if (skins == null) {
            throw new WebException("E_OFFLINE", "该玩家不在线，无法读取皮肤");
        }
        JsonObject r = new JsonObject();
        r.add("skins", skins);
        return r;
    }

    private String requireGameId(JsonObject args) throws WebException {
        if (!args.has("gameId") || args.get("gameId").getAsString().isBlank()) {
            throw new WebException("E_VALIDATION", "缺少 gameId");
        }
        return args.get("gameId").getAsString();
    }
}
