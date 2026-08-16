package com.kghua.npcai.webbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 网站互通配置：复用 config/npcai-server.json 的 web_bridge 节（照 NpcAiServerConfig 的 Gson 缓存模式）。
 * 首次加载会把 web_bridge 默认节合并写入现有文件，不影响原有字段。
 */
public class WebConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static JsonObject cached = null;
    private static boolean loaded = false;

    public static boolean enabled() {
        JsonObject o = get();
        return !o.has("enabled") || o.get("enabled").getAsBoolean();
    }

    public static String url() {
        JsonObject o = get();
        return o.has("url") ? o.get("url").getAsString() : "ws://127.0.0.1:3000/ws";
    }

    public static String token() {
        JsonObject o = get();
        return o.has("token") ? o.get("token").getAsString() : "dev-bridge-token";
    }

    public static String serverId() {
        JsonObject o = get();
        return o.has("server_id") ? o.get("server_id").getAsString() : "habitatrain";
    }

    public static String serverName() {
        JsonObject o = get();
        return o.has("server_name") ? o.get("server_name").getAsString() : "残月哈比快车";
    }

    public static String serverDescription() {
        JsonObject o = get();
        return o.has("server_description") ? o.get("server_description").getAsString() : "";
    }

    public static String serverAddress() {
        JsonObject o = get();
        return o.has("server_address") ? o.get("server_address").getAsString() : "zx2.sjcmc.cn:36197";
    }

    private static synchronized JsonObject get() {
        if (loaded) {
            return cached;
        }
        cached = defaults();
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                boolean changed = false;
                if (obj.has("web_bridge") && obj.get("web_bridge").isJsonObject()) {
                    JsonObject wb = obj.getAsJsonObject("web_bridge");
                    cached = deepMerge(cached, wb);
                } else {
                    obj.add("web_bridge", cached);
                    changed = true;
                }
                if (changed) {
                    Files.writeString(path, GSON.toJson(obj), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                // 读取失败时使用默认值
            }
        } else {
            saveDefault(path);
        }
        loaded = true;
        return cached;
    }

    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", false);
        o.addProperty("url", "ws://127.0.0.1:3000/ws");
        o.addProperty("token", "dev-bridge-token");
        o.addProperty("server_id", "habitatrain");
        o.addProperty("server_name", "残月哈比快车");
        o.addProperty("server_description", "");
        o.addProperty("server_address", "zx2.sjcmc.cn:36197");
        return o;
    }

    private static JsonObject deepMerge(JsonObject base, JsonObject override) {
        JsonObject out = base.deepCopy();
        for (String key : override.keySet()) {
            if (override.get(key).isJsonObject() && out.has(key) && out.get(key).isJsonObject()) {
                out.add(key, deepMerge(out.getAsJsonObject(key), override.getAsJsonObject(key)));
            } else {
                out.add(key, override.get(key));
            }
        }
        return out;
    }

    private static void saveDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            JsonObject wb = defaults();
            wb.addProperty("_comment", "网站互通桥接配置：enabled 是否启用；url/token 与网站后端 WEB_BRIDGE_TOKEN 对应");
            obj.add("web_bridge", wb);
            Files.writeString(path, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // ignore
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("npcai-server.json");
    }
}
