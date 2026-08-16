package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonObject;
import com.kghua.npcai.service.ContributionService;
import com.kghua.npcai.webbridge.CommandDispatcher;
import com.kghua.npcai.webbridge.HandlerUtil;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;

/**
 * 投稿命令：
 * list/mine/submit/like（玩家；like 每日上限禁自赞）
 * approve/pending/export（管理员；approve 通过=奖励邮件+改期数，驳回=删除+驳回邮件）
 */
public class ContributionHandler implements CommandDispatcher.Handler {

    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception {
        String gameId = HandlerUtil.requireGameId(args);
        return switch (cmd) {
            case "contrib.list" -> ContributionService.list(server, gameId);
            case "contrib.mine" -> ContributionService.mine(server, gameId);
            case "contrib.submit" -> ContributionService.submit(server, gameId,
                HandlerUtil.optString(args, "type", ""),
                HandlerUtil.optString(args, "title", ""),
                HandlerUtil.optString(args, "shortDesc", ""),
                HandlerUtil.optString(args, "description", ""),
                HandlerUtil.optString(args, "shop", ""),
                HandlerUtil.optString(args, "background", ""),
                HandlerUtil.optString(args, "faction", ""));
            case "contrib.like" -> ContributionService.like(server, gameId,
                HandlerUtil.requireUuid(args, "contributionId"));
            case "contrib.approve" -> ContributionService.approve(server, gameId,
                HandlerUtil.requireUuid(args, "contributionId"),
                HandlerUtil.optBoolean(args, "approved", false));
            case "contrib.pending" -> ContributionService.pending(server, gameId);
            case "contrib.export" -> ContributionService.export(server, gameId,
                HandlerUtil.requireUuid(args, "contributionId"));
            default -> throw new WebException("E_UNSUPPORTED", "未支持的命令: " + cmd);
        };
    }
}
