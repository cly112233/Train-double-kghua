package com.kghua.npcai.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 地图组成员权限持久化存储。
 */
public class PlayerMapGroupStorage {
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("player_mapgroup.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Set<UUID> members = new HashSet<>();
    private static boolean dirty = false;

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                members = new HashSet<>();
                return;
            }
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            Set<UUID> loaded = GSON.fromJson(text, new TypeToken<Set<UUID>>(){}.getType());
            members = loaded != null ? new HashSet<>(loaded) : new HashSet<>();
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to load player map group storage", e);
            members = new HashSet<>();
        }
    }

    public static void save() {
        if (!dirty) return;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(members), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save player map group storage", e);
        }
    }

    public static boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public static void add(UUID playerId) {
        if (members.add(playerId)) {
            dirty = true;
            save();
        }
    }

    public static void remove(UUID playerId) {
        if (members.remove(playerId)) {
            dirty = true;
            save();
        }
    }

    public static void toggle(UUID playerId) {
        if (members.contains(playerId)) {
            remove(playerId);
        } else {
            add(playerId);
        }
    }
}
