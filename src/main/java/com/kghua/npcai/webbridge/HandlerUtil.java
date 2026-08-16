package com.kghua.npcai.webbridge;

import com.google.gson.JsonObject;
import com.kghua.npcai.data.NpcAdminStorage;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 命令处理器公共工具：参数提取 + 管理权限校验（NPC 管理员，离线 UUID） */
public final class HandlerUtil {

    private HandlerUtil() {}

    public static String requireGameId(JsonObject args) throws WebException {
        if (!args.has("gameId") || args.get("gameId").getAsString().isBlank()) {
            throw new WebException("E_VALIDATION", "缺少 gameId");
        }
        return args.get("gameId").getAsString();
    }

    public static String optString(JsonObject args, String key, String def) {
        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsString() : def;
    }

    public static long optLong(JsonObject args, String key, long def) {
        try {
            return args.has(key) ? args.get(key).getAsLong() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean optBoolean(JsonObject args, String key, boolean def) {
        try {
            return args.has(key) ? args.get(key).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static List<String> stringList(JsonObject args, String key) {
        List<String> out = new ArrayList<>();
        if (args.has(key) && args.get(key).isJsonArray()) {
            for (var e : args.getAsJsonArray(key)) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    public static UUID requireUuid(JsonObject args, String key) throws WebException {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            throw new WebException("E_VALIDATION", "缺少参数: " + key);
        }
        try {
            return UUID.fromString(args.get(key).getAsString());
        } catch (IllegalArgumentException e) {
            throw new WebException("E_VALIDATION", "参数无效: " + key);
        }
    }

    /** 解析 int 数组（如奖励卡 4 项），缺失返回 null */
    public static int[] intArray(JsonObject args, String key) {
        if (!args.has(key) || !args.get(key).isJsonArray()) return null;
        var arr = args.getAsJsonArray(key);
        int[] out = new int[Math.min(arr.size(), 4)];
        for (int i = 0; i < out.length; i++) {
            try {
                out[i] = arr.get(i).getAsInt();
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** NPC 管理员校验（与游戏内管理端一致：NpcAdminStorage 离线 UUID） */
    public static void requireAdmin(MinecraftServer server, String gameId) throws WebException {
        if (!isAdmin(gameId)) {
            throw new WebException("E_PERMISSION", "无权限");
        }
    }

    /** 非抛异常的权限判断（如问卷详情按权限决定是否附带回答） */
    public static boolean isAdmin(String gameId) {
        return NpcAdminStorage.isAdmin(OfflineUuid.of(gameId));
    }
}
