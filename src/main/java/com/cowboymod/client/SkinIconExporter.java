package com.cowboymod.client;

import com.cowboymod.CowboyMod;
import com.cowboymod.network.SkinIconUploadPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 皮肤图标导出（客户端，2D 贴图版）：
 * 皮肤图标直接用星穹铁道(starrailexpress)mod 的平铺贴图 PNG——mod 资源包内每个皮肤一张真实
 * 物品图（assets/starrailexpress/textures/item/{bat,grenade,knife,gun}/<id>.png），游戏内
 * N 键皮肤系统渲染的就是这些贴图。
 *
 * 为什么放弃 3D 离屏渲染：GL 离屏渲染（tick 直渲、recordRenderCall 帧内渲两版）都出黑图/空图
 * （FBO 内容未定义、GUI 状态残留等），贴图方案零 GL 依赖，直接拷贝像素源最稳。
 *
 * 流程：进世界后读本地 lottery_skin_data/lottery_pool.json 奖池 → 对每个 type/skin 从资源
 * 管理器读贴图 PNG 字节 → SkinIconUploadPacket 发给服务端落盘 + 上传网站（文件名
 * {type}_{skin}.png 与前端 /train/skins/ 一致）。无贴图的皮肤回退到该类型基础贴图
 * （wathe:item/{bat|knife|grenade|revolver}.png），再缺失则跳过（coin 等非皮肤条目本来就该
 * 跳过，网站有剪影图标兜底）。
 */
public final class SkinIconExporter {

    /** 每 tick 最多发送几张（纯 IO 无 GL，全部几百张一两秒内完成） */
    private static final int PER_TICK = 16;

    /** 奖池类型 → 纹理目录（wathe 注册名是 revolver，目录是 gun） */
    private static final Map<String, String> TYPE_DIR = Map.of(
        "bat", "bat",
        "grenade", "grenade",
        "knife", "knife",
        "gun", "gun"
    );

    /** 奖池类型 → 回退基础贴图（无该皮肤贴图时用，命名规则同 wathe 资源包） */
    private static final Map<String, String> FALLBACK = Map.of(
        "bat", "wathe:item/bat.png",
        "grenade", "wathe:item/grenade.png",
        "knife", "wathe:item/knife.png",
        "gun", "wathe:item/revolver.png"
    );

    private SkinIconExporter() {}

    private static boolean queued = false;
    private static boolean done = false;
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int total = 0;
    private static int fail = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (done) return;
            if (mc.player == null || mc.level == null) return;
            if (mc.level.getGameTime() < 200) return; // 等 10 秒，资源就绪
            if (!queued) {
                queued = true;
                queueAll(mc);
            }
            pumpQueue(mc);
        });
    }

    /** 读取奖池并排队（只算路径，无 GL 状态要求） */
    private static void queueAll(Minecraft mc) {
        Set<String> entries;
        try {
            entries = collectFromPool(mc);
        } catch (Exception e) {
            CowboyMod.LOGGER.warn("[skin-icon] 读取奖池配置失败: {}", e.toString());
            done = true;
            return;
        }
        if (entries.isEmpty()) {
            CowboyMod.LOGGER.info("[skin-icon] 奖池配置为空，跳过导出");
            done = true;
            return;
        }

        for (String entry : entries) {
            int slash = entry.indexOf('/');
            if (slash <= 0) continue;
            String type = entry.substring(0, slash);

            String dir = TYPE_DIR.get(type);
            if (dir == null) {
                fail++; // coin/hat 等非贴图类型跳过（网站有剪影图标兜底）
                continue;
            }
            QUEUE.add(entry);
        }

        total = QUEUE.size();
        if (total == 0) {
            done = true;
            return;
        }
        CowboyMod.LOGGER.info("[skin-icon] 排队 {} 张，开始读取贴图", total);
    }

    /** 每 tick 读几张贴图并发包（纯资源读取，无需渲染帧） */
    private static void pumpQueue(Minecraft mc) {
        for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
            String entry = QUEUE.poll();
            if (entry == null) break;
            int slash = entry.indexOf('/');
            String type = entry.substring(0, slash);
            String skin = entry.substring(slash + 1);
            String dir = TYPE_DIR.get(type);

            byte[] png = readPng(mc, "starrailexpress:textures/item/" + dir + "/" + skin + ".png");
            if (png == null) {
                // 个别皮肤没有贴图文件 → 用该类型基础贴图兜底（游戏内也这么显示）
                png = readPng(mc, FALLBACK.get(type));
            }
            if (png == null) {
                fail++;
                continue;
            }
            ClientPlayNetworking.send(new SkinIconUploadPacket(type + "_" + skin + ".png", png));
        }
        if (QUEUE.isEmpty() && !done) {
            done = true;
            CowboyMod.LOGGER.info("[skin-icon] 导出完成：成功 {}，跳过/失败 {}（共 {}）", total - fail, fail, total);
        }
    }

    /** 从资源管理器读贴图 PNG 原始字节（失败返回 null） */
    private static byte[] readPng(Minecraft mc, String path) {
        try {
            var res = mc.getResourceManager().getResource(ResourceLocation.parse(path));
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 lottery_skin_data/lottery_pool.json 收集 "type/skin" 条目 + 每类默认皮肤 */
    private static Set<String> collectFromPool(Minecraft mc) throws Exception {
        Set<String> out = new LinkedHashSet<>();
        Path pool = mc.gameDirectory.toPath().resolve("lottery_skin_data").resolve("lottery_pool.json");
        if (!Files.isRegularFile(pool)) return out;

        JsonObject root = JsonParser.parseString(Files.readString(pool)).getAsJsonObject();
        JsonArray pools = root.getAsJsonArray("Pools");
        for (JsonElement pe : pools) {
            JsonObject p = pe.getAsJsonObject();
            if (!p.get("Enable").getAsBoolean()) continue;
            String type = p.get("PoolType").getAsString();
            JsonArray groups = p.getAsJsonArray("QualityListGroup");
            for (JsonElement ge : groups) {
                JsonObject g = ge.getAsJsonObject();
                JsonArray items = g.getAsJsonArray("ItemList");
                for (JsonElement ie : items) {
                    out.add(ie.getAsString());
                }
            }
            out.add(type + "/default");
        }
        return out;
    }
}
