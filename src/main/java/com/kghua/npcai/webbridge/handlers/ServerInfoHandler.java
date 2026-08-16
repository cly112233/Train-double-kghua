package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.webbridge.CommandDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** server.info：在线数/上限/玩家名单/MOTD */
public class ServerInfoHandler implements CommandDispatcher.Handler {
    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) {
        JsonObject r = new JsonObject();
        r.addProperty("online", server.getPlayerCount());
        r.addProperty("maxPlayers", server.getMaxPlayers());
        r.addProperty("motd", server.getMotd());
        JsonArray players = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            players.add(p.getGameProfile().getName());
        }
        r.add("players", players);
        return r;
    }
}
