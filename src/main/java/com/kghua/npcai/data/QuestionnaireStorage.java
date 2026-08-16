package com.kghua.npcai.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class QuestionnaireStorage {
    private static final Path DIR = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("diaocha");
    private static final Path HIDDEN_FILE = DIR.resolve("player_hidden.json");
    private static final Map<UUID, Set<UUID>> HIDDEN_QUESTIONNAIRES = new HashMap<>();
    private static boolean hiddenLoaded = false;

    public static List<Questionnaire> loadAll() {
        List<Questionnaire> list = new ArrayList<>();
        try {
            if (!Files.exists(DIR)) return list;
            Files.list(DIR)
                .filter(p -> p.toString().endsWith(".json") && !p.getFileName().toString().equals("player_hidden.json"))
                .forEach(p -> {
                    try {
                        String text = Files.readString(p, StandardCharsets.UTF_8);
                        JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                        list.add(Questionnaire.fromJson(obj));
                    } catch (Exception e) {
                        NpcAiMod.LOGGER.warn("Failed to load questionnaire {}", p, e);
                    }
                });
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to list questionnaires", e);
        }
        list.sort(Comparator.comparingLong(Questionnaire::getCreatedAt).reversed());
        return list;
    }

    public static Questionnaire get(UUID id) {
        Path file = DIR.resolve(id + ".json");
        if (!Files.exists(file)) return null;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return Questionnaire.fromJson(JsonParser.parseString(text).getAsJsonObject());
        } catch (Exception e) {
            NpcAiMod.LOGGER.warn("Failed to load questionnaire {}", id, e);
            return null;
        }
    }

    public static void save(Questionnaire questionnaire) {
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(questionnaire.getId() + ".json");
            Files.writeString(file, questionnaire.toJson().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save questionnaire {}", questionnaire.getId(), e);
        }
    }

    public static void delete(UUID id) {
        try {
            Path file = DIR.resolve(id + ".json");
            Files.deleteIfExists(file);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to delete questionnaire {}", id, e);
        }
    }

    public static void hideForPlayer(UUID playerId, UUID questionnaireId) {
        ensureHiddenLoaded();
        HIDDEN_QUESTIONNAIRES.computeIfAbsent(playerId, k -> new HashSet<>()).add(questionnaireId);
        saveHidden();
    }

    public static boolean isHiddenForPlayer(UUID playerId, UUID questionnaireId) {
        ensureHiddenLoaded();
        return HIDDEN_QUESTIONNAIRES.getOrDefault(playerId, Collections.emptySet()).contains(questionnaireId);
    }

    public static List<Questionnaire> filterVisibleForPlayer(List<Questionnaire> list, UUID playerId) {
        ensureHiddenLoaded();
        Set<UUID> hidden = HIDDEN_QUESTIONNAIRES.getOrDefault(playerId, Collections.emptySet());
        List<Questionnaire> result = new ArrayList<>();
        for (Questionnaire q : list) {
            if (!hidden.contains(q.getId())) {
                result.add(q);
            }
        }
        return result;
    }

    private static void ensureHiddenLoaded() {
        if (hiddenLoaded) return;
        hiddenLoaded = true;
        if (!Files.exists(HIDDEN_FILE)) return;
        try {
            String text = Files.readString(HIDDEN_FILE, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(e.getKey());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                Set<UUID> set = new HashSet<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    try {
                        set.add(UUID.fromString(el.getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                HIDDEN_QUESTIONNAIRES.put(playerId, set);
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.warn("Failed to load hidden questionnaires", e);
        }
    }

    private static void saveHidden() {
        try {
            Files.createDirectories(DIR);
            JsonObject obj = new JsonObject();
            for (Map.Entry<UUID, Set<UUID>> e : HIDDEN_QUESTIONNAIRES.entrySet()) {
                JsonArray arr = new JsonArray();
                for (UUID id : e.getValue()) {
                    arr.add(id.toString());
                }
                obj.add(e.getKey().toString(), arr);
            }
            Files.writeString(HIDDEN_FILE, obj.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save hidden questionnaires", e);
        }
    }

    public static void addResponse(UUID id, String playerName, List<String> answers) {
        Questionnaire q = get(id);
        if (q == null) return;
        q.getResponses().add(new Questionnaire.Response(playerName, System.currentTimeMillis(), answers));
        save(q);
    }

    /** 生成问卷导出的 Markdown 内容与文件名（不再写服务端文件，由客户端本地保存） */
    public static String[] exportToMarkdownText(Questionnaire q) {
        try {
            String fileName = q.getTitle().replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9_-]", "_") + ".md";
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(q.getTitle()).append("\n\n");
            for (Questionnaire.Response r : q.getResponses()) {
                sb.append(r.playerName).append("\n");
                sb.append(formatTime(r.respondedAt)).append("\n");
                for (int i = 0; i < r.answers.size(); i++) {
                    sb.append(r.answers.get(i)).append("\n");
                }
                sb.append("\n");
            }
            return new String[]{fileName, sb.toString()};
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to build questionnaire export {}", q.getId(), e);
            return null;
        }
    }

    private static String formatTime(long millis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
