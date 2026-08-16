package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.data.CerebellumDetailStore;
import com.kghua.npcai.data.CerebellumStorage;
import com.kghua.npcai.service.CerebellumService;
import com.kghua.npcai.webbridge.CommandDispatcher;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * 小脑榜（网站 /admin 与首页）：
 * - cerebellum.board   小脑榜（计分板权威，惩罚降序→当前降序）+ 次数阈值
 * - cerebellum.details 明细最新 30 条（新→旧）
 * - cerebellum.export  导出 markdown（榜 + 明细，格式与游戏内导出一致）
 */
public class CerebellumHandler implements CommandDispatcher.Handler {
    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) {
        return switch (cmd) {
            case "cerebellum.details" -> details();
            case "cerebellum.export" -> CerebellumService.export(server);
            default -> board(server);
        };
    }

    private JsonObject board(MinecraftServer server) {
        JsonObject r = new JsonObject();
        r.addProperty("requiredDeaths", CerebellumStorage.getSettings().getRequiredDeaths());
        r.add("entries", CerebellumService.leaderboard(server));
        return r;
    }

    private JsonObject details() {
        List<JsonObject> all = CerebellumDetailStore.getAll();
        JsonArray arr = new JsonArray();
        for (int i = Math.max(0, all.size() - 30); i < all.size(); i++) {
            arr.add(all.get(i));
        }
        JsonArray newestFirst = new JsonArray();
        for (int i = arr.size() - 1; i >= 0; i--) {
            newestFirst.add(arr.get(i));
        }
        JsonObject r = new JsonObject();
        r.add("entries", newestFirst);
        return r;
    }
}
