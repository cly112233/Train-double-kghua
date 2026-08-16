package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonObject;
import com.kghua.npcai.service.MailService;
import com.kghua.npcai.webbridge.CommandDispatcher;
import com.kghua.npcai.webbridge.HandlerUtil;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;

/**
 * 邮件命令（habitrain 邮箱互通）：
 * inbox/unreadCount（离线可读）/ claim/claimAll（必须在线）
 * publish（管理员，复刻游戏内 sendMailToPlayers）
 */
public class MailHandler implements CommandDispatcher.Handler {

    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception {
        String gameId = HandlerUtil.requireGameId(args);
        return switch (cmd) {
            case "mail.inbox" -> MailService.inbox(server, gameId);
            case "mail.unreadCount" -> MailService.unreadCount(server, gameId);
            case "mail.claim" -> MailService.claim(server, gameId,
                HandlerUtil.optString(args, "id", ""));
            case "mail.claimAll" -> MailService.claimAll(server, gameId);
            case "mail.publish" -> {
                HandlerUtil.requireAdmin(server, gameId);
                yield MailService.publish(server, gameId,
                    HandlerUtil.optString(args, "title", ""),
                    HandlerUtil.optString(args, "content", ""),
                    HandlerUtil.intArray(args, "cards"),
                    (int) HandlerUtil.optLong(args, "lotteryCount", 0),
                    HandlerUtil.optLong(args, "startAt", 0),
                    HandlerUtil.optLong(args, "endAt", 0),
                    (int) HandlerUtil.optLong(args, "sendMode", 0),
                    HandlerUtil.stringList(args, "playerNames"));
            }
            case "mail.bindQuestionnaire" -> {
                HandlerUtil.requireAdmin(server, gameId);
                yield MailService.bindQuestionnaire(server, gameId,
                    HandlerUtil.optString(args, "questionnaireId", ""),
                    HandlerUtil.optString(args, "title", ""),
                    HandlerUtil.optString(args, "content", ""),
                    HandlerUtil.intArray(args, "cards"),
                    (int) HandlerUtil.optLong(args, "lotteryCount", 0),
                    HandlerUtil.optLong(args, "endAt", 0));
            }
            default -> throw new WebException("E_UNSUPPORTED", "未支持的命令: " + cmd);
        };
    }
}
