package com.kghua.npcai.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家投稿存储。
 * 目录：npctalltome/juesetougao/
 * - 每投稿一个 JSON：{id}.json
 * - 点赞记录：likes.json
 */
public class ContributionStorage {
    private static final Path DIR = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("juesetougao");
    private static final Path LIKES_FILE = DIR.resolve("likes.json");

    private static final Map<UUID, Contribution> CACHE = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    // 点赞记录: {"yyyy-MM-dd": {"玩家UUID": ["投稿id", ...]}}
    private static final Map<String, Map<String, List<String>>> LIKES = new HashMap<>();

    public static final int DAILY_LIKE_LIMIT = 3;
    /** 每期每玩家两个分区合计最大投稿数 */
    public static final int MAX_SUBMISSIONS_PER_PERIOD = 5;

    /** 某玩家在指定期内已投稿数量（角色+修饰符两个分区合计） */
    public static int countSubmissions(UUID playerId, int period) {
        ensureLoaded();
        int count = 0;
        for (Contribution c : CACHE.values()) {
            if (c.getPeriod() == period
                && c.getAuthorId() != null
                && c.getAuthorId().equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    public static List<Contribution> loadAll() {
        ensureLoaded();
        List<Contribution> list = new ArrayList<>(CACHE.values());
        list.sort(Comparator.comparingInt(Contribution::getLikes).reversed());
        return list;
    }

    public static Contribution get(UUID id) {
        ensureLoaded();
        return CACHE.get(id);
    }

    public static void save(Contribution contribution) {
        ensureLoaded();
        CACHE.put(contribution.getId(), contribution);
        writeToDisk(contribution);
    }

    public static void delete(UUID id) {
        ensureLoaded();
        CACHE.remove(id);
        try {
            Files.deleteIfExists(DIR.resolve(id + ".json"));
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to delete contribution {}", id, e);
        }
    }

    private static void writeToDisk(Contribution c) {
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(c.getId() + ".json");
            Files.writeString(file, c.toJson().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save contribution {}", c.getId(), e);
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(DIR)) {
                try (var stream = Files.list(DIR)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(".json")
                            && !p.getFileName().toString().equals("likes.json"))
                        .forEach(p -> {
                            try {
                                String text = Files.readString(p, StandardCharsets.UTF_8);
                                Contribution c = Contribution.fromJson(JsonParser.parseString(text).getAsJsonObject());
                                CACHE.put(c.getId(), c);
                            } catch (Exception e) {
                                NpcAiMod.LOGGER.warn("Failed to load contribution {}", p, e);
                            }
                        });
                }
            }
            loadLikes();
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to list contributions", e);
        }
    }

    private static void loadLikes() {
        if (!Files.exists(LIKES_FILE)) return;
        try {
            String text = Files.readString(LIKES_FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            for (Map.Entry<String, JsonElement> dayEntry : root.entrySet()) {
                JsonObject dayObj = dayEntry.getValue().getAsJsonObject();
                Map<String, List<String>> dayMap = LIKES.computeIfAbsent(dayEntry.getKey(), k -> new HashMap<>());
                for (Map.Entry<String, JsonElement> playerEntry : dayObj.entrySet()) {
                    List<String> ids = new ArrayList<>();
                    for (JsonElement e : playerEntry.getValue().getAsJsonArray()) {
                        ids.add(e.getAsString());
                    }
                    dayMap.put(playerEntry.getKey(), ids);
                }
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to load likes", e);
        }
    }

    private static void saveLikes() {
        try {
            Files.createDirectories(DIR);
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Map<String, List<String>>> dayEntry : LIKES.entrySet()) {
                JsonObject dayObj = new JsonObject();
                for (Map.Entry<String, List<String>> playerEntry : dayEntry.getValue().entrySet()) {
                    JsonArray arr = new JsonArray();
                    for (String id : playerEntry.getValue()) arr.add(id);
                    dayObj.add(playerEntry.getKey(), arr);
                }
                root.add(dayEntry.getKey(), dayObj);
            }
            Files.writeString(LIKES_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save likes", e);
        }
    }

    private static String today() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /** 今日剩余点赞次数（每天重置3次） */
    public static int getRemainingLikes(UUID playerId) {
        ensureLoaded();
        Map<String, List<String>> todayMap = LIKES.getOrDefault(today(), Collections.emptyMap());
        int used = todayMap.getOrDefault(playerId.toString(), Collections.emptyList()).size();
        return Math.max(0, DAILY_LIKE_LIMIT - used);
    }

    /** 是否已点赞该投稿 */
    public static boolean hasLiked(UUID playerId, UUID contributionId) {
        ensureLoaded();
        Map<String, List<String>> todayMap = LIKES.getOrDefault(today(), Collections.emptyMap());
        return todayMap.getOrDefault(playerId.toString(), Collections.emptyList()).contains(contributionId.toString());
    }

    /**
     * 点赞/取消点赞。
     * @return true=点赞成功，false=取消点赞，null=无权限
     */
    public static Boolean toggleLike(UUID playerId, UUID contributionId) {
        ensureLoaded();
        Contribution c = CACHE.get(contributionId);
        if (c == null) return null;
        // 只能给当前期数的投稿点赞
        if (c.getPeriod() != Contribution.getCurrentPeriod()) return null;

        Map<String, List<String>> todayMap = LIKES.computeIfAbsent(today(), k -> new HashMap<>());
        List<String> myLikes = todayMap.computeIfAbsent(playerId.toString(), k -> new ArrayList<>());

        if (myLikes.contains(contributionId.toString())) {
            // 取消点赞
            myLikes.remove(contributionId.toString());
            c.decrementLikes();
            writeToDisk(c);
            saveLikes();
            return false;
        }
        // 点赞：检查每日限制
        if (myLikes.size() >= DAILY_LIKE_LIMIT) return null;
        myLikes.add(contributionId.toString());
        c.incrementLikes();
        writeToDisk(c);
        saveLikes();
        return true;
    }

    /** 生成投稿导出的 Markdown 内容与文件名（不再写服务端文件，由客户端本地保存） */
    public static String[] exportToMarkdownText(Contribution c) {
        try {
            String safeTitle = c.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
            String safeAuthor = c.getAuthorName().replaceAll("[\\\\/:*?\"<>|]", "_");
            String time = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(c.getCreatedAt()), ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = safeTitle + "_" + safeAuthor + "_" + time + ".md";

            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(c.getTitle()).append("\n\n");
            sb.append("类型：").append(c.getType()).append("\n");
            if (!c.getFaction().isEmpty()) {
                sb.append("阵营：").append(c.getFaction()).append("\n");
            }
            sb.append("投稿玩家：").append(c.getAuthorName()).append("\n");
            sb.append("投稿时间：").append(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(c.getCreatedAt()), ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            sb.append("点赞数：").append(c.getLikes()).append("\n\n");
            if (!c.getShortDesc().isEmpty()) {
                sb.append("## 简介\n").append(c.getShortDesc()).append("\n\n");
            }
            if (!c.getDescription().isEmpty()) {
                sb.append("## 描述\n").append(c.getDescription()).append("\n\n");
            }
            if (!c.getShop().isEmpty()) {
                sb.append("## 商店\n").append(c.getShop()).append("\n\n");
            }
            if (!c.getBackground().isEmpty()) {
                sb.append("## 背景\n").append(c.getBackground()).append("\n");
            }
            return new String[]{fileName, sb.toString()};
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to build contribution export {}", c.getId(), e);
            return null;
        }
    }
}
