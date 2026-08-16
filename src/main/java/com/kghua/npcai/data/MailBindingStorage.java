package com.kghua.npcai.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 问卷绑定邮箱的持久化存储。
 * 管理端在邮箱设置里把某个问卷绑定到一封邮件模板后，
 * 玩家第一次提交该问卷时，系统会自动把这封邮件发送到提交玩家的邮箱。
 */
public class MailBindingStorage {
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("mail_binding.json");

    /** 当前绑定配置（questionnaireId 为空表示未绑定） */
    private static Binding current = new Binding();

    public static class Binding {
        /** 绑定的问卷ID（"" = 未绑定） */
        public String questionnaireId = "";
        /** 自动发送的邮件模板（绑定瞬间从管理端编辑区快照） */
        public String title = "";
        public String content = "";
        public int[] cards = new int[4]; // 4种身份卡数量（杀手/平民/独赢中立/杀手中立）
        public int lotteryCount = 0;
        public long endAt = 0;
        /** 已自动发送过的玩家名（每个玩家只发一次） */
        public final Set<String> mailedPlayers = new HashSet<>();

        public void clear() {
            questionnaireId = "";
            title = "";
            content = "";
            cards = new int[4];
            lotteryCount = 0;
            endAt = 0;
            mailedPlayers.clear();
        }
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            Binding b = new Binding();
            b.questionnaireId = obj.has("questionnaireId") ? obj.get("questionnaireId").getAsString() : "";
            b.title = obj.has("title") ? obj.get("title").getAsString() : "";
            b.content = obj.has("content") ? obj.get("content").getAsString() : "";
            if (obj.has("cards")) {
                int i = 0;
                for (var e : obj.getAsJsonArray("cards")) {
                    if (i < 4) b.cards[i++] = e.getAsInt();
                }
            }
            b.lotteryCount = obj.has("lotteryCount") ? obj.get("lotteryCount").getAsInt() : 0;
            b.endAt = obj.has("endAt") ? obj.get("endAt").getAsLong() : 0;
            if (obj.has("mailedPlayers")) {
                for (var e : obj.getAsJsonArray("mailedPlayers")) b.mailedPlayers.add(e.getAsString());
            }
            current = b;
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to load mail binding", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Binding b = current;
            JsonObject obj = new JsonObject();
            obj.addProperty("questionnaireId", b.questionnaireId);
            obj.addProperty("title", b.title);
            obj.addProperty("content", b.content);
            JsonArray cardsJson = new JsonArray();
            for (int c : b.cards) cardsJson.add(c);
            obj.add("cards", cardsJson);
            obj.addProperty("lotteryCount", b.lotteryCount);
            obj.addProperty("endAt", b.endAt);
            JsonArray mailed = new JsonArray();
            for (String n : b.mailedPlayers) mailed.add(n);
            obj.add("mailedPlayers", mailed);
            Files.writeString(FILE, obj.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save mail binding", e);
        }
    }

    public static Binding get() {
        return current;
    }
}
