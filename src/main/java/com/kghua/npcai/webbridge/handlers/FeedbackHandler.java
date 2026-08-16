package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonObject;
import com.kghua.npcai.service.FeedbackService;
import com.kghua.npcai.webbridge.CommandDispatcher;
import com.kghua.npcai.webbridge.HandlerUtil;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;

/** 反馈命令：feedback.submit（玩家）/ feedback.list|export（管理员） */
public class FeedbackHandler implements CommandDispatcher.Handler {

    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception {
        String gameId = HandlerUtil.requireGameId(args);
        return switch (cmd) {
            case "feedback.submit" -> FeedbackService.submit(server, gameId,
                HandlerUtil.optBoolean(args, "anonymous", false),
                HandlerUtil.optString(args, "content", ""));
            case "feedback.list" -> {
                HandlerUtil.requireAdmin(server, gameId);
                yield FeedbackService.list(HandlerUtil.optLong(args, "startAt", 0),
                    HandlerUtil.optLong(args, "endAt", Long.MAX_VALUE));
            }
            case "feedback.export" -> {
                HandlerUtil.requireAdmin(server, gameId);
                yield FeedbackService.export(HandlerUtil.stringList(args, "fileNames"));
            }
            default -> throw new WebException("E_UNSUPPORTED", "未支持的命令: " + cmd);
        };
    }
}
