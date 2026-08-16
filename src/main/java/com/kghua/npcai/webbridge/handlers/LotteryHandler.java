package com.kghua.npcai.webbridge.handlers;

import com.google.gson.JsonObject;
import com.kghua.npcai.webbridge.CommandDispatcher;
import com.kghua.npcai.webbridge.HandlerUtil;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;

/**
 * 抽奖命令（与游戏内同源）：
 * state（卡池+经济，离线可读）/ roll（单抽十连，必须在线）/ exchange（160金=1次，必须在线）
 * history（habitrain 抽奖记录，离线可读）
 *
 * 2026-08-16：抽奖系统已断开（官方皮肤系统不对外开放，自建前不接）。
 * 命令仍可识别，统一返回 E_DISABLED 占位；后续自建皮肤系统时改回实现即可。
 * （LotteryService.java 保留为死代码，作为自建实现的参考骨架。）
 */
public class LotteryHandler implements CommandDispatcher.Handler {

    @Override
    public JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception {
        HandlerUtil.requireGameId(args);
        switch (cmd) {
            case "lottery.state":
            case "lottery.roll":
            case "lottery.exchange":
            case "lottery.history":
                throw new WebException("E_DISABLED", "抽奖系统建设中，敬请期待");
            default:
                throw new WebException("E_UNSUPPORTED", "未支持的命令: " + cmd);
        }
    }
}
