package com.kghua.npcai.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * NPC管理员权限持久化存储。
 * 内置初始管理员：kg_WCLyy
 */
public class NpcAdminStorage {
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("npc_admin.json");
    private static final Set<UUID> ADMINS = new HashSet<>();
    private static boolean loaded = false;

    public static boolean isAdmin(UUID uuid) {
        ensureLoaded();
        return ADMINS.contains(uuid);
    }

    public static void addAdmin(UUID uuid) {
        ensureLoaded();
        if (ADMINS.add(uuid)) save();
    }

    public static void removeAdmin(UUID uuid) {
        ensureLoaded();
        if (ADMINS.remove(uuid)) save();
    }

    public static Set<UUID> getAllAdmins() {
        ensureLoaded();
        return new HashSet<>(ADMINS);
    }

    /** 服务器启动时调用，确保内置管理员已存在 */
    public static void ensureBuiltinAdmin(net.minecraft.server.MinecraftServer server) {
        ensureLoaded();
        // 查找名为 kg_WCLyy 的玩家
        var profile = server.getProfileCache().get("kg_WCLyy");
        if (profile.isPresent()) {
            UUID builtinUuid = profile.get().getId();
            if (!ADMINS.contains(builtinUuid)) {
                ADMINS.add(builtinUuid);
                save();
                NpcAiMod.LOGGER.info("NPC AI: Built-in admin kg_WCLyy ({}) added", builtinUuid);
            }
        }
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                loaded = true;
                return;
            }
            String text = Files.readString(FILE);
            JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
            for (JsonElement e : arr) {
                try {
                    ADMINS.add(UUID.fromString(e.getAsString()));
                } catch (IllegalArgumentException ignored) {}
            }
            loaded = true;
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to load NPC admin list", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonArray arr = new JsonArray();
            for (UUID uuid : ADMINS) arr.add(uuid.toString());
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(arr));
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save NPC admin list", e);
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }
}
