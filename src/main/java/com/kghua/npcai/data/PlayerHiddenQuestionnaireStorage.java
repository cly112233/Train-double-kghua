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
import java.util.*;

/**
 * 持久化存储每个玩家主动隐藏的问卷 UUID。
 * 隐藏仅对对应玩家生效，服务端不会删除问卷。
 */
public class PlayerHiddenQuestionnaireStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("hidden_questionnaires.json");

    // player UUID string -> hidden questionnaire UUID set
    private static Map<String, Set<String>> data = new HashMap<>();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        try {
            if (!Files.exists(FILE)) {
                data = new HashMap<>();
                loaded = true;
                return;
            }
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            Map<String, Set<String>> loadedData = GSON.fromJson(json, new TypeToken<Map<String, Set<String>>>() {}.getType());
            data = loadedData != null ? new HashMap<>(loadedData) : new HashMap<>();
            loaded = true;
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to load hidden questionnaires", e);
            data = new HashMap<>();
            loaded = true;
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save hidden questionnaires", e);
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    public static synchronized void hide(UUID playerId, UUID questionnaireId) {
        ensureLoaded();
        String key = playerId.toString();
        Set<String> set = data.computeIfAbsent(key, k -> new HashSet<>());
        if (set.add(questionnaireId.toString())) {
            save();
        }
    }

    public static synchronized Set<UUID> getHidden(UUID playerId) {
        ensureLoaded();
        Set<String> set = data.getOrDefault(playerId.toString(), Collections.emptySet());
        Set<UUID> result = new HashSet<>();
        for (String s : set) {
            try {
                result.add(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                NpcAiMod.LOGGER.warn("Invalid hidden questionnaire UUID: {}", s);
            }
        }
        return result;
    }
}
