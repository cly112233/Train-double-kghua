package com.kghua.npcai.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 管理员已发布邮件的记录（用于管理端列表展示）。
 */
public class MailRecord {
    private final UUID id;
    private String title = "";
    private String content = "";
    private int[] cards = new int[4]; // 4种身份卡数量（杀手/平民/独赢中立/杀手中立）
    private int lotteryCount = 0;
    private int sendMode; // 0=全部, 1=白名单, 2=黑名单
    private List<String> playerNames = new ArrayList<>();
    private long startAt;
    private long endAt;
    private long sentAt;
    private final Set<String> deliveredPlayers = new HashSet<>();

    public MailRecord(UUID id) {
        this.id = id;
    }

    public UUID getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int[] getCards() { return cards; }
    public void setCards(int[] cards) { this.cards = cards != null && cards.length >= 4 ? cards : new int[4]; }

    public int getLotteryCount() { return lotteryCount; }
    public void setLotteryCount(int lotteryCount) { this.lotteryCount = Math.max(0, lotteryCount); }

    public int getSendMode() { return sendMode; }
    public void setSendMode(int sendMode) { this.sendMode = sendMode; }

    public List<String> getPlayerNames() { return playerNames; }
    public void setPlayerNames(List<String> playerNames) { this.playerNames = playerNames != null ? new ArrayList<>(playerNames) : new ArrayList<>(); }

    public long getStartAt() { return startAt; }
    public void setStartAt(long startAt) { this.startAt = startAt; }

    public long getEndAt() { return endAt; }
    public void setEndAt(long endAt) { this.endAt = endAt; }

    public long getSentAt() { return sentAt; }
    public void setSentAt(long sentAt) { this.sentAt = sentAt; }

    public boolean hasDeliveredPlayer(String name) { return deliveredPlayers.contains(name); }
    public void addDeliveredPlayer(String name) { deliveredPlayers.add(name); }
    public Set<String> getDeliveredPlayers() { return deliveredPlayers; }

    /**
     * 判断当前时间是否在邮件有效期内。
     */
    public boolean isActive() {
        long now = System.currentTimeMillis();
        if (startAt > 0 && now < startAt) return false;
        if (endAt > 0 && now > endAt) return false;
        return true;
    }

    /**
     * 根据发送模式判断目标玩家是否应该收到此邮件。
     */
    public boolean shouldSendTo(String playerName) {
        return switch (sendMode) {
            case 0 -> true; // 全部
            case 1 -> playerNames.contains(playerName); // 白名单
            case 2 -> !playerNames.contains(playerName); // 黑名单
            default -> true;
        };
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id.toString());
        obj.addProperty("title", title);
        obj.addProperty("content", content);
        JsonArray cardsJson = new JsonArray();
        for (int c : cards) cardsJson.add(c);
        obj.add("cards", cardsJson);
        obj.addProperty("lotteryCount", lotteryCount);
        obj.addProperty("sendMode", sendMode);
        JsonArray names = new JsonArray();
        for (String n : playerNames) names.add(n);
        obj.add("playerNames", names);
        obj.addProperty("startAt", startAt);
        obj.addProperty("endAt", endAt);
        obj.addProperty("sentAt", sentAt);
        JsonArray delivered = new JsonArray();
        for (String n : deliveredPlayers) delivered.add(n);
        obj.add("deliveredPlayers", delivered);
        return obj;
    }

    public static MailRecord fromJson(JsonObject obj) {
        UUID id = UUID.fromString(obj.get("id").getAsString());
        MailRecord r = new MailRecord(id);
        r.title = obj.has("title") ? obj.get("title").getAsString() : "";
        r.content = obj.has("content") ? obj.get("content").getAsString() : "";
        if (obj.has("cards")) {
            int i = 0;
            for (var e : obj.getAsJsonArray("cards")) {
                if (i < 4) r.cards[i++] = e.getAsInt();
            }
        }
        r.lotteryCount = obj.has("lotteryCount") ? obj.get("lotteryCount").getAsInt() : 0;
        r.sendMode = obj.has("sendMode") ? obj.get("sendMode").getAsInt() : 0;
        if (obj.has("playerNames")) {
            for (var e : obj.getAsJsonArray("playerNames")) r.playerNames.add(e.getAsString());
        }
        r.startAt = obj.has("startAt") ? obj.get("startAt").getAsLong() : 0;
        r.endAt = obj.has("endAt") ? obj.get("endAt").getAsLong() : 0;
        r.sentAt = obj.has("sentAt") ? obj.get("sentAt").getAsLong() : 0;
        if (obj.has("deliveredPlayers")) {
            for (var e : obj.getAsJsonArray("deliveredPlayers")) r.deliveredPlayers.add(e.getAsString());
        }
        return r;
    }
}
