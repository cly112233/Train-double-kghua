package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonObject;
import com.kghua.npcai.service.CatalogService;
import com.kghua.npcai.webbridge.CommandDispatcher;
import net.minecraft.server.MinecraftServer;

/**
 * 图鉴（网站 /roles）：catalog.all 全量角色/修饰符/道具（与游戏内图鉴同源）。
 * 后端缓存，每次 mod 连接 + 每日刷新。
 */
public class CatalogHandler implements CommandDispatcher.Handler {
    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) {
        // catalog.all：单一命令，无参数
        return CatalogService.all();
    }
}
