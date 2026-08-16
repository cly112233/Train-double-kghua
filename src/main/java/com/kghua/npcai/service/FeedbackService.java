package com.kghua.npcai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.data.FeedbackEntry;
import com.kghua.npcai.data.FeedbackStorage;
import com.kghua.npcai.player.PlayerPendingTracker;
import com.kghua.npcai.webbridge.OfflineUuid;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 反馈服务（网站 feedback.submit/list/export 与游戏内数据互通）：
 * 存储走 FeedbackStorage（npctalltome/fankui/*.txt），与游戏内提交完全同构。
 */
public final class FeedbackService {

    private FeedbackService() {}

    /** 玩家提交反馈（与 SubmitFeedbackPacket 同构：落盘 + 清待办） */
    public static JsonObject submit(MinecraftServer server, String gameId, boolean anonymous, String content)
        throws WebException {
        if (content == null || content.trim().isEmpty()) {
            throw new WebException("E_VALIDATION", "反馈内容不能为空");
        }
        FeedbackStorage.save(gameId, anonymous, content.trim());
        PlayerPendingTracker.clearFeedbackPending(OfflineUuid.of(gameId));
        JsonObject r = new JsonObject();
        r.addProperty("submitted", true);
        return r;
    }

    /** 管理端反馈列表（时间区间筛选，与 RequestFeedbackPacket 同构） */
    public static JsonObject list(long startAt, long endAt) {
        JsonArray arr = new JsonArray();
        for (FeedbackEntry e : FeedbackStorage.loadAll()) {
            if (e.timestamp() >= startAt && e.timestamp() <= endAt) {
                arr.add(entryJson(e));
            }
        }
        JsonObject r = new JsonObject();
        r.add("entries", arr);
        return r;
    }

    /** 管理端导出选中反馈（格式与 ExportFeedbackPacket 一致：名字/时间/内容） */
    public static JsonObject export(List<String> fileNames) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = "导出_" + date + ".md";
        StringBuilder sb = new StringBuilder();
        for (FeedbackEntry e : FeedbackStorage.loadAll()) {
            if (!fileNames.contains(e.fileName())) continue;
            String name = e.anonymous() ? "匿名玩家" : e.playerName();
            sb.append(name).append("\n");
            sb.append(formatTime(e.timestamp())).append("\n");
            sb.append(e.content()).append("\n\n");
        }
        JsonObject r = new JsonObject();
        r.addProperty("fileName", fileName);
        r.addProperty("content", sb.toString());
        return r;
    }

    /** 与 NpcAiMod.formatFeedbackTime 一致（东八区 yyyy-MM-dd HH:mm:ss） */
    public static String formatTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static JsonObject entryJson(FeedbackEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("fileName", e.fileName());
        o.addProperty("playerName", e.playerName());
        o.addProperty("anonymous", e.anonymous());
        o.addProperty("content", e.content());
        o.addProperty("timestamp", e.timestamp());
        return o;
    }
}
