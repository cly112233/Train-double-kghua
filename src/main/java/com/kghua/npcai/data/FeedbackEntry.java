package com.kghua.npcai.data;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record FeedbackEntry(String fileName, String playerName, boolean anonymous, String content, long timestamp) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.of("Asia/Shanghai"));

    public String toFileContent() {
        String displayName = anonymous ? "匿名玩家" : playerName;
        return displayName + "\n" + FORMATTER.format(Instant.ofEpochMilli(timestamp)) + "\n" + content;
    }

    public static String makeFileName(String playerName, long timestamp) {
        return timestamp + "_" + (playerName.isEmpty() ? "anonymous" : playerName) + ".txt";
    }
}
