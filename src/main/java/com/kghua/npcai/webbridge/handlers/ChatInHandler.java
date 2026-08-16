package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonObject;
import com.kghua.npcai.webbridge.CommandDispatcher;
import com.kghua.npcai.webbridge.WebBridge;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** chat.webIn：网站发言 → 以绑定游戏ID身份在游戏内 say（玩家必须在线） */
public class ChatInHandler implements CommandDispatcher.Handler {
    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception {
        if (!args.has("gameId") || !args.has("content")) {
            throw new WebException("E_VALIDATION", "缺少 gameId/content");
        }
        String gameId = args.get("gameId").getAsString();
        // 只允许单行纯文本，防注入换行指令
        String content = args.get("content").getAsString()
            .replaceAll("[\\r\\n]", " ")
            .trim();
        if (content.isEmpty()) {
            throw new WebException("E_VALIDATION", "消息不能为空");
        }
        if (content.length() > 200) {
            throw new WebException("E_VALIDATION", "消息不能超过 200 字");
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(gameId);
        if (player == null) {
            throw new WebException("E_OFFLINE", "该玩家不在线，无法发言");
        }

        // 以玩家身份执行 say（withSuppressedOutput + permission(4) 现成模式，展示为真实玩家聊天）
        WebBridge.suppressNextChatEcho();
        try {
            CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            server.getCommands().performPrefixedCommand(source, "say " + content);
        } finally {
            WebBridge.clearChatEchoSuppression();
        }

        JsonObject r = new JsonObject();
        r.addProperty("sent", true);
        return r;
    }
}
