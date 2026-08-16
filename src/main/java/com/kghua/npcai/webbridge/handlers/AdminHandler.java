package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.data.NpcAdminStorage;
import com.kghua.npcai.data.PlayerMapGroupStorage;
import com.kghua.npcai.webbridge.CommandDispatcher;
import com.kghua.npcai.webbridge.HandlerUtil;
import com.kghua.npcai.webbridge.OfflineUuid;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * admin.check：按游戏ID（离线 UUID）校验是否有 NPC 管理权限
 * admin.playerList：在线玩家列表（字段与游戏内管理端 syncPlayerList 一致）
 */
public class AdminHandler implements CommandDispatcher.Handler {
    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception {
        String gameId = HandlerUtil.requireGameId(args);
        return switch (cmd) {
            case "admin.check" -> {
                JsonObject r = new JsonObject();
                r.addProperty("gameId", gameId);
                r.addProperty("isAdmin", NpcAdminStorage.isAdmin(OfflineUuid.of(gameId)));
                yield r;
            }
            case "admin.playerList" -> {
                HandlerUtil.requireAdmin(server, gameId);
                yield playerList(server);
            }
            default -> throw new WebException("E_UNSUPPORTED", "未支持的命令: " + cmd);
        };
    }

    /** 与游戏内管理端 syncPlayerList 同字段：uuid/名字/OP/队伍名/队伍色/玩家色/地图组/管理员 */
    private static JsonObject playerList(MinecraftServer server) {
        JsonArray arr = new JsonArray();
        var scoreboard = server.getScoreboard();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", p.getUUID().toString());
            o.addProperty("name", p.getName().getString());
            o.addProperty("op", p.hasPermissions(2));
            PlayerTeam team = scoreboard.getPlayersTeam(p.getScoreboardName());
            String teamName = "";
            String teamColor = "";
            String playerColor = "";
            if (team != null) {
                teamName = team.getDisplayName().getString();
                TextColor tc = team.getDisplayName().getStyle().getColor();
                if (tc != null) {
                    teamColor = String.format("#%06X", tc.getValue() & 0xFFFFFF);
                }
                if (team.getColor() != ChatFormatting.RESET && team.getColor().getColor() != null) {
                    playerColor = formatTeamColor(team.getColor());
                }
            }
            o.addProperty("teamName", teamName);
            o.addProperty("teamColor", teamColor);
            o.addProperty("playerColor", playerColor);
            o.addProperty("mapGroup", PlayerMapGroupStorage.isMember(p.getUUID()));
            o.addProperty("npcAdmin", NpcAdminStorage.isAdmin(p.getUUID()));
            arr.add(o);
        }
        JsonObject r = new JsonObject();
        r.add("players", arr);
        return r;
    }

    private static String formatTeamColor(ChatFormatting formatting) {
        Integer c = formatting.getColor();
        return c == null ? "" : String.format("#%06X", c & 0xFFFFFF);
    }
}
