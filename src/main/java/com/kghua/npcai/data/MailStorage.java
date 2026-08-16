package com.kghua.npcai.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

/**
 * 管理员发布邮件记录的持久化存储。
 */
public class MailStorage {
    private static final Path DIR = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("mails");

    public static List<MailRecord> loadAll() {
        List<MailRecord> list = new ArrayList<>();
        try {
            if (!Files.exists(DIR)) return list;
            Files.list(DIR)
                .filter(p -> p.toString().endsWith(".json"))
                .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                .forEach(p -> {
                    try {
                        String text = Files.readString(p, StandardCharsets.UTF_8);
                        JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                        list.add(MailRecord.fromJson(obj));
                    } catch (Exception e) {
                        NpcAiMod.LOGGER.warn("Failed to load mail record {}", p, e);
                    }
                });
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to list mail records", e);
        }
        return list;
    }

    public static void save(MailRecord record) {
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(record.getId().toString() + ".json");
            Files.writeString(file, record.toJson().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save mail record {}", record.getId(), e);
        }
    }

    public static void delete(UUID id) {
        try {
            Path file = DIR.resolve(id.toString() + ".json");
            Files.deleteIfExists(file);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to delete mail record {}", id, e);
        }
    }
}
