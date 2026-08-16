package com.kghua.npcai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.habitrain.lottery.mail.LocalMailboxStore;
import com.habitrain.lottery.mail.LocalMailboxStore.MailJson;
import com.habitrain.lottery.storage.WorldLotteryPaths;
import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.data.MailBindingStorage;
import com.kghua.npcai.data.MailRecord;
import com.kghua.npcai.data.MailStorage;
import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.data.QuestionnaireStorage;
import com.kghua.npcai.mailbridge.MailBridge;
import com.kghua.npcai.webbridge.OfflineUuid;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 邮件服务（网站 mail.* 与 habitrain 邮箱互通）：
 * - inbox/unreadCount 走 LocalMailboxStore.load（无副作用，绝不走 MailService.list —— 它会把未读置为已读）
 * - claim/claimAll 玩家必须在线（habitrain 领取机制在线执行）
 * - publish 复刻 NpcAiMod.sendMailToPlayers：投递在线目标 + 落 MailRecord 发布记录
 */
public final class MailService {

    private MailService() {}

    /** 收件箱（离线可读）：{mails:[{id,sender,title,content,read,claimed,sentAt,expiresAt,expired}]} */
    public static JsonObject inbox(MinecraftServer server, String gameId) {
        UUID uuid = OfflineUuid.of(gameId);
        JsonArray arr = new JsonArray();
        try {
            if (WorldLotteryPaths.ready()) {
                long now = System.currentTimeMillis();
                List<MailJson> mails = LocalMailboxStore.load(uuid);
                for (int i = mails.size() - 1; i >= 0; i--) { // 新→旧（文件按投递顺序追加）
                    MailJson m = mails.get(i);
                    JsonObject o = new JsonObject();
                    o.addProperty("id", m.id);
                    o.addProperty("sender", m.sender);
                    o.addProperty("title", m.title);
                    o.addProperty("content", m.content);
                    o.addProperty("read", m.read);
                    o.addProperty("claimed", m.claimed);
                    o.addProperty("sentAt", m.sentAt);
                    o.addProperty("expiresAt", m.expiresAt);
                    o.addProperty("expired", m.expiresAt > 0 && now > m.expiresAt);
                    arr.add(o);
                }
            }
        } catch (Throwable e) {
            NpcAiMod.LOGGER.warn("MailService: failed to load inbox for {}", gameId, e);
        }
        JsonObject r = new JsonObject();
        r.add("mails", arr);
        return r;
    }

    /** 未读红点数（离线可读，与 MailBridge.getUnreadCount 同口径） */
    public static JsonObject unreadCount(MinecraftServer server, String gameId) {
        JsonObject r = new JsonObject();
        int count = 0;
        ServerPlayer online = server.getPlayerList().getPlayerByName(gameId);
        if (online != null) {
            count = MailBridge.getUnreadCount(online);
        } else {
            try {
                if (WorldLotteryPaths.ready()) {
                    long now = System.currentTimeMillis();
                    for (MailJson m : LocalMailboxStore.load(OfflineUuid.of(gameId))) {
                        if (m.claimed || m.read) continue;
                        if (m.expiresAt > 0 && now > m.expiresAt) continue;
                        count++;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        r.addProperty("count", count);
        return r;
    }

    /** 领取单封邮件（玩家必须在线，habitrain 领取机制在线执行） */
    public static JsonObject claim(MinecraftServer server, String gameId, String mailId) throws WebException {
        ServerPlayer player = server.getPlayerList().getPlayerByName(gameId);
        if (player == null) {
            throw new WebException("E_OFFLINE", "领取需要登录游戏");
        }
        boolean ok = false;
        try {
            ok = com.habitrain.lottery.mail.MailService.claim(player, mailId);
        } catch (Throwable e) {
            NpcAiMod.LOGGER.error("MailService: claim failed for {}", gameId, e);
        }
        JsonObject r = new JsonObject();
        r.addProperty("claimed", ok);
        return r;
    }

    /** 一键领取（玩家必须在线） */
    public static JsonObject claimAll(MinecraftServer server, String gameId) throws WebException {
        ServerPlayer player = server.getPlayerList().getPlayerByName(gameId);
        if (player == null) {
            throw new WebException("E_OFFLINE", "领取需要登录游戏");
        }
        int count = 0;
        try {
            count = com.habitrain.lottery.mail.MailService.claimAll(player);
        } catch (Throwable e) {
            NpcAiMod.LOGGER.error("MailService: claimAll failed for {}", gameId, e);
        }
        JsonObject r = new JsonObject();
        r.addProperty("claimed", count);
        return r;
    }

    /**
     * 管理员发布邮件（复刻 sendMailToPlayers）：
     * sendMode 0=全部在线 1=白名单 2=黑名单；奖励在投递时立即发放（MailBridge 同构），
     * 发布记录落 MailStorage 供管理端列表展示。
     */
    public static JsonObject publish(MinecraftServer server, String adminName, String title, String content,
            int[] cards, int lotteryCount, long startAt, long expiresAt, int sendMode, List<String> playerNames)
        throws WebException {
        if (sendMode == 3) {
            throw new WebException("E_VALIDATION", "绑定问卷模式不会直接发送邮件");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new WebException("E_VALIDATION", "邮件标题不能为空");
        }
        long sentAt = System.currentTimeMillis();

        List<ServerPlayer> targets = new ArrayList<>();
        switch (sendMode) {
            case 0 -> targets.addAll(server.getPlayerList().getPlayers());
            case 1 -> {
                for (String name : playerNames) {
                    ServerPlayer p = server.getPlayerList().getPlayerByName(name);
                    if (p != null) targets.add(p);
                }
            }
            case 2 -> {
                Set<String> blacklist = new HashSet<>(playerNames);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (!blacklist.contains(p.getName().getString())) {
                        targets.add(p);
                    }
                }
            }
            default -> throw new WebException("E_VALIDATION", "发送模式无效");
        }

        int count = 0;
        MailRecord record = new MailRecord(UUID.randomUUID());
        record.setTitle(title.trim());
        record.setContent(content == null ? "" : content);
        record.setCards(cards == null ? new int[4] : cards);
        record.setLotteryCount(Math.max(0, lotteryCount));
        record.setSendMode(sendMode);
        record.setPlayerNames(playerNames == null ? new ArrayList<>() : playerNames);
        record.setStartAt(startAt);
        record.setEndAt(expiresAt);
        record.setSentAt(sentAt);

        for (ServerPlayer target : targets) {
            MailBridge.sendMail(target, adminName, title.trim(), content == null ? "" : content,
                expiresAt, record.getCards(), record.getLotteryCount());
            record.addDeliveredPlayer(target.getName().getString());
            count++;
        }
        MailStorage.save(record);

        JsonObject r = new JsonObject();
        r.addProperty("sent", count);
        return r;
    }

    /**
     * 绑定问卷邮件（发送模式3，与游戏内 BindMailQuestionnairePacket 逻辑一致）：
     * questionnaireId 为空 = 解除绑定；非空 = 校验问卷存在后写入模板快照，
     * 换绑其他问卷时清空已发记录（每个玩家只发一次）。玩家首次提交问卷后自动发送。
     */
    public static JsonObject bindQuestionnaire(MinecraftServer server, String adminName,
            String questionnaireId, String title, String content, int[] cards,
            int lotteryCount, long endAt) throws WebException {
        MailBindingStorage.Binding binding = MailBindingStorage.get();
        if (questionnaireId == null || questionnaireId.trim().isEmpty()) {
            binding.clear();
            MailBindingStorage.save();
            JsonObject r = new JsonObject();
            r.addProperty("bound", false);
            return r;
        }
        UUID qid;
        try {
            qid = UUID.fromString(questionnaireId.trim());
        } catch (IllegalArgumentException e) {
            throw new WebException("E_VALIDATION", "问卷ID无效");
        }
        Questionnaire q = QuestionnaireStorage.get(qid);
        if (q == null) {
            throw new WebException("E_NOT_FOUND", "问卷不存在");
        }
        if (!binding.questionnaireId.equals(qid.toString())) {
            binding.mailedPlayers.clear(); // 换绑其他问卷：清空已发记录
        }
        binding.questionnaireId = qid.toString();
        binding.title = title == null ? "" : title.trim();
        binding.content = content == null ? "" : content;
        binding.cards = cards == null ? new int[4] : cards;
        binding.lotteryCount = Math.max(0, lotteryCount);
        binding.endAt = endAt;
        MailBindingStorage.save();
        JsonObject r = new JsonObject();
        r.addProperty("bound", true);
        r.addProperty("questionnaireId", qid.toString());
        r.addProperty("questionnaireTitle", q.getTitle());
        return r;
    }
}
