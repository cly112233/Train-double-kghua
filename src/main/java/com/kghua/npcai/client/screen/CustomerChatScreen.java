package com.kghua.npcai.client.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.client.ClientCache;
import com.kghua.npcai.client.config.NpcAiClientConfig;
import com.kghua.npcai.network.ExecuteAiCommandPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomerChatScreen extends Screen {
    private final String npcName;
    private final int entityId;
    private final String playerUuid;
    private final boolean mapGroupMember;
    private EditBox inputBox;
    private Button teleportButton;
    private Button feedbackButton;
    private Button questionnaireButton;
    private Button mailButton;
    private Button contributionButton;
    private Button mapModeButton;
    private final List<ChatMessage> messages = new ArrayList<>();

    // 待确认执行的指令（AI 生成后等待玩家输入 yes 才发送给服务端）
    private String pendingCommand = null;
    // 消息区滚动
    private double messageScroll = 0;
    private double totalMessageHeight = 0;
    private boolean scrollToBottom = false;
    // 消息点击复制（选取）：每条消息在消息区内的矩形（与 messages 下标一一对应）
    private final List<int[]> messageBounds = new ArrayList<>();
    private long copyHintUntil = 0;
    // 当前打开的对话界面（用于显示指令执行结果）
    private static CustomerChatScreen currentScreen = null;
    // AI 指令标记：<指令>...</指令> 或 【指令】...【/指令】
    private static final Pattern CMD_PATTERN = Pattern.compile("<指令>([^<]+)</指令>");
    private static final Pattern CMD_PATTERN_ALT = Pattern.compile("【指令】([^【]+)【/指令】");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private final Gson gson = new Gson();

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;
    private static final int INPUT_H = 24;
    private static final int MSG_AREA_TOP = 32;
    private static final int LINE_HEIGHT = 12;
    private static final int SIDE_BTN_W = 40;
    private static final int SIDE_BTN_H = 24;
    private static final int SIDE_BTN_GAP = 4;

    public CustomerChatScreen(String npcName, int entityId, String playerUuid, boolean mapGroupMember,
                               int unreadMailCount, int unfilledQuestionnaireCount) {
        super(Component.literal(npcName));
        this.npcName = npcName;
        this.entityId = entityId;
        this.playerUuid = playerUuid;
        this.mapGroupMember = mapGroupMember;
        // 地图组指令提示优先
        if (mapGroupMember) {
            addMessage(npcName, "您好，地图组成员！您可以使用 /playnpc ditu <creative/adventure> 指令随时切换创造或冒险模式，您也可以使用按键 X 随时打开传送面板进行传送！");
        }
        // 投稿宣传（地图组提示之后、未读事项之前）
        addMessage(npcName, "点击右侧投稿可以进行角色以及修饰符的投稿，有机会将你的创意制作到游戏中，同时会以每两周为一期进行投稿点赞，获得点赞数最高的三个作品将得到丰厚的奖励。");
        // 未读通知（邮件 → 问卷）
        if (unreadMailCount > 0) {
            addMessage(npcName, "您有" + unreadMailCount + "封未读邮件！");
        }
        if (unfilledQuestionnaireCount > 0) {
            addMessage(npcName, "您有" + unfilledQuestionnaireCount + "个未填问卷！");
        }
    }

    /**
     * 兼容旧调用的双参数构造器，自动从当前玩家获取 UUID 作为 Coze 的 user_id。
     */
    public CustomerChatScreen(String npcName, int entityId) {
        this(npcName, entityId, getLocalPlayerUuid(), false, 0, 0);
    }

    private static String getLocalPlayerUuid() {
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getUUID().toString();
        }
        return "";
    }

    @Override
    protected void init() {
        super.init();
        currentScreen = this;

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        int inputY = py + PANEL_H - INPUT_H - 8;
        int sendW = 44;
        int sideX = px + PANEL_W - SIDE_BTN_W - 8;
        int inputW = sideX - px - 10 - sendW - 8;

        this.inputBox = new EditBox(
            this.font,
            px + 10,
            inputY,
            inputW,
            INPUT_H,
            Component.literal("")
        );
        this.inputBox.setMaxLength(256);
        this.inputBox.setFocused(true);
        this.addRenderableWidget(this.inputBox);

        int sendX = px + 10 + inputW + 4;
        this.addRenderableWidget(new NoShadowButton(sendX, inputY, sendW, INPUT_H,
            Component.literal("发送"), btn -> sendMessage()));

        int sideTopY = py + 40;
        this.teleportButton = new NoShadowButton(sideX, sideTopY, SIDE_BTN_W, SIDE_BTN_H,
            Component.literal("传送"), btn -> openTeleport());
        this.addRenderableWidget(this.teleportButton);

        this.feedbackButton = new NoShadowButton(sideX, sideTopY + SIDE_BTN_H + SIDE_BTN_GAP, SIDE_BTN_W, SIDE_BTN_H,
            Component.literal("反馈"), btn -> openFeedback());
        this.addRenderableWidget(this.feedbackButton);

        this.questionnaireButton = new NoShadowButton(sideX, sideTopY + (SIDE_BTN_H + SIDE_BTN_GAP) * 2, SIDE_BTN_W, SIDE_BTN_H,
            Component.literal("问卷"), btn -> openQuestionnaire());
        this.addRenderableWidget(this.questionnaireButton);

        this.mailButton = new NoShadowButton(sideX, sideTopY + (SIDE_BTN_H + SIDE_BTN_GAP) * 3, SIDE_BTN_W, SIDE_BTN_H,
            Component.literal("邮箱"), btn -> openMail());
        this.addRenderableWidget(this.mailButton);

        this.contributionButton = new NoShadowButton(sideX, sideTopY + (SIDE_BTN_H + SIDE_BTN_GAP) * 4, SIDE_BTN_W, SIDE_BTN_H,
            Component.literal("投稿"), btn -> openContribution());
        this.addRenderableWidget(this.contributionButton);

        if (mapGroupMember) {
            this.mapModeButton = new NoShadowButton(sideX, sideTopY + (SIDE_BTN_H + SIDE_BTN_GAP) * 5, SIDE_BTN_W, SIDE_BTN_H,
                Component.literal("地图模式"), btn -> enterMapMode());
            this.addRenderableWidget(this.mapModeButton);
        }
    }

    @Override
    public void onClose() {
        if (currentScreen == this) {
            currentScreen = null;
        }
        super.onClose();
    }

    /** AI指令执行结果：对话界面打开时显示在界面内，否则返回 false 由调用方落到游戏聊天 */
    public static boolean showCommandResult(String message) {
        if (currentScreen == null) {
            return false;
        }
        currentScreen.addMessage("系统", message);
        return true;
    }

    private void openContribution() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ContributionCategoryScreen(entityId, npcName, this));
        }
    }

    private void openTeleport() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TeleportCategoryScreen(entityId, npcName, this));
        }
    }

    private void openFeedback() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new FeedbackScreen(entityId, npcName));
        }
    }

    private void openQuestionnaire() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new PlayerQuestionnaireScreen(entityId, npcName));
        }
    }

    private void openMail() {
        ClientPlayNetworking.send(new com.kghua.npcai.network.OpenMailboxPacket());
    }

    private void enterMapMode() {
        // 对局进行中禁止地图模式（NPC管理员豁免），防止游戏中切换创造模式
        if (com.kghua.npcai.client.ClientCache.isGameInProgress()
            && !com.kghua.npcai.client.ClientCache.isNpcAdmin()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c游戏中无法使用传送"));
            }
            return;
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand("playnpc ditu creative");
        }
        openTeleport();
    }

    private void sendMessage() {
        String msg = inputBox.getValue().trim();
        if (msg.isEmpty()) {
            return;
        }
        // yes 确认执行待定指令：发送给服务端执行，不再转发给 AI
        if (msg.equalsIgnoreCase("yes") && pendingCommand != null) {
            String cmd = pendingCommand;
            pendingCommand = null;
            addMessage("你", msg);
            inputBox.setValue("");
            ClientPlayNetworking.send(new ExecuteAiCommandPacket(cmd));
            addMessage("系统", "已请求执行指令：" + cmd);
            return;
        }
        // 玩家提出了新问题，作废之前的待确认指令
        pendingCommand = null;

        addMessage("你", msg);
        inputBox.setValue("");

        // 先显示“思考中...”占位
        ChatMessage pending = new ChatMessage(npcName, "思考中...");
        messages.add(pending);

        callAiAsync(msg, pending);
    }

    private void callAiAsync(String question, ChatMessage pending) {
        // 注入指令生成规则（按玩家身份），引导 AI 生成指令并提供 yes 确认流程
        String fullQuestion = buildCommandRulePrefix() + question;
        String provider = NpcAiClientConfig.getProvider();
        if ("coze".equals(provider)) {
            callCozeAsync(fullQuestion, pending);
        } else {
            callRagAsync(fullQuestion, pending);
        }
    }

    /** 根据玩家身份构造 AI 指令生成规则 */
    private String buildCommandRulePrefix() {
        boolean admin = ClientCache.isOp() || ClientCache.isNpcAdmin();
        String roleRule;
        if (admin) {
            roleRule = "你可以生成任意指令，并在指令后说明该指令的作用。";
        } else if (mapGroupMember) {
            roleRule = "你只能生成消息类指令与模式修改指令：消息类指令为 say、tell、me、teammsg、tellraw、title（可用 execute as 玩家名 run 消息指令 的形式让指定玩家发言）；模式修改指令为 playnpc ditu creative 或 playnpc ditu adventure。";
        } else {
            roleRule = "你只能生成消息类指令：say、tell、me、teammsg、tellraw、title（可用 execute as 玩家名 run 消息指令 的形式让指定玩家发言，例如玩家说“让11发送123”，应生成 execute as 11 run say 123）。";
        }
        return "【指令生成规则】当玩家请求执行游戏指令时：1. 先识别玩家意图，生成对应的Minecraft指令；"
            + "2. 指令必须单独一行并用<指令>与</指令>包裹，一次只生成一条指令，例如：<指令>execute as 11 run say 123</指令>；"
            + "3. " + roleRule
            + "4. 绝对不要生成上述范围之外的任何指令；"
            + "5. 生成指令后不要自行执行，提示玩家：如果需要执行该指令请输入“yes”确认执行。"
            + "如果玩家没有请求执行指令，直接正常回答。\n\n";
    }

    /**
     * 调用本地 RAG 服务（兼容原有逻辑）。
     */
    private void callRagAsync(String question, ChatMessage pending) {
        CompletableFuture.supplyAsync(() -> {
            String rawUrl = NpcAiClientConfig.getAiApiUrl();
            String url = normalizeRagUrl(rawUrl);

            try {
                JsonObject body = new JsonObject();
                body.addProperty("message", question);
                body.add("history", buildHistoryJson());
                body.addProperty("npc_display_name", npcName);
                String bodyJson = gson.toJson(body);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "NpcAiMod/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    return "[ERR] AI 服务异常，地址：" + url + "，状态码：" + response.statusCode() + "，响应：" + truncate(response.body(), 256);
                }
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json == null || !json.has("reply")) {
                    return "[ERR] AI 返回格式异常，响应：" + truncate(response.body(), 256);
                }
                return json.get("reply").getAsString();
            } catch (Exception e) {
                return "[ERR] 请求 AI 失败：" + e.getClass().getSimpleName() + ": " + e.getMessage() + " (地址：" + url + ")";
            }
        }).thenAccept(reply -> updatePending(pending, reply));
    }

    private String normalizeRagUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        String trimmed = url.trim();
        if (trimmed.endsWith("/api/chat")) return trimmed;
        if (trimmed.endsWith("/")) return trimmed + "api/chat";
        return trimmed + "/api/chat";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    private void logToChat(String text) {
        if (Minecraft.getInstance() != null) {
            Minecraft.getInstance().execute(() -> addMessage("系统", text));
        }
    }

    /**
     * 调用 Coze（扣子）/v3/chat API，带简单轮询兜底。
     */
    private void callCozeAsync(String question, ChatMessage pending) {
        CompletableFuture.supplyAsync(() -> {
            try {
                String token = NpcAiClientConfig.getCozeToken();
                String botId = NpcAiClientConfig.getCozeBotId();
                if (token.isEmpty() || botId.isEmpty()) {
                    return "[ERR] Coze 配置不完整，请先填写 coze_token 和 coze_bot_id";
                }

                JsonObject body = new JsonObject();
                body.addProperty("bot_id", botId);
                body.addProperty("user_id", playerUuid);
                body.addProperty("stream", false);
                body.addProperty("auto_save_history", true);
                body.add("additional_messages", buildCozeMessages(question));

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NpcAiClientConfig.getCozeApiUrl()))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    return "[ERR] Coze 服务异常，状态码：" + response.statusCode();
                }

                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json == null || !json.has("data")) {
                    return "[ERR] Coze 返回格式异常";
                }
                JsonObject data = json.getAsJsonObject("data");

                // 如果返回里已经有 assistant answer，直接取
                String immediate = extractAnswerFromData(data);
                if (immediate != null && !immediate.isEmpty()) {
                    return immediate;
                }

                // 否则轮询等待完成
                return pollAndFetchAnswer(data);
            } catch (Exception e) {
                return "[ERR] 请求 Coze 失败：" + e.getMessage();
            }
        }).thenAccept(reply -> updatePending(pending, reply));
    }

    /**
     * 从 Coze data 对象里直接提取 answer 内容。
     */
    private String extractAnswerFromData(JsonObject data) {
        if (!data.has("messages") || !data.get("messages").isJsonArray()) {
            return null;
        }
        JsonArray arr = data.getAsJsonArray("messages");
        for (int i = arr.size() - 1; i >= 0; i--) {
            JsonObject msg = arr.get(i).getAsJsonObject();
            if ("assistant".equals(getString(msg, "role")) && "answer".equals(getString(msg, "type"))) {
                return getString(msg, "content");
            }
        }
        return null;
    }

    /**
     * 轮询 /v3/chat/retrieve，完成后调用 /v3/chat/message/list 获取最终结果。
     */
    private String pollAndFetchAnswer(JsonObject initialData) throws Exception {
        String chatId = getString(initialData, "id");
        String conversationId = getString(initialData, "conversation_id");
        String status = getString(initialData, "status");
        if (chatId == null || chatId.isEmpty() || conversationId == null || conversationId.isEmpty()) {
            return "[ERR] Coze 返回缺少 chat_id 或 conversation_id";
        }

        String baseUrl = getCozeBaseUrl();
        String token = NpcAiClientConfig.getCozeToken();

        long deadline = System.currentTimeMillis() + 30_000;
        while (!isCompletedOrFailed(status) && System.currentTimeMillis() < deadline) {
            Thread.sleep(1_000);
            String retrieveUrl = baseUrl + "/v3/chat/retrieve?" +
                "conversation_id=" + urlEncode(conversationId) +
                "&chat_id=" + urlEncode(chatId);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(retrieveUrl))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
                if (json != null && json.has("data")) {
                    JsonObject data = json.getAsJsonObject("data");
                    status = getString(data, "status");
                    String immediate = extractAnswerFromData(data);
                    if (immediate != null && !immediate.isEmpty()) {
                        return immediate;
                    }
                }
            }
        }

        if (!"completed".equalsIgnoreCase(status)) {
            return "[ERR] Coze 对话未在 30 秒内完成，当前状态：" + status;
        }

        String listUrl = baseUrl + "/v3/chat/message/list?" +
            "conversation_id=" + urlEncode(conversationId) +
            "&chat_id=" + urlEncode(chatId);
        HttpRequest listReq = HttpRequest.newBuilder()
            .uri(URI.create(listUrl))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .GET()
            .build();
        HttpResponse<String> listResp = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (listResp.statusCode() != 200) {
            return "[ERR] Coze 获取消息列表失败，状态码：" + listResp.statusCode();
        }

        JsonObject listJson = gson.fromJson(listResp.body(), JsonObject.class);
        if (listJson == null || !listJson.has("data")) {
            return "[ERR] Coze 消息列表格式异常";
        }

        JsonArray arr = listJson.getAsJsonArray("data");
        for (int i = arr.size() - 1; i >= 0; i--) {
            JsonObject msg = arr.get(i).getAsJsonObject();
            if ("assistant".equals(getString(msg, "role")) && "answer".equals(getString(msg, "type"))) {
                return getString(msg, "content");
            }
        }
        return "[ERR] Coze 未返回有效回答";
    }

    private boolean isCompletedOrFailed(String status) {
        if (status == null) return false;
        String s = status.toLowerCase();
        return s.equals("completed") || s.equals("failed") || s.equals("require_action");
    }

    private String getCozeBaseUrl() {
        String url = NpcAiClientConfig.getCozeApiUrl();
        int idx = url.lastIndexOf("/v3/chat");
        if (idx > 0) {
            return url.substring(0, idx);
        }
        idx = url.lastIndexOf('/');
        return idx > 0 ? url.substring(0, idx) : url;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 构造 Coze additional_messages：历史 6 轮（最多 12 条）+ 当前问题。
     */
    private JsonArray buildCozeMessages(String currentQuestion) {
        JsonArray result = new JsonArray();
        List<ChatMessage> history = buildFilteredHistory();
        for (ChatMessage msg : history) {
            JsonObject turn = new JsonObject();
            turn.addProperty("role", msg.sender.equals("你") ? "user" : "assistant");
            turn.addProperty("content", msg.text);
            turn.addProperty("content_type", "text");
            result.add(turn);
        }
        JsonObject current = new JsonObject();
        current.addProperty("role", "user");
        current.addProperty("content", currentQuestion);
        current.addProperty("content_type", "text");
        result.add(current);
        return result;
    }

    /**
     * 构造本地 RAG 用的 history JSON。
     */
    private com.google.gson.JsonArray buildHistoryJson() {
        com.google.gson.JsonArray history = new com.google.gson.JsonArray();
        for (ChatMessage msg : buildFilteredHistory()) {
            JsonObject turn = new JsonObject();
            turn.addProperty("role", msg.sender.equals("你") ? "user" : "assistant");
            turn.addProperty("content", msg.text);
            history.add(turn);
        }
        return history;
    }

    /**
     * 返回最近最多 12 条有效历史消息，过滤错误占位和“思考中...”。
     */
    private List<ChatMessage> buildFilteredHistory() {
        List<ChatMessage> filtered = new ArrayList<>();
        int start = Math.max(0, messages.size() - 12);
        for (int i = start; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg.text.startsWith("[ERR]") || msg.text.equals("思考中...")) {
                continue;
            }
            filtered.add(msg);
        }
        return filtered;
    }

    private void updatePending(ChatMessage pending, String reply) {
        if (Minecraft.getInstance() != null) {
            Minecraft.getInstance().execute(() -> {
                pending.text = reply;
                applyCommandExtraction(pending);
                scrollToBottom = true;
            });
        }
    }

    /** 从 AI 回复中提取指令：存入待确认指令，并在消息中高亮指令行 */
    private void applyCommandExtraction(ChatMessage msg) {
        String command = extractCommand(msg.text);
        if (command == null || command.isEmpty()) {
            return;
        }
        msg.command = command;
        pendingCommand = command;
        // 移除标记，保留 AI 的说明文字
        msg.text = msg.text
            .replaceAll("<指令>[^<]*</指令>", "")
            .replaceAll("【指令】[^【]*【/指令】", "")
            .trim();
    }

    /** 从文本中提取指令（支持 <指令>...</指令> 与 【指令】...【/指令】 两种标记） */
    private String extractCommand(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = CMD_PATTERN.matcher(text);
        if (m.find()) {
            String cmd = m.group(1).trim();
            return cmd.isEmpty() ? null : cmd;
        }
        Matcher m2 = CMD_PATTERN_ALT.matcher(text);
        if (m2.find()) {
            String cmd = m2.group(1).trim();
            return cmd.isEmpty() ? null : cmd;
        }
        return null;
    }

    public void addMessage(String sender, String text) {
        messages.add(new ChatMessage(sender, text));
        scrollToBottom = true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 /* Enter */ && inputBox.isFocused()) {
            sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        // 标题居中显示在面板顶部
        Component title = Component.literal("与 " + npcName + " 对话中");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, px + (PANEL_W - titleWidth) / 2, py + 8, 0xFFFFFFFF, false);

        // 消息历史区域（右侧留出按钮空间，底部留出待确认指令状态行）
        int msgTop = py + MSG_AREA_TOP;
        int msgBottom = py + PANEL_H - 44;
        int sideX = px + PANEL_W - SIDE_BTN_W - 8;
        renderMessages(graphics, px + 10, msgTop, sideX - px - 16, msgBottom - msgTop, mouseX, mouseY);

        // 待确认指令状态行（输入框上方）
        if (pendingCommand != null) {
            String status = "待确认指令：" + pendingCommand;
            int maxStatusW = sideX - px - 16;
            if (this.font.width(status) > maxStatusW) {
                while (!status.isEmpty() && this.font.width(status + "…") > maxStatusW) {
                    status = status.substring(0, status.length() - 1);
                }
                status += "…";
            }
            graphics.drawString(this.font, Component.literal("§e" + status), px + 10, py + PANEL_H - 43, 0xFFCC00, false);
        }
        // 复制成功提示
        if (System.currentTimeMillis() < copyHintUntil) {
            graphics.drawString(this.font, Component.literal("§a已复制到剪贴板"), px + 10, py + 22, 0xFF55FF55, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderMessages(GuiGraphics graphics, int x, int y, int maxW, int maxH, int mouseX, int mouseY) {
        int lineHeight = this.font.lineHeight + 2;
        int spacing = 4;
        int endY = y + maxH;
        messageBounds.clear();

        // 先计算总高度（含指令行与确认行），用于滚动范围
        double totalH = 0;
        for (ChatMessage msg : messages) {
            totalH += blockHeight(msg, maxW, lineHeight) + spacing;
        }
        totalMessageHeight = totalH;
        if (totalH <= maxH) {
            messageScroll = 0;
        } else {
            messageScroll = Math.max(0, Math.min(messageScroll, totalH - maxH));
        }
        if (scrollToBottom) {
            messageScroll = Math.max(0, totalH - maxH);
            scrollToBottom = false;
        }

        // 绘制（从滚动偏移开始）
        int currentY = y - (int) messageScroll;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            int blockH = blockHeight(msg, maxW, lineHeight);
            if (currentY + blockH >= y && currentY <= endY) {
                // 悬停高亮，提示可点击复制
                if (mouseX >= x && mouseX <= x + maxW && mouseY >= currentY && mouseY < currentY + blockH) {
                    graphics.fill(x - 2, currentY, x + maxW + 2, currentY + blockH, 0x30FFFFFF);
                }
                drawMessageBlock(graphics, msg, x, currentY, maxW, lineHeight);
            }
            messageBounds.add(new int[]{x - 2, currentY, maxW + 4, blockH});
            currentY += blockH + spacing;
        }
    }

    /** 单条消息块高度：文本行 + （指令行 + 确认提示行） */
    private int blockHeight(ChatMessage msg, int maxW, int lineHeight) {
        int textH = wrapText(msg.text, textWidth(maxW, msg.sender)).size() * lineHeight;
        if (msg.command == null) {
            return textH;
        }
        int cmdH = wrapText("指令：" + msg.command, maxW).size() * lineHeight;
        return textH + cmdH + lineHeight;
    }

    private void drawMessageBlock(GuiGraphics graphics, ChatMessage msg, int x, int y, int maxW, int lineHeight) {
        String prefix = msg.sender.equals("你") ? "你：" : msg.sender + "：";
        int prefixWidth = this.font.width(prefix);
        int textMaxW = maxW - prefixWidth - 4;
        List<String> lines = wrapText(msg.text, textMaxW);

        // 第一行带发送者前缀
        if (!lines.isEmpty()) {
            int color = msg.sender.equals("你") ? 0xFF4A9EFF : 0xFFFFFFFF;
            graphics.drawString(this.font, Component.literal(prefix), x, y, 0xFFAAAAAA, false);
            graphics.drawString(this.font, Component.literal(lines.get(0)), x + prefixWidth + 2, y, color, false);
            y += lineHeight;
        }
        // 后续行缩进对齐
        for (int j = 1; j < lines.size(); j++) {
            graphics.drawString(this.font, Component.literal(lines.get(j)), x + prefixWidth + 2, y, 0xFFFFFFFF, false);
            y += lineHeight;
        }
        // 指令行：金色高亮，方便查找与复制
        if (msg.command != null) {
            List<String> cmdLines = wrapText("指令：" + msg.command, maxW);
            for (String line : cmdLines) {
                graphics.drawString(this.font, Component.literal(line), x + prefixWidth + 2, y, 0xFFCC00, false);
                y += lineHeight;
            }
            // 确认提示行
            graphics.drawString(this.font, Component.literal("如果需要执行该指令请输入“yes”确认执行"),
                x + prefixWidth + 2, y, 0xFF55FF55, false);
        }
    }

    private int textWidth(int maxW, String sender) {
        String prefix = sender.equals("你") ? "你：" : sender + "：";
        return maxW - this.font.width(prefix) - 4;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }
        // 使用 Minecraft 字体实际宽度换行
        StringBuilder current = new StringBuilder();
        int width = 0;
        for (char c : text.toCharArray()) {
            int charWidth = this.font.width(String.valueOf(c));
            if (width + charWidth > maxWidth && current.length() > 0) {
                result.add(current.toString());
                current = new StringBuilder();
                width = 0;
            }
            current.append(c);
            width += charWidth;
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }



    private boolean isMouseOver(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 点击消息选取并复制：有指令优先复制指令（方便查找），否则复制整条消息 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int py = (this.height - PANEL_H) / 2;
            int msgTop = py + MSG_AREA_TOP;
            int msgBottom = py + PANEL_H - 44;
            if (mouseY >= msgTop && mouseY <= msgBottom) {
                for (int i = messageBounds.size() - 1; i >= 0; i--) {
                    int[] b = messageBounds.get(i);
                    if (mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                        ChatMessage msg = messages.get(i);
                        String copyText = msg.command != null ? msg.command : msg.text;
                        if (this.minecraft != null) {
                            this.minecraft.keyboardHandler.setClipboard(copyText);
                            copyHintUntil = System.currentTimeMillis() + 2000;
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 消息区滚轮滚动 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int py = (this.height - PANEL_H) / 2;
        int msgTop = py + MSG_AREA_TOP;
        int msgBottom = py + PANEL_H - 44;
        if (mouseY >= msgTop && mouseY <= msgBottom) {
            if (totalMessageHeight > msgBottom - msgTop) {
                messageScroll = Math.max(0,
                    Math.min(messageScroll - scrollY * 12, totalMessageHeight - (msgBottom - msgTop)));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static class ChatMessage {
        String sender;
        String text;
        String command; // 从 AI 回复中提取出的待执行指令（无则为 null）

        ChatMessage(String sender, String text) {
            this(sender, text, null);
        }

        ChatMessage(String sender, String text, String command) {
            this.sender = sender;
            this.text = text;
            this.command = command;
        }
    }
}
