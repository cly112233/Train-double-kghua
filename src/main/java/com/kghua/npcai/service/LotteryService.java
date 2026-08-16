package com.kghua.npcai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.habitrain.lottery.config.LotteryConfigService;
import com.habitrain.lottery.grant.CoinToDrawService;
import com.habitrain.lottery.storage.PlayerLotteryStore;
import com.habitrain.lottery.storage.WorldLotteryPaths;
import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.webbridge.OfflineUuid;
import com.kghua.npcai.webbridge.WebException;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.utils.Pair;
import org.agmas.noellesroles.utils.lottery.LotteryManager;
import org.agmas.noellesroles.utils.lottery.LotteryManager.LotteryPool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 抽奖服务（网站 lottery.* 与游戏内抽奖同源）：
 * roll/exchange 直接调用游戏内同一套公开 API（LotteryPool.rollOnce / CoinToDrawService.tryBuy），
 * 与游戏内点击抽卡零逻辑分叉；习惯性副作用（皮肤解锁/金币转换/历史记录 mixin）由原 API 内部完成。
 * roll/exchange 必须在线（机制走游戏代码）；state/history 离线可读。
 */
public final class LotteryService {

    private LotteryService() {}

    /** 抽奖面板状态：卡池列表 + 兑换单价 + 经济（在线走 SRE 运行时，离线读 habitrain 落盘） */
    public static JsonObject state(MinecraftServer server, String gameId) {
        JsonObject r = new JsonObject();
        r.addProperty("exchangeCost", LotteryConfigService.get().getRates().coinPerDraw());

        JsonArray pools = new JsonArray();
        for (LotteryPool pool : LotteryManager.getInstance().getLotteryPools()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", pool.getPoolID());
            p.addProperty("name", pool.getName());
            p.addProperty("type", pool.getType());
            JsonArray groups = new JsonArray();
            for (Pair<Double, List<String>> g : pool.getQualityListGroupConfigs()) {
                JsonObject go = new JsonObject();
                go.addProperty("probability", g.first);
                JsonArray items = new JsonArray();
                for (String item : g.second) {
                    JsonObject io = new JsonObject();
                    io.addProperty("id", item);
                    // 皮肤染色颜色（与游戏内 ItemSkinManager 同一来源；非皮肤条目给 -1 由前端兜底）
                    io.addProperty("color", skinColorOf(item));
                    items.add(io);
                }
                go.add("items", items);
                groups.add(go);
            }
            p.add("groups", groups);
            pools.add(p);
        }
        r.add("pools", pools);

        ServerPlayer online = server.getPlayerList().getPlayerByName(gameId);
        r.addProperty("online", online != null);
        if (online != null) {
            r.addProperty("coins", PlayerEconomyManager.getCoinNum(online));
            r.addProperty("draws", PlayerEconomyManager.getLootChance(online));
        } else {
            // 离线可读：habitrain 落盘（world 目录，与上线时加载同文件）
            UUID uuid = OfflineUuid.of(gameId);
            r.addProperty("coins", PlayerLotteryStore.get().getCoinNum(uuid));
            r.addProperty("draws", PlayerLotteryStore.get().getLootChance(uuid));
        }
        return r;
    }

    /** 抽卡（单抽 count=1 / 十连 count=2..5，与 LootRollServer 处理器逐行一致） */
    public static JsonObject roll(MinecraftServer server, String gameId, int poolId, int count)
        throws WebException {
        ServerPlayer player = requireOnline(server, gameId, "抽奖需要登录游戏");
        if (count < 1 || count > 5) {
            throw new WebException("E_VALIDATION", "一次最多抽 5 次");
        }

        LotteryManager lm = LotteryManager.getInstance();
        LotteryPool pool = lm.getLotteryPool(poolId);
        if (pool == null) {
            throw new WebException("E_VALIDATION", "卡池不存在");
        }

        List<int[]> results = new ArrayList<>();
        for (int i = 0; i < count && lm.canRoll(player); i++) {
            Pair<Integer, Integer> result = pool.rollOnce(player);
            if (result.first != null && result.first != -1) {
                results.add(new int[] {result.first, result.second});
                lm.addOrDegreeLotteryChance(player, -1);
            }
        }
        if (results.isEmpty()) {
            throw new WebException("E_VALIDATION", "抽数不足");
        }

        JsonObject r = new JsonObject();
        r.addProperty("poolId", poolId);
        r.addProperty("poolName", pool.getName());
        JsonArray arr = new JsonArray();
        for (int[] res : results) {
            JsonObject o = new JsonObject();
            o.addProperty("quality", res[0]);
            o.addProperty("index", res[1]);
            arr.add(o);
        }
        r.add("results", arr);
        addEconomy(r, player);
        return r;
    }

    /** 金币兑换抽数（160 金 = 1 次，与 CoinToDrawService.tryBuy 同一实现） */
    public static JsonObject exchange(MinecraftServer server, String gameId) throws WebException {
        ServerPlayer player = requireOnline(server, gameId, "兑换需要登录游戏");
        int cost = LotteryConfigService.get().getRates().coinPerDraw();

        if (!PlayerLotteryStore.get().isTakeoverActive()) {
            throw new WebException("E_VALIDATION", "经济系统尚未就绪");
        }
        UUID uuid = player.getUUID();
        if (PlayerLotteryStore.get().getCoinNum(uuid) < cost) {
            throw new WebException("E_VALIDATION", "金币不足：需要 " + cost + "，当前 "
                + PlayerLotteryStore.get().getCoinNum(uuid));
        }

        // 主线程执行，与游戏内点击兑换同一代码路径（含 SRE 镜像 + 历史记录）
        int exchanged = CoinToDrawService.tryBuy(player);

        JsonObject r = new JsonObject();
        r.addProperty("exchanged", exchanged);
        addEconomy(r, player);
        return r;
    }

    /** 抽奖历史（habitrain 记录：抽卡 chance_delta + 兑换 coin_to_draw，新→旧，最近 50 条） */
    public static JsonObject history(MinecraftServer server, String gameId) {
        JsonObject r = new JsonObject();
        JsonArray arr = new JsonArray();
        try {
            if (WorldLotteryPaths.ready()) {
                Path file = WorldLotteryPaths.historyFile(OfflineUuid.of(gameId));
                if (Files.isRegularFile(file)) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = lines.size() - 1; i >= 0 && arr.size() < 50; i--) {
                        try {
                            arr.add(JsonParser.parseString(lines.get(i)));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.warn("LotteryService: failed to load history for {}", gameId, e);
        }
        r.add("entries", arr);
        return r;
    }

    // ---------- 工具 ----------

    /** 皮肤染色颜色（与游戏内 ItemSkinManager 同一来源）；条目形如 "knife/anubis"，非皮肤（如 coin）给 -1 */
    private static int skinColorOf(String item) {
        if (item == null || !item.contains("/")) return -1;
        String[] parts = item.split("/", 2);
        try {
            var skin = io.wifi.starrailexpress.util.ItemSkinManager.getSkinFromName(parts[0], parts[1]);
            return skin == null ? -1 : skin.getColor();
        } catch (Exception e) {
            return -1;
        }
    }

    private static ServerPlayer requireOnline(MinecraftServer server, String gameId, String msg)
        throws WebException {
        ServerPlayer player = server.getPlayerList().getPlayerByName(gameId);
        if (player == null) {
            throw new WebException("E_OFFLINE", msg);
        }
        return player;
    }

    /** 经济回显（与 sendEconomyRefresh 同口径：SRE 运行时金币/抽数） */
    private static void addEconomy(JsonObject r, ServerPlayer player) {
        r.addProperty("coins", PlayerEconomyManager.getCoinNum(player));
        r.addProperty("draws", PlayerEconomyManager.getLootChance(player));
    }
}
