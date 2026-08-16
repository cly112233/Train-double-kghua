package com.kghua.npcai.data;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

/**
 * 补偿机制规则。
 */
public class CompensationRule {
    private final UUID id;
    private String title = "";
    private String deathReason = "noellesroles:voodoo";
    private int requiredDeaths = 1;
    private final List<CommandEntry> commands = new ArrayList<>();

    public CompensationRule(UUID id) {
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

    public String getDeathReason() {
        return deathReason;
    }

    public void setDeathReason(String deathReason) {
        this.deathReason = deathReason;
    }

    public int getRequiredDeaths() {
        return requiredDeaths;
    }

    public void setRequiredDeaths(int requiredDeaths) {
        this.requiredDeaths = Math.max(1, requiredDeaths);
    }

    public List<CommandEntry> getCommands() {
        return commands;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id.toString());
        obj.addProperty("title", title);
        obj.addProperty("deathReason", deathReason);
        obj.addProperty("requiredDeaths", requiredDeaths);
        JsonArray arr = new JsonArray();
        for (CommandEntry e : commands) arr.add(e.toJson());
        obj.add("commands", arr);
        return obj;
    }

    public static CompensationRule fromJson(JsonObject obj) {
        UUID id = UUID.fromString(obj.get("id").getAsString());
        CompensationRule rule = new CompensationRule(id);
        rule.title = obj.has("title") ? obj.get("title").getAsString() : "";
        rule.deathReason = obj.has("deathReason") ? obj.get("deathReason").getAsString() : "noellesroles:voodoo";
        rule.requiredDeaths = obj.has("requiredDeaths") ? obj.get("requiredDeaths").getAsInt() : 1;
        if (obj.has("commands")) {
            Type listType = new TypeToken<List<CommandEntry>>(){}.getType();
            rule.commands.addAll(new Gson().fromJson(obj.get("commands"), listType));
        }
        return rule;
    }

    public static class CommandEntry {
        public String name = "";
        public String command = "";

        public CommandEntry() {}

        public CommandEntry(String name, String command) {
            this.name = name;
            this.command = command;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", name);
            obj.addProperty("command", command);
            return obj;
        }

        public static CommandEntry fromJson(JsonObject obj) {
            CommandEntry e = new CommandEntry();
            e.name = obj.has("name") ? obj.get("name").getAsString() : "";
            e.command = obj.has("command") ? obj.get("command").getAsString() : "";
            return e;
        }
    }
}
