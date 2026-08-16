package com.kghua.npcai.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.kghua.npcai.network.RequestTitlePacket;
import com.kghua.npcai.network.SaveTitlePacket;
import com.kghua.npcai.network.SyncTitlePacket;
import com.kghua.npcai.server.TitleManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 称号设置页。
 * 顶部：当前称号展示区（只展示名称+样式，非编辑）；下方：简单/复杂模式切换编辑；
 * 最底部：确认（点确认才生效）/取消（未确认退出 = 不修改）。
 * 填空确认 = 不想要称号 = 清除（不加入队伍）。
 * 字数规则：最多 6 个汉字或 12 个字母，两个字母或字符算一个字（【】不计）。
 */
public class TitleScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 300;
    private static final int MAX_UNITS = 6;

    private final Screen parentScreen;

    private int mode = 0; // 0=简单 1=复杂
    private EditBox simpleTitleBox;
    private EditBox complexJsonBox;
    private Button simpleModeBtn;
    private Button complexModeBtn;
    private Button titleColorBtn;
    private Button frameColorBtn;
    private Button nameColorBtn;
    private int titleColorIdx = 0;
    private int frameColorIdx = 0;
    private int nameColorIdx = 0;

    // 当前称号展示（来自服务端 SyncTitlePacket）
    private String displayPrefixJson = "";
    private String nameColorName = "";

    public TitleScreen(Screen parentScreen) {
        super(Component.literal("称号"));
        this.parentScreen = parentScreen;
    }

    private void backToParent() {
        if (this.minecraft != null && parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            onClose();
        }
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int fieldX = px + 100;
        int fieldW = PANEL_W - 110;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        // 简单模式：称号名称 / 称号颜色 / 名字颜色
        this.simpleTitleBox = new EditBox(this.font, fieldX, py + 104, fieldW, 16, Component.literal(""));
        this.simpleTitleBox.setMaxLength(12); // 12 字母=6 字（字数上限由确认校验把关）
        this.simpleTitleBox.setHint(Component.literal("填写称号名称"));
        this.addRenderableWidget(this.simpleTitleBox);

        this.titleColorBtn = new NoShadowButton(fieldX, py + 130, fieldW, 16,
            Component.literal(""), btn -> cycleColor(0));
        this.addRenderableWidget(this.titleColorBtn);

        // 名字颜色两模式共用（位置重合）
        this.nameColorBtn = new NoShadowButton(fieldX, py + 156, fieldW, 16,
            Component.literal(""), btn -> cycleColor(1));
        this.addRenderableWidget(this.nameColorBtn);

        // 复杂模式：称号指令 JSON / 方框颜色 / 名字颜色
        this.complexJsonBox = new EditBox(this.font, fieldX, py + 104, fieldW, 16, Component.literal(""));
        this.complexJsonBox.setMaxLength(200);
        this.complexJsonBox.setHint(Component.literal("{\"text\":\"第一个字\",\"color\":\"颜色\"}"));
        this.addRenderableWidget(this.complexJsonBox);

        this.frameColorBtn = new NoShadowButton(fieldX, py + 130, fieldW, 16,
            Component.literal(""), btn -> cycleColor(2));
        this.addRenderableWidget(this.frameColorBtn);

        // 模式切换按钮
        this.simpleModeBtn = new NoShadowButton(px + 70, py + 76, 90, 16,
            Component.literal(""), btn -> setMode(0));
        this.addRenderableWidget(this.simpleModeBtn);
        this.complexModeBtn = new NoShadowButton(px + 200, py + 76, 90, 16,
            Component.literal(""), btn -> setMode(1));
        this.addRenderableWidget(this.complexModeBtn);

        // 底部固定：确认 / 取消
        this.addRenderableWidget(new NoShadowButton(px + 100, py + PANEL_H - 32, 80, 20,
            Component.literal("确认"), btn -> confirm()));
        this.addRenderableWidget(new NoShadowButton(px + 200, py + PANEL_H - 32, 60, 20,
            Component.literal("取消"), btn -> backToParent()));

        setMode(0);
        refreshColorButtons();

        // 打开即拉取当前称号状态
        ClientPlayNetworking.send(new RequestTitlePacket());
    }

    // ===== 交互 =====

    private void setMode(int m) {
        this.mode = m;
        this.simpleTitleBox.visible = (m == 0);
        this.titleColorBtn.visible = (m == 0);
        this.complexJsonBox.visible = (m == 1);
        this.frameColorBtn.visible = (m == 1);
        this.nameColorBtn.visible = true; // 两模式共用
        this.simpleModeBtn.setMessage(Component.literal(m == 0 ? "§a✓简单模式" : "简单模式"));
        this.complexModeBtn.setMessage(Component.literal(m == 1 ? "§a✓复杂模式" : "复杂模式"));
    }

    /** 0=称号颜色 1=名字颜色 2=方框颜色：点击循环切换 16 色 */
    private void cycleColor(int which) {
        if (which == 0) titleColorIdx = (titleColorIdx + 1) % TitleManager.COLOR_NAMES.length;
        else if (which == 1) nameColorIdx = (nameColorIdx + 1) % TitleManager.COLOR_NAMES.length;
        else frameColorIdx = (frameColorIdx + 1) % TitleManager.COLOR_NAMES.length;
        refreshColorButtons();
    }

    private void refreshColorButtons() {
        titleColorBtn.setMessage(colorButtonText("称号颜色", titleColorIdx));
        frameColorBtn.setMessage(colorButtonText("方框颜色", frameColorIdx));
        nameColorBtn.setMessage(colorButtonText("名字颜色", nameColorIdx));
    }

    /** 「称号颜色：」白色标签 + 中文色名（自带颜色样式） */
    private MutableComponent colorButtonText(String label, int idx) {
        ChatFormatting cf = ChatFormatting.getByName(TitleManager.COLOR_NAMES[idx]);
        return Component.literal(label + "：").withStyle(ChatFormatting.WHITE)
            .append(Component.literal(TitleManager.COLOR_CN[idx])
                .withStyle(cf != null ? cf : ChatFormatting.WHITE));
    }

    private void confirm() {
        if (mode == 0) {
            String title = simpleTitleBox.getValue().trim();
            if (TitleManager.countUnits(title) > MAX_UNITS) {
                sendHint("§c称号过长（最多6个汉字或12个字母，两个字母或字符算一个字）");
                return;
            }
            // 称号为空也照发 = 清除称号
            ClientPlayNetworking.send(new SaveTitlePacket(0, title,
                TitleManager.COLOR_NAMES[titleColorIdx], "",
                "", TitleManager.COLOR_NAMES[nameColorIdx]));
        } else {
            String json = complexJsonBox.getValue().trim();
            if (!json.isEmpty()) {
                String err = validateComplexJson(json);
                if (err != null) {
                    sendHint(err);
                    return;
                }
            }
            // JSON 为空也照发 = 清除称号
            ClientPlayNetworking.send(new SaveTitlePacket(1, "", "",
                json, TitleManager.COLOR_NAMES[frameColorIdx],
                TitleManager.COLOR_NAMES[nameColorIdx]));
        }
        backToParent();
    }

    /** 客户端预校验复杂模式 JSON（与服务端一致；通过后才发包） */
    private static String validateComplexJson(String raw) {
        if (raw.length() > 200) return "§cJSON过长（最多200字符）";
        String wrapped = raw.startsWith("[") ? raw : "[" + raw + "]";
        JsonElement el;
        try {
            el = JsonParser.parseString(wrapped);
        } catch (JsonSyntaxException e) {
            return "§cJSON格式错误";
        }
        if (!el.isJsonArray()) return "§cJSON必须是对象数组";
        JsonArray arr = el.getAsJsonArray();
        if (arr.isEmpty() || arr.size() > 6) return "§c元素数量需在1-6之间";
        int units = 0;
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) return "§c每个元素必须是JSON对象";
            JsonObject o = e.getAsJsonObject();
            if (!o.has("text") || !o.get("text").isJsonPrimitive()
                || !o.get("text").getAsJsonPrimitive().isString()) {
                return "§c每个元素必须含 text 字符串";
            }
            for (String key : o.keySet()) {
                if (!key.equals("text") && !key.equals("color")) {
                    return "§c只允许 text 和 color 字段";
                }
            }
            units += TitleManager.countUnits(o.get("text").getAsString());
        }
        if (units > MAX_UNITS) {
            return "§c称号过长（最多6个汉字或12个字母，两个字母或字符算一个字，【】不计）";
        }
        return null;
    }

    /** 服务端 SyncTitlePacket 到达：预填编辑框 + 复位颜色选择 + 刷新当前称号展示 */
    public void applySync(SyncTitlePacket p) {
        if (simpleTitleBox == null) return; // 页面尚未 init（网络先于界面）
        setMode(p.mode() == 1 ? 1 : 0);
        simpleTitleBox.setValue(p.simpleTitle() == null ? "" : p.simpleTitle());
        complexJsonBox.setValue(p.complexPrefixJson() == null ? "" : p.complexPrefixJson());
        titleColorIdx = indexOfColor(p.titleColor());
        frameColorIdx = indexOfColor(p.frameColor());
        nameColorIdx = indexOfColor(p.nameColor());
        displayPrefixJson = p.displayPrefixJson() == null ? "" : p.displayPrefixJson();
        nameColorName = p.nameColorName() == null ? "" : p.nameColorName();
        refreshColorButtons();
    }

    private static int indexOfColor(String name) {
        if (name == null) return 0;
        for (int i = 0; i < TitleManager.COLOR_NAMES.length; i++) {
            if (TitleManager.COLOR_NAMES[i].equals(name)) return i;
        }
        return 0;
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        TrainStyleRenderHelper.renderPanelTitle(graphics, this.font, "称号", px + 4, py + 12, PANEL_W - 8);

        // 当前称号展示卡片（固定，非编辑）
        int cardY = py + 32;
        TrainStyleRenderHelper.renderCard(graphics, px + 10, cardY, PANEL_W - 20, 34);
        drawCurrentTitle(graphics, px + 16, cardY + 10, px + 14, px + PANEL_W - 14);

        // 内容区结界
        graphics.enableScissor(px + 4, py + 24, px + PANEL_W - 4, py + PANEL_H - 4);

        // 复杂模式：称号指令未填时显示填写格式提示（一行）
        if (mode == 1 && complexJsonBox.getValue().trim().isEmpty()) {
            graphics.drawString(this.font, Component.literal(
                "§7填写格式：{\"text\":\"第一个字\",\"color\":\"颜色\"},{\"text\":\"第二个字\",\"color\":\"颜色\"}...（以此类推）"),
                px + 100, py + 122, 0xFFAAAAAA, false);
        }

        graphics.disableScissor();

        super.render(graphics, mouseX, mouseY, delta);
    }

    /** 当前称号：彩色【称号】+ 玩家名（名字颜色）；无称号显示「无称号」 */
    private void drawCurrentTitle(GuiGraphics graphics, int x, int y, int clipLeft, int clipRight) {
        String playerName = (this.minecraft != null && this.minecraft.player != null)
            ? this.minecraft.player.getScoreboardName() : "玩家";
        MutableComponent label = Component.literal("当前称号：").withStyle(ChatFormatting.GRAY);
        graphics.enableScissor(clipLeft, y - 2, clipRight, y + 12);
        if (displayPrefixJson.isEmpty()) {
            graphics.drawString(this.font,
                label.append(Component.literal("无称号").withStyle(ChatFormatting.DARK_GRAY)),
                x, y, 0xFFFFFFFF, false);
        } else {
            try {
                HolderLookup.Provider provider = (this.minecraft != null && this.minecraft.level != null)
                    ? this.minecraft.level.registryAccess() : null;
                Component prefix = Component.Serializer.fromJson(displayPrefixJson, provider);
                ChatFormatting nc = ChatFormatting.getByName(nameColorName);
                MutableComponent name = Component.literal(playerName)
                    .withStyle(nc != null && nc.isColor() ? nc : ChatFormatting.WHITE);
                graphics.drawString(this.font, label.append(prefix).append(name), x, y, 0xFFFFFFFF, false);
            } catch (Exception e) {
                graphics.drawString(this.font,
                    label.append(Component.literal("无称号").withStyle(ChatFormatting.DARK_GRAY)),
                    x, y, 0xFFFFFFFF, false);
            }
        }
        graphics.disableScissor();
    }

    private void sendHint(String msg) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal(msg));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
