package com.kghua.npcai.data;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

/**
 * 问卷数据模型。
 */
public class Questionnaire {
    private final UUID id;
    private String title = "";
    private List<String> questions = new ArrayList<>();
    private List<String> hints = new ArrayList<>();
    private long startAt;
    private long endAt;
    private final List<Response> responses = new ArrayList<>();
    private long createdAt;

    public Questionnaire(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getQuestions() {
        return questions;
    }

    public void setQuestions(List<String> questions) {
        this.questions = questions != null ? new ArrayList<>(questions) : new ArrayList<>();
    }

    public List<String> getHints() {
        return hints;
    }

    public void setHints(List<String> hints) {
        this.hints = hints != null ? new ArrayList<>(hints) : new ArrayList<>();
    }

    public String getHint(int index) {
        if (index >= 0 && index < hints.size()) {
            return hints.get(index);
        }
        return "";
    }

    public long getStartAt() {
        return startAt;
    }

    public void setStartAt(long startAt) {
        this.startAt = startAt;
    }

    public long getEndAt() {
        return endAt;
    }

    public void setEndAt(long endAt) {
        this.endAt = endAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<Response> getResponses() {
        return responses;
    }

    public boolean hasResponded(String playerName) {
        for (Response r : responses) {
            if (r.playerName.equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    public boolean isActive() {
        long now = System.currentTimeMillis();
        if (startAt > 0 && now < startAt) return false;
        if (endAt > 0 && now > endAt) return false;
        return true;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id.toString());
        obj.addProperty("title", title);
        JsonArray qs = new JsonArray();
        for (String q : questions) qs.add(q);
        obj.add("questions", qs);
        JsonArray hs = new JsonArray();
        for (String h : hints) hs.add(h);
        obj.add("hints", hs);
        obj.addProperty("startAt", startAt);
        obj.addProperty("endAt", endAt);
        obj.addProperty("createdAt", createdAt);
        JsonArray rs = new JsonArray();
        for (Response r : responses) rs.add(r.toJson());
        obj.add("responses", rs);
        return obj;
    }

    public static Questionnaire fromJson(JsonObject obj) {
        UUID id = UUID.fromString(obj.get("id").getAsString());
        Questionnaire q = new Questionnaire(id);
        q.title = obj.has("title") ? obj.get("title").getAsString() : "";
        q.startAt = obj.has("startAt") ? obj.get("startAt").getAsLong() : 0;
        q.endAt = obj.has("endAt") ? obj.get("endAt").getAsLong() : 0;
        q.createdAt = obj.has("createdAt") ? obj.get("createdAt").getAsLong() : 0;
        Type listType = new TypeToken<List<String>>(){}.getType();
        if (obj.has("questions")) {
            q.questions = new Gson().fromJson(obj.get("questions"), listType);
        }
        if (obj.has("hints")) {
            q.hints = new Gson().fromJson(obj.get("hints"), listType);
        }
        if (obj.has("responses")) {
            for (JsonElement e : obj.getAsJsonArray("responses")) {
                q.responses.add(Response.fromJson(e.getAsJsonObject()));
            }
        }
        return q;
    }

    public static class Response {
        public String playerName;
        public long respondedAt;
        public List<String> answers;

        public Response(String playerName, long respondedAt, List<String> answers) {
            this.playerName = playerName;
            this.respondedAt = respondedAt;
            this.answers = answers != null ? new ArrayList<>(answers) : new ArrayList<>();
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("playerName", playerName);
            obj.addProperty("respondedAt", respondedAt);
            JsonArray arr = new JsonArray();
            for (String a : answers) arr.add(a);
            obj.add("answers", arr);
            return obj;
        }

        public static Response fromJson(JsonObject obj) {
            String player = obj.has("playerName") ? obj.get("playerName").getAsString() : "";
            long time = obj.has("respondedAt") ? obj.get("respondedAt").getAsLong() : 0;
            List<String> answers = new ArrayList<>();
            if (obj.has("answers")) {
                for (JsonElement e : obj.getAsJsonArray("answers")) {
                    answers.add(e.getAsString());
                }
            }
            return new Response(player, time, answers);
        }
    }
}
