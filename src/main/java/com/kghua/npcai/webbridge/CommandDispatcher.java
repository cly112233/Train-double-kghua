package com.kghua.npcai.webbridge;

import com.google.gson.JsonObject;
import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.webbridge.handlers.AdminHandler;
import com.kghua.npcai.webbridge.handlers.CatalogHandler;
import com.kghua.npcai.webbridge.handlers.CerebellumHandler;
import com.kghua.npcai.webbridge.handlers.ChatInHandler;
import com.kghua.npcai.webbridge.handlers.ContributionHandler;
import com.kghua.npcai.webbridge.handlers.FeedbackHandler;
import com.kghua.npcai.webbridge.handlers.LotteryHandler;
import com.kghua.npcai.webbridge.handlers.MailHandler;
import com.kghua.npcai.webbridge.handlers.QuestionnaireHandler;
import com.kghua.npcai.webbridge.handlers.ServerInfoHandler;
import com.kghua.npcai.webbridge.handlers.StatsHandler;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令分发：cmd（小写点分层）→ Handler 注册表。
 * 收到的 req 一定在主线程 dispatch；异常统一包装成 resp{ok:false,error{code,msg}}。
 */
public final class CommandDispatcher {
    public interface Handler {
        JsonObject handle(MinecraftServer server, String cmd, JsonObject args) throws Exception;
    }

    private static final Map<String, Handler> HANDLERS = new HashMap<>();

    static {
        register("chat.webIn", new ChatInHandler());
        AdminHandler admin = new AdminHandler();
        register("admin.check", admin);
        register("admin.playerList", admin);
        register("server.info", new ServerInfoHandler());
        StatsHandler stats = new StatsHandler();
        register("stats.get", stats);
        register("stats.mine", stats);
        register("skins.mine", stats);
        CerebellumHandler cerebellum = new CerebellumHandler();
        register("cerebellum.board", cerebellum);
        register("cerebellum.details", cerebellum);
        register("cerebellum.export", cerebellum);
        CatalogHandler catalog = new CatalogHandler();
        register("catalog.all", catalog);
        ContributionHandler contrib = new ContributionHandler();
        register("contrib.list", contrib);
        register("contrib.mine", contrib);
        register("contrib.submit", contrib);
        register("contrib.like", contrib);
        register("contrib.approve", contrib);
        register("contrib.pending", contrib);
        register("contrib.export", contrib);
        QuestionnaireHandler questionnaire = new QuestionnaireHandler();
        register("questionnaire.list", questionnaire);
        register("questionnaire.get", questionnaire);
        register("questionnaire.submit", questionnaire);
        register("questionnaire.create", questionnaire);
        register("questionnaire.delete", questionnaire);
        register("questionnaire.export", questionnaire);
        FeedbackHandler feedback = new FeedbackHandler();
        register("feedback.submit", feedback);
        register("feedback.list", feedback);
        register("feedback.export", feedback);
        MailHandler mail = new MailHandler();
        register("mail.inbox", mail);
        register("mail.unreadCount", mail);
        register("mail.claim", mail);
        register("mail.claimAll", mail);
        register("mail.publish", mail);
        LotteryHandler lottery = new LotteryHandler();
        register("lottery.state", lottery);
        register("lottery.roll", lottery);
        register("lottery.exchange", lottery);
        register("lottery.history", lottery);
    }

    public static void register(String cmd, Handler handler) {
        HANDLERS.put(cmd, handler);
    }

    public static void dispatch(MinecraftServer server, JsonObject req) {
        String reqId = req.has("reqId") ? req.get("reqId").getAsString() : "";
        String cmd = req.has("cmd") ? req.get("cmd").getAsString() : "";
        JsonObject args = req.has("args") && req.get("args").isJsonObject()
            ? req.getAsJsonObject("args") : new JsonObject();

        Handler handler = HANDLERS.get(cmd);
        if (handler == null) {
            resp(reqId, false, null, "E_UNSUPPORTED", "未支持的命令: " + cmd);
            return;
        }
        try {
            JsonObject data = handler.handle(server, cmd, args);
            resp(reqId, true, data, null, null);
        } catch (WebException e) {
            resp(reqId, false, null, e.code, e.getMessage());
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("[webbridge] 命令 {} 处理失败", cmd, e);
            resp(reqId, false, null, "E_INTERNAL", "服务器内部错误");
        }
    }

    private static void resp(String reqId, boolean ok, JsonObject data, String errCode, String errMsg) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "resp");
        out.addProperty("reqId", reqId);
        out.addProperty("ok", ok);
        if (ok) {
            out.add("data", data == null ? new JsonObject() : data);
        } else {
            JsonObject error = new JsonObject();
            error.addProperty("code", errCode);
            error.addProperty("msg", errMsg);
            out.add("error", error);
        }
        WebBridge.send(out);
    }
}
