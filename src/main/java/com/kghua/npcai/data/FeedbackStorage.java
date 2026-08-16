package com.kghua.npcai.data;

import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class FeedbackStorage {
    private static final Path FEEDBACK_DIR = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("fankui");

    public static void save(String playerName, boolean anonymous, String content) {
        try {
            Files.createDirectories(FEEDBACK_DIR);
            long timestamp = System.currentTimeMillis();
            String fileName = FeedbackEntry.makeFileName(playerName, timestamp);
            Path file = FEEDBACK_DIR.resolve(fileName);
            FeedbackEntry entry = new FeedbackEntry(fileName, playerName, anonymous, content, timestamp);
            Files.writeString(file, entry.toFileContent(), StandardCharsets.UTF_8);
            NpcAiMod.LOGGER.info("Saved feedback: {}", file);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save feedback", e);
        }
    }

    public static List<FeedbackEntry> loadAll() {
        List<FeedbackEntry> result = new ArrayList<>();
        try {
            if (!Files.exists(FEEDBACK_DIR)) return result;
            List<Path> files = Files.list(FEEDBACK_DIR)
                .filter(p -> p.toString().endsWith(".txt"))
                .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                .toList();
            for (Path file : files) {
                try {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    if (lines.size() >= 3) {
                        String playerName = lines.get(0);
                        boolean anonymous = playerName.equals("匿名玩家");
                        long timestamp = parseTimestamp(file.getFileName().toString());
                        String content = String.join("\n", lines.subList(2, lines.size()));
                        result.add(new FeedbackEntry(file.getFileName().toString(), anonymous ? "" : playerName, anonymous, content, timestamp));
                    }
                } catch (IOException e) {
                    NpcAiMod.LOGGER.warn("Failed to read feedback {}", file, e);
                }
            }
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to list feedback files", e);
        }
        return result;
    }

    public static List<FeedbackEntry> loadSince(long sinceTimestamp) {
        return loadAll().stream()
            .filter(e -> e.timestamp() > sinceTimestamp)
            .sorted(Comparator.comparingLong(FeedbackEntry::timestamp).reversed())
            .toList();
    }

    private static long parseTimestamp(String fileName) {
        try {
            int idx = fileName.indexOf('_');
            if (idx > 0) {
                return Long.parseLong(fileName.substring(0, idx));
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }
}
