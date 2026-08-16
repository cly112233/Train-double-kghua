package com.kghua.npcai.webbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kghua.npcai.NpcAiMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网站互通桥接：mod 作为 WebSocket 客户端主动连出到网站后端（面板服无法跑外部进程）。
 *  - 心跳 10s（在线数/上限/玩家名单），后端 30s 无心跳判离线
 *  - 游戏聊天上行事件 chat.game（未连接时丢弃不缓冲）
 *  - 断线指数退避重连 2s → 60s
 * 所有游戏状态读写都在主线程（onText/onClose 经 server.execute 回主线程）。
 */
public final class WebBridge {
    private static MinecraftServer server;
    private static WebSocketClient client;
    private static ScheduledExecutorService reconnectPool;
    private static volatile boolean connected = false;
    private static volatile boolean suppressChatEcho = false;
    private static final AtomicLong reconnectDelayMs = new AtomicLong(0);
    private static final long BACKOFF_MIN_MS = 2_000;
    private static final long BACKOFF_MAX_MS = 60_000;

    private WebBridge() {}

    public static void start(MinecraftServer srv) {
        server = srv;
        if (!WebConfig.enabled()) {
            NpcAiMod.LOGGER.info("[webbridge] 未启用（config/npcai-server.json web_bridge.enabled=false）");
            return;
        }

        // 游戏聊天上行（网页发言经 say 指令执行时用 suppressChatEcho 抑制回显）
        ServerMessageEvents.CHAT_MESSAGE.register((PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) -> {
            if (!connected || suppressChatEcho) return;
            JsonObject ev = new JsonObject();
            ev.addProperty("type", "event");
            JsonObject body = new JsonObject();
            body.addProperty("type", "chat.game");
            JsonObject data = new JsonObject();
            data.addProperty("serverId", WebConfig.serverId());
            data.addProperty("player", sender.getGameProfile().getName());
            data.addProperty("content", message.signedContent());
            body.add("data", data);
            ev.add("event", body);
            send(ev);
        });

        // 心跳：每 10 秒（200 tick）上报在线数/上限/玩家名单
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (connected && s.getTickCount() % 200 == 0) {
                sendHeartbeat();
            }
        });

        client = new WebSocketClient();
        reconnectPool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "webbridge-reconnect");
            t.setDaemon(true);
            return t;
        });
        reconnectDelayMs.set(0);
        scheduleConnect(0);
        NpcAiMod.LOGGER.info("[webbridge] 已启动，目标 {}", WebConfig.url());
    }

    public static void stop() {
        connected = false;
        if (client != null) {
            client.close();
            client = null;
        }
        if (reconnectPool != null) {
            reconnectPool.shutdownNow();
            reconnectPool = null;
        }
        server = null;
    }

    public static void send(JsonObject obj) {
        WebSocketClient c = client;
        if (c != null && connected) {
            c.send(obj.toString());
        }
    }

    // ---- 连接管理（reconnectPool 线程） ----

    private static void scheduleConnect(long delayMs) {
        ScheduledExecutorService pool = reconnectPool;
        if (pool == null || pool.isShutdown()) return;
        pool.schedule(() -> {
            MinecraftServer srv = server;
            if (srv == null || connected) return;
            WebSocketClient c = client;
            if (c == null) return;
            try {
                c.connect(WebConfig.url(), WebConfig.token(), new WebSocketClient.Listener() {
                    @Override
                    public void onOpen() {
                        // 握手：hello 携带 token + 服务器信息
                        JsonObject hello = new JsonObject();
                        hello.addProperty("type", "hello");
                        hello.addProperty("role", "mod");
                        hello.addProperty("token", WebConfig.token());
                        JsonObject s = new JsonObject();
                        s.addProperty("server_id", WebConfig.serverId());
                        s.addProperty("name", WebConfig.serverName());
                        s.addProperty("description", WebConfig.serverDescription());
                        s.addProperty("address", WebConfig.serverAddress());
                        hello.add("server", s);
                        c.send(hello.toString());
                    }

                    @Override
                    public void onText(String text) {
                        marshalToMain(() -> handleText(text));
                    }

                    @Override
                    public void onClose() {
                        connected = false;
                        NpcAiMod.LOGGER.warn("[webbridge] 连接断开，稍后重连");
                        scheduleReconnect();
                    }

                    @Override
                    public void onError(Throwable t) {
                        connected = false;
                        NpcAiMod.LOGGER.warn("[webbridge] 连接错误：{}", t.toString());
                        scheduleReconnect();
                    }
                });
            } catch (Exception e) {
                NpcAiMod.LOGGER.warn("[webbridge] 连接失败：{}", e.toString());
                scheduleReconnect();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /** 断线后指数退避：2s → 4s → 8s … 封顶 60s；连接成功后由 hello_ok 重置 */
    private static void scheduleReconnect() {
        long current = reconnectDelayMs.get();
        long next = Math.min(Math.max(current * 2, BACKOFF_MIN_MS), BACKOFF_MAX_MS);
        reconnectDelayMs.set(next);
        scheduleConnect(next);
    }

    private static void handleText(String text) {
        try {
            JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
            String type = msg.has("type") ? msg.get("type").getAsString() : "";
            switch (type) {
                case "hello_ok" -> {
                    connected = true;
                    reconnectDelayMs.set(BACKOFF_MIN_MS);
                    NpcAiMod.LOGGER.info("[webbridge] 已连接网站后端");
                    sendHeartbeat();
                }
                case "req" -> CommandDispatcher.dispatch(server, msg);
                case "error" -> NpcAiMod.LOGGER.warn("[webbridge] 后端错误：{}", text);
                default -> NpcAiMod.LOGGER.warn("[webbridge] 未知消息类型：{}", type);
            }
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("[webbridge] 消息解析失败：{}", text, e);
        }
    }

    private static void sendHeartbeat() {
        MinecraftServer srv = server;
        if (srv == null) return;
        JsonObject hb = new JsonObject();
        hb.addProperty("type", "heartbeat");
        hb.addProperty("online", srv.getPlayerCount());
        hb.addProperty("max_players", srv.getMaxPlayers());
        JsonArray players = new JsonArray();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            players.add(p.getGameProfile().getName());
        }
        hb.add("players", players);
        send(hb);
    }

    // HttpClient 回调线程 → 主线程
    private static void marshalToMain(Runnable task) {
        MinecraftServer srv = server;
        if (srv == null) return;
        try {
            srv.execute(task);
        } catch (Exception e) {
            // 服务器已停止：直接在当前线程执行，保证 close 处理仍能清理状态
            task.run();
        }
    }

    /** 网站发言以玩家身份 say 后，抑制 chat.game 回显（避免网站收到重复消息） */
    public static void suppressNextChatEcho() {
        suppressChatEcho = true;
    }

    public static void clearChatEchoSuppression() {
        suppressChatEcho = false;
    }
}
