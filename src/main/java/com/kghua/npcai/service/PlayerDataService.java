package com.kghua.npcai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.data.CerebellumStorage;
import com.kghua.npcai.webbridge.OfflineUuid;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

import java.util.Map;
import java.util.UUID;

/**
 * 个人数据聚合（网站 stats.mine/skins.mine）：
 * 战绩 + 小脑（计分板权威）+ 4 身份卡 + 金币/抽奖次数 + 四类皮肤。
 * 战绩与小脑离线可读；身份卡/金币/皮肤依赖在线内存态，离线时缺省。
 */
public final class PlayerDataService {
    private static final String[] SKIN_TYPES = {"knife", "revolver", "bat", "grenade"};

    private PlayerDataService() {}

    public static JsonObject mine(MinecraftServer server, String gameId) {
        JsonObject r = new JsonObject();
        r.addProperty("gameId", gameId);
        ServerPlayer player = server.getPlayerList().getPlayerByName(gameId);
        r.addProperty("online", player != null);

        // 战绩：O 键同源（离线读盘）
        r.add("stats", StatsService.getStats(server, gameId));

        // 小脑：计分板权威（kgxnbang=当前次数, kgxnbang_punish=惩罚次数）+ 待执行
        JsonObject cer = new JsonObject();
        cer.addProperty("current", scoreboardValue(server, gameId, "kgxnbang"));
        cer.addProperty("punish", scoreboardValue(server, gameId, "kgxnbang_punish"));
        cer.addProperty("pending", CerebellumStorage.getPendingCount(OfflineUuid.of(gameId)));
        r.add("cerebellum", cer);

        if (player != null) {
            r.add("cards", cardsOf(player));
            JsonObject eco = new JsonObject();
            eco.addProperty("coins", PlayerEconomyManager.getCoinNum(player));
            eco.addProperty("draws", PlayerEconomyManager.getLootChance(player));
            r.add("economy", eco);
            r.add("skins", skinsOf(player));
        }
        return r;
    }

    /** skins.mine：四类皮肤（name + color，default 恒有） */
    public static JsonObject skinsOnly(MinecraftServer server, String gameId) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(gameId);
        if (player == null) {
            return null;
        }
        return skinsOf(player);
    }

    /** 4 种身份卡数量（顺序：杀手/平民/独赢中立/杀手中立，与进度背包一致） */
    private static JsonObject cardsOf(ServerPlayer player) {
        ProgressionState state = ProgressionDataManager.get(player);
        JsonObject cards = new JsonObject();
        cards.addProperty("killer", state.factionCards.getOrDefault(ProgressionState.FactionCardType.KILLER, 0));
        cards.addProperty("civilian", state.factionCards.getOrDefault(ProgressionState.FactionCardType.CIVILIAN, 0));
        cards.addProperty("neutral", state.factionCards.getOrDefault(ProgressionState.FactionCardType.NEUTRAL, 0));
        cards.addProperty("neutralForKiller",
            state.factionCards.getOrDefault(ProgressionState.FactionCardType.NEUTRAL_FOR_KILLER, 0));
        return cards;
    }

    private static JsonObject skinsOf(ServerPlayer player) {
        JsonObject skins = new JsonObject();
        Map<String, Map<String, Boolean>> unlocked = PlayerEconomyManager.getUnlockedSkins(player);
        for (String type : SKIN_TYPES) {
            JsonArray arr = new JsonArray();
            arr.add(skinEntry(type, "default"));
            Map<String, Boolean> owned = unlocked.getOrDefault(type, Map.of());
            for (Map.Entry<String, Boolean> e : owned.entrySet()) {
                if ("default".equalsIgnoreCase(e.getKey())) continue;
                if (!Boolean.TRUE.equals(e.getValue())) continue;
                arr.add(skinEntry(type, e.getKey()));
            }
            skins.add(type, arr);
        }
        return skins;
    }

    private static JsonObject skinEntry(String type, String name) {
        JsonObject s = new JsonObject();
        s.addProperty("name", name);
        ItemSkinManager.Skin skin = ItemSkinManager.getSkins(type).get(name);
        s.addProperty("color", skin != null ? skin.getColor() : 0x9E9E9E);
        return s;
    }

    /** 计分板只读取值（未上榜=0；不存在该 objective=0） */
    private static int scoreboardValue(MinecraftServer server, String name, String objectiveName) {
        try {
            Objective obj = server.getScoreboard().getObjective(objectiveName);
            if (obj == null) return 0;
            ReadOnlyScoreInfo info = server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(name), obj);
            return info == null ? 0 : info.value();
        } catch (Exception ignored) {
            return 0;
        }
    }

    // UUID 工具引用保持与 OfflineUuid 一致，避免手写 MD5
    static UUID uuidOf(String gameId) {
        return OfflineUuid.of(gameId);
    }
}
