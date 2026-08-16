package com.kghua.npcai.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

public record TeleportPoint(String name, double x, double y, double z, long updatedAt, String category) {

    public static final String[] CATEGORIES = {"小图", "中图", "大图", "其他"};

    public TeleportPoint(String name, double x, double y, double z, long updatedAt) {
        this(name, x, y, z, updatedAt, "其他");
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putLong("UpdatedAt", updatedAt);
        tag.putString("Category", category != null ? category : "其他");
        return tag;
    }

    public static TeleportPoint fromNbt(CompoundTag tag) {
        return new TeleportPoint(
            tag.getString("Name"),
            tag.getDouble("X"),
            tag.getDouble("Y"),
            tag.getDouble("Z"),
            tag.getLong("UpdatedAt"),
            tag.contains("Category") ? tag.getString("Category") : "其他"
        );
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        obj.addProperty("updatedAt", updatedAt);
        obj.addProperty("category", category != null ? category : "其他");
        return obj;
    }

    public static TeleportPoint fromJson(JsonObject obj) {
        return new TeleportPoint(
            obj.get("name").getAsString(),
            obj.get("x").getAsDouble(),
            obj.get("y").getAsDouble(),
            obj.get("z").getAsDouble(),
            obj.has("updatedAt") ? obj.get("updatedAt").getAsLong() : System.currentTimeMillis(),
            obj.has("category") ? obj.get("category").getAsString() : "其他"
        );
    }
}
