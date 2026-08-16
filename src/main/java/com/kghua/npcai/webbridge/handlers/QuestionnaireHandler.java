package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonObject;
import com.kghua.npcai.service.QuestionnaireService;
import com.kghua.npcai.webbridge.CommandDispatcher;
import com.kghua.npcai.webbridge.HandlerUtil;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;

/**
 * 问卷命令：
 * list/get/submit（玩家，get 管理员视角附全部回答）
 * create/delete/export（管理员）
 */
public class QuestionnaireHandler implements CommandDispatcher.Handler {

    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception {
        String gameId = HandlerUtil.requireGameId(args);
        return switch (cmd) {
            case "questionnaire.list" -> QuestionnaireService.list(server, gameId);
            case "questionnaire.get" -> QuestionnaireService.get(server, gameId,
                HandlerUtil.requireUuid(args, "id"), HandlerUtil.isAdmin(gameId));
            case "questionnaire.submit" -> QuestionnaireService.submit(server, gameId,
                HandlerUtil.requireUuid(args, "id"), HandlerUtil.stringList(args, "answers"));
            case "questionnaire.create" -> {
                HandlerUtil.requireAdmin(server, gameId);
                yield QuestionnaireService.create(server,
                    HandlerUtil.optString(args, "title", ""),
                    HandlerUtil.stringList(args, "questions"),
                    HandlerUtil.stringList(args, "hints"),
                    HandlerUtil.optLong(args, "startAt", System.currentTimeMillis()),
                    HandlerUtil.optLong(args, "endAt", 0));
            }
            case "questionnaire.delete" -> {
                HandlerUtil.requireAdmin(server, gameId);
                yield QuestionnaireService.delete(server, HandlerUtil.requireUuid(args, "id"));
            }
            case "questionnaire.export" -> {
                HandlerUtil.requireAdmin(server, gameId);
                yield QuestionnaireService.export(server, HandlerUtil.requireUuid(args, "id"));
            }
            default -> throw new WebException("E_UNSUPPORTED", "未支持的命令: " + cmd);
        };
    }
}
