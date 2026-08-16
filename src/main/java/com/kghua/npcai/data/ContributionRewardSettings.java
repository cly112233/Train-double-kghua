package com.kghua.npcai.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 投稿奖励设置。
 * 身份卡共4种（顺序与基座mod进度背包展示一致）：杀手阵营/平民阵营/独赢中立/杀手中立。
 * 结构：每次投稿奖励（4种卡各数字+抽奖次数=5项），每期前三名各（4种卡+抽奖次数=5项）。
 */
public class ContributionRewardSettings {
    public static final int CARD_KILLER = 0;
    public static final int CARD_CIVILIAN = 1;
    public static final int CARD_NEUTRAL = 2;
    public static final int CARD_NEUTRAL_FOR_KILLER = 3;
    /** 4种身份卡显示名（与投稿阵营命名一致） */
    public static final String[] CARD_LABELS = {"杀手阵营卡", "平民阵营卡", "独赢中立卡", "杀手中立卡"};

    // 每次投稿奖励：4种卡 + 抽奖次数
    private int[] perSubmitCards = new int[4];
    private int perSubmitLottery = 0;
    // 每期前三名奖励（索引0=第一名, 1=第二名, 2=第三名）：4种卡 + 抽奖次数
    private int[][] placeCards = new int[3][4];
    private int[] placeLottery = new int[3];

    public int getPerSubmitCard(int index) {
        return index >= 0 && index < 4 ? perSubmitCards[index] : 0;
    }

    public void setPerSubmitCard(int index, int value) {
        if (index >= 0 && index < 4) perSubmitCards[index] = Math.max(0, value);
    }

    public int getPerSubmitLottery() {
        return perSubmitLottery;
    }

    /** 每次投稿4种卡数量（拷贝，避免外部修改内部数组） */
    public int[] getPerSubmitCards() {
        return perSubmitCards.clone();
    }

    /** 指定名次（0=第一名）4种卡数量（拷贝） */
    public int[] getPlaceCards(int place) {
        if (place < 0 || place >= 3) return new int[4];
        return placeCards[place].clone();
    }

    public void setPerSubmitLottery(int value) {
        perSubmitLottery = Math.max(0, value);
    }

    public int getPlaceCard(int place, int cardIndex) {
        if (place < 0 || place >= 3 || cardIndex < 0 || cardIndex >= 4) return 0;
        return placeCards[place][cardIndex];
    }

    public void setPlaceCard(int place, int cardIndex, int value) {
        if (place < 0 || place >= 3 || cardIndex < 0 || cardIndex >= 4) return;
        placeCards[place][cardIndex] = Math.max(0, value);
    }

    public int getPlaceLottery(int place) {
        return place >= 0 && place < 3 ? placeLottery[place] : 0;
    }

    public void setPlaceLottery(int place, int value) {
        if (place >= 0 && place < 3) placeLottery[place] = Math.max(0, value);
    }

    /** 整组奖励是否全部为0（未设置） */
    public static boolean isAllZero(int[] cards, int lottery) {
        for (int v : cards) {
            if (v > 0) return false;
        }
        return lottery <= 0;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.add("perSubmitCards", intArrayToJson(perSubmitCards));
        obj.addProperty("perSubmitLottery", perSubmitLottery);
        JsonArray placeArr = new JsonArray();
        for (int[] cards : placeCards) {
            placeArr.add(intArrayToJson(cards));
        }
        obj.add("placeCards", placeArr);
        obj.add("placeLottery", intArrayToJson(placeLottery));
        return obj;
    }

    public static ContributionRewardSettings fromJson(JsonObject obj) {
        ContributionRewardSettings settings = new ContributionRewardSettings();
        if (obj.has("perSubmitCards")) {
            settings.perSubmitCards = intArrayFromJson(obj.getAsJsonArray("perSubmitCards"), 4);
        }
        settings.perSubmitLottery = obj.has("perSubmitLottery") ? obj.get("perSubmitLottery").getAsInt() : 0;
        if (obj.has("placeCards") && obj.getAsJsonArray("placeCards").size() >= 3) {
            for (int p = 0; p < 3; p++) {
                settings.placeCards[p] = intArrayFromJson(obj.getAsJsonArray("placeCards").get(p).getAsJsonArray(), 4);
            }
        }
        if (obj.has("placeLottery")) {
            settings.placeLottery = intArrayFromJson(obj.getAsJsonArray("placeLottery"), 3);
        }
        return settings;
    }

    private static JsonArray intArrayToJson(int[] arr) {
        JsonArray json = new JsonArray();
        for (int v : arr) json.add(v);
        return json;
    }

    private static int[] intArrayFromJson(JsonArray json, int size) {
        int[] arr = new int[size];
        for (int i = 0; i < size && i < json.size(); i++) {
            try {
                arr[i] = Math.max(0, json.get(i).getAsInt());
            } catch (Exception ignored) {
            }
        }
        return arr;
    }
}
