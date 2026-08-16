package com.kghua.npcai.data;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * 玩家投稿数据模型。
 * type: "角色" 或 "修饰符"
 */
public class Contribution {
    public static final String TYPE_ROLE = "角色";
    public static final String TYPE_MODIFIER = "修饰符";
    public static final String[] TYPES = {TYPE_ROLE, TYPE_MODIFIER};
    /** 角色投稿可选阵营 */
    public static final String[] FACTIONS = {"平民阵营", "警长阵营", "独赢中立", "杀手中立", "杀手阵营"};

    private final UUID id;
    private String type = TYPE_ROLE;
    private String title = "";
    private String shortDesc = "";
    private String description = "";
    private String shop = "";
    private String background = "";
    private String faction = "";
    private String authorName = "";
    private UUID authorId;
    private long createdAt;
    private int likes = 0;
    private int period = 1;
    /** 是否已审核通过（管理端审核；false=待审核） */
    private boolean approved = false;

    // 第一期起始日：2026-08-03（周一），一期=14天（周一到第二周周天）
    private static final long PERIOD_EPOCH_MS = 1785744000000L; // 2026-08-03 00:00:00 UTC+8
    private static final long PERIOD_LENGTH_MS = 14L * 24 * 60 * 60 * 1000;

    /** 计算某个时间戳所属的期数 */
    public static int getPeriodFor(long timeMs) {
        long diff = timeMs - PERIOD_EPOCH_MS;
        int period = (int) (diff / PERIOD_LENGTH_MS) + 1;
        return Math.max(1, period);
    }

    /** 当前期数 */
    public static int getCurrentPeriod() {
        return getPeriodFor(System.currentTimeMillis());
    }

    /** 某期结束时间（毫秒，即下一期起始时间）；网站投稿页倒计时用 */
    public static long getPeriodEndAt(int period) {
        return PERIOD_EPOCH_MS + (long) period * PERIOD_LENGTH_MS;
    }

    public Contribution(UUID id) {
        this.id = id;
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getShortDesc() { return shortDesc; }
    public void setShortDesc(String shortDesc) { this.shortDesc = shortDesc; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getShop() { return shop; }
    public void setShop(String shop) { this.shop = shop; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public String getFaction() { return faction; }
    public void setFaction(String faction) { this.faction = faction; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public UUID getAuthorId() { return authorId; }
    public void setAuthorId(UUID authorId) { this.authorId = authorId; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = Math.max(0, likes); }
    public void incrementLikes() { this.likes++; }
    public void decrementLikes() { this.likes = Math.max(0, this.likes - 1); }
    public int getPeriod() { return period; }
    public void setPeriod(int period) { this.period = Math.max(1, period); }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id.toString());
        obj.addProperty("type", type);
        obj.addProperty("title", title);
        obj.addProperty("shortDesc", shortDesc);
        obj.addProperty("description", description);
        obj.addProperty("shop", shop);
        obj.addProperty("background", background);
        obj.addProperty("faction", faction);
        obj.addProperty("authorName", authorName);
        obj.addProperty("authorId", authorId != null ? authorId.toString() : "");
        obj.addProperty("createdAt", createdAt);
        obj.addProperty("likes", likes);
        obj.addProperty("period", period);
        obj.addProperty("approved", approved);
        return obj;
    }

    public static Contribution fromJson(JsonObject obj) {
        UUID id = UUID.fromString(obj.get("id").getAsString());
        Contribution c = new Contribution(id);
        c.type = obj.has("type") ? obj.get("type").getAsString() : TYPE_ROLE;
        c.title = obj.has("title") ? obj.get("title").getAsString() : "";
        c.shortDesc = obj.has("shortDesc") ? obj.get("shortDesc").getAsString() : "";
        c.description = obj.has("description") ? obj.get("description").getAsString() : "";
        c.shop = obj.has("shop") ? obj.get("shop").getAsString() : "";
        c.background = obj.has("background") ? obj.get("background").getAsString() : "";
        c.faction = obj.has("faction") ? obj.get("faction").getAsString() : "";
        c.authorName = obj.has("authorName") ? obj.get("authorName").getAsString() : "";
        if (obj.has("authorId") && !obj.get("authorId").getAsString().isEmpty()) {
            try {
                c.authorId = UUID.fromString(obj.get("authorId").getAsString());
            } catch (IllegalArgumentException ignored) {}
        }
        c.createdAt = obj.has("createdAt") ? obj.get("createdAt").getAsLong() : 0;
        c.likes = obj.has("likes") ? obj.get("likes").getAsInt() : 0;
        c.period = obj.has("period") ? obj.get("period").getAsInt() : 1;
        c.approved = obj.has("approved") && obj.get("approved").getAsBoolean();
        return c;
    }
}
