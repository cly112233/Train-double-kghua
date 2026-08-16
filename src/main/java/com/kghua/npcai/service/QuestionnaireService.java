package com.kghua.npcai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.data.MailBindingStorage;
import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.data.QuestionnaireStorage;
import com.kghua.npcai.mailbridge.MailBridge;
import com.kghua.npcai.player.PlayerPendingTracker;
import com.kghua.npcai.webbridge.OfflineUuid;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 问卷服务（网站 questionnaire.* 与游戏内问卷互通）：
 * 存储走 QuestionnaireStorage，提交/绑定邮件逻辑与游戏内 SubmitQuestionnaireResponsePacket 完全一致。
 */
public final class QuestionnaireService {

    private QuestionnaireService() {}

    /** 玩家可见问卷列表（隐藏已填写的，与 RequestQuestionnairesPacket 同构） */
    public static JsonObject list(MinecraftServer server, String gameId) {
        UUID uuid = OfflineUuid.of(gameId);
        List<Questionnaire> all = QuestionnaireStorage.filterVisibleForPlayer(
            QuestionnaireStorage.loadAll(), uuid);
        JsonArray arr = new JsonArray();
        for (Questionnaire q : all) {
            arr.add(summaryJson(q, gameId));
        }
        JsonObject r = new JsonObject();
        r.add("questionnaires", arr);
        return r;
    }

    /** 问卷详情（玩家视角不含他人回答；admin 视角含全部回答） */
    public static JsonObject get(MinecraftServer server, String gameId, UUID qid, boolean admin)
        throws WebException {
        Questionnaire q = QuestionnaireStorage.get(qid);
        if (q == null) {
            throw new WebException("E_NOT_FOUND", "问卷不存在");
        }
        JsonObject o = summaryJson(q, gameId);
        o.add("questions", stringArray(q.getQuestions()));
        o.add("hints", stringArray(q.getHints()));
        if (admin) {
            JsonArray rs = new JsonArray();
            for (Questionnaire.Response resp : q.getResponses()) {
                rs.add(resp.toJson());
            }
            o.add("responses", rs);
        }
        return o;
    }

    /** 玩家提交问卷（含绑定问卷自动发邮件，与 SubmitQuestionnaireResponsePacket 同构） */
    public static JsonObject submit(MinecraftServer server, String gameId, UUID qid, List<String> answers)
        throws WebException {
        Questionnaire q = QuestionnaireStorage.get(qid);
        if (q == null) {
            throw new WebException("E_NOT_FOUND", "问卷不存在");
        }
        if (q.hasResponded(gameId)) {
            throw new WebException("E_VALIDATION", "你已经填写过这份问卷了");
        }
        QuestionnaireStorage.addResponse(qid, gameId, answers);
        PlayerPendingTracker.clearQuestionnairePending(OfflineUuid.of(gameId), qid);

        // 问卷绑定邮箱：绑定后玩家首次提交问卷，自动把绑定模板邮件发送到提交玩家邮箱
        boolean mailSent = false;
        try {
            MailBindingStorage.Binding binding = MailBindingStorage.get();
            if (binding.questionnaireId.equals(q.getId().toString())) {
                if (!binding.mailedPlayers.contains(gameId)) {
                    ServerPlayer online = server.getPlayerList().getPlayerByName(gameId);
                    if (online != null) {
                        MailBridge.sendMail(online, "系统", binding.title, binding.content,
                            binding.endAt, binding.cards, binding.lotteryCount);
                    }
                    binding.mailedPlayers.add(gameId);
                    MailBindingStorage.save();
                    mailSent = true;
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to send bound questionnaire mail", e);
        }
        JsonObject r = new JsonObject();
        r.addProperty("submitted", true);
        r.addProperty("mailSent", mailSent);
        return r;
    }

    /** 管理员创建问卷（与 CreateQuestionnairePacket 同构） */
    public static JsonObject create(MinecraftServer server, String title, List<String> questions,
            List<String> hints, long startAt, long endAt) throws WebException {
        if (title == null || title.trim().isEmpty()) {
            throw new WebException("E_VALIDATION", "问卷标题不能为空");
        }
        Questionnaire q = new Questionnaire(UUID.randomUUID());
        q.setTitle(title.trim());
        q.setQuestions(questions == null ? new ArrayList<>() : questions);
        q.setHints(hints == null ? new ArrayList<>() : hints);
        q.setStartAt(startAt);
        q.setEndAt(endAt);
        q.setCreatedAt(System.currentTimeMillis());
        QuestionnaireStorage.save(q);
        JsonObject r = new JsonObject();
        r.addProperty("created", true);
        r.addProperty("id", q.getId().toString());
        return r;
    }

    /** 管理员删除问卷（与 DeleteQuestionnairePacket 同构） */
    public static JsonObject delete(MinecraftServer server, UUID qid) {
        QuestionnaireStorage.delete(qid);
        JsonObject r = new JsonObject();
        r.addProperty("deleted", true);
        return r;
    }

    /** 管理员导出问卷（与 ExportQuestionnairePacket 同构：发回 markdown） */
    public static JsonObject export(MinecraftServer server, UUID qid) throws WebException {
        Questionnaire q = QuestionnaireStorage.get(qid);
        if (q == null) {
            throw new WebException("E_NOT_FOUND", "问卷不存在");
        }
        String[] result = QuestionnaireStorage.exportToMarkdownText(q);
        if (result == null) {
            throw new WebException("E_INTERNAL", "问卷导出失败");
        }
        JsonObject r = new JsonObject();
        r.addProperty("fileName", result[0]);
        r.addProperty("content", result[1]);
        return r;
    }

    // ---------- 工具 ----------

    private static JsonObject summaryJson(Questionnaire q, String viewerName) {
        JsonObject o = new JsonObject();
        o.addProperty("id", q.getId().toString());
        o.addProperty("title", q.getTitle());
        o.addProperty("startAt", q.getStartAt());
        o.addProperty("endAt", q.getEndAt());
        o.addProperty("createdAt", q.getCreatedAt());
        o.addProperty("active", q.isActive());
        o.addProperty("responded", viewerName != null && q.hasResponded(viewerName));
        return o;
    }

    private static JsonArray stringArray(List<String> list) {
        JsonArray arr = new JsonArray();
        for (String s : list) arr.add(s);
        return arr;
    }
}
