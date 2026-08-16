package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.FeedbackEntry;
import com.kghua.npcai.network.ExportFeedbackPacket;
import com.kghua.npcai.network.RequestFeedbackPacket;
import com.kghua.npcai.network.SyncFeedbackPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 反馈设置页面：按时间范围筛选并导出反馈。
 */
public class FeedbackSettingsScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 260;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private EditBox startBox;
    private EditBox endBox;
    private final List<FeedbackEntry> entries = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private double scrollOffset = 0;

    private static final int CARD_H = 44;
    private static final int CARD_GAP = 4;

    public FeedbackSettingsScreen() {
        super(Component.literal("反馈设置"));
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        LocalDateTime now = LocalDateTime.now();
        this.startBox = new EditBox(this.font, px + 50, py + 28, 90, 16, Component.literal(""));
        this.startBox.setValue(now.minusDays(7).format(FORMATTER));
        this.addRenderableWidget(this.startBox);

        this.endBox = new EditBox(this.font, px + 180, py + 28, 90, 16, Component.literal(""));
        this.endBox.setValue(now.format(FORMATTER));
        this.addRenderableWidget(this.endBox);

        this.addRenderableWidget(new NoShadowButton(px + 50, py + 50, 50, 18,
            Component.literal("查询"), btn -> requestFeedback()));
        this.addRenderableWidget(new NoShadowButton(px + 110, py + 50, 50, 18,
            Component.literal("全选"), btn -> toggleSelectAll()));
        this.addRenderableWidget(new NoShadowButton(px + 170, py + 50, 50, 18,
            Component.literal("导出"), btn -> exportSelected()));
        this.addRenderableWidget(new NoShadowButton(px + 230, py + 50, 50, 18,
            Component.literal("返回"), btn -> onClose()));
    }

    private void requestFeedback() {
        long start = parseTime(startBox.getValue().trim());
        long end = parseTime(endBox.getValue().trim());
        ClientPlayNetworking.send(new RequestFeedbackPacket(start, end));
    }

    private long parseTime(String text) {
        try {
            return LocalDateTime.parse(text, FORMATTER).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private void toggleSelectAll() {
        if (selected.size() == entries.size()) {
            selected.clear();
        } else {
            selected.clear();
            for (FeedbackEntry e : entries) {
                selected.add(e.fileName());
            }
        }
    }

    private void exportSelected() {
        if (selected.isEmpty()) {
            sendHint("§c未选择任何反馈");
            return;
        }
        ClientPlayNetworking.send(new ExportFeedbackPacket(new ArrayList<>(selected)));
        sendHint("§a已导出 " + selected.size() + " 条反馈");
    }

    public void setFeedback(List<FeedbackEntry> entries) {
        this.entries.clear();
        this.entries.addAll(entries);
        this.selected.clear();
    }

    private void sendHint(String msg) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal(msg));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("反馈设置"), px + 10, py + 10, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("开始"), px + 10, py + 32, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("结束"), px + 145, py + 32, 0xFFFFFFFF, false);

        renderFeedbackList(graphics, px + 10, py + 78, PANEL_W - 20, PANEL_H - 88);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderFeedbackList(GuiGraphics graphics, int x, int y, int w, int h) {
        int totalHeight = entries.size() * (CARD_H + CARD_GAP);
        if (totalHeight > h && scrollOffset > totalHeight - h) {
            scrollOffset = Math.max(0, totalHeight - h);
        }

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            FeedbackEntry e = entries.get(i);
            int cardY = currentY + i * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < y || cardY > y + h) continue;

            if (selected.contains(e.fileName())) {
                TrainStyleRenderHelper.renderSelectedCard(graphics, x, cardY, w, CARD_H);
            } else {
                TrainStyleRenderHelper.renderCard(graphics, x, cardY, w, CARD_H);
            }

            String name = e.anonymous() ? "匿名玩家" : e.playerName();
            graphics.drawString(this.font, Component.literal(name), x + 24, cardY + 6, 0x333333, false);
            graphics.drawString(this.font, Component.literal(formatTime(e.timestamp())), x + 24, cardY + 18, 0x666666, false);
            String preview = e.content().length() > 24 ? e.content().substring(0, 24) + "..." : e.content();
            graphics.drawString(this.font, Component.literal(preview), x + 24, cardY + 30, 0x666666, false);

            int checkX = x + 6;
            int checkY = cardY + 14;
            graphics.fill(checkX, checkY, checkX + 14, checkY + 14, 0xFFFFFFFF);
            graphics.renderOutline(checkX, checkY, 14, 14, TrainStyleRenderHelper.CARD_BORDER);
            if (selected.contains(e.fileName())) {
                graphics.drawString(this.font, Component.literal("✓"), checkX + 3, checkY + 2, TrainStyleRenderHelper.CARD_BORDER, false);
            }
        }
    }

    private String formatTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.ofHours(8)).format(FORMATTER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            int contentTop = py + 78;
            int contentX = px + 10;
            int contentW = PANEL_W - 20;
            int currentY = contentTop - (int) scrollOffset;

            for (int i = 0; i < entries.size(); i++) {
                int cardY = currentY + i * (CARD_H + CARD_GAP);
                if (mouseY >= cardY && mouseY <= cardY + CARD_H
                    && mouseX >= contentX && mouseX <= contentX + contentW) {
                    FeedbackEntry e = entries.get(i);
                    if (selected.contains(e.fileName())) {
                        selected.remove(e.fileName());
                    } else {
                        selected.add(e.fileName());
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalHeight = entries.size() * (CARD_H + CARD_GAP);
        int visibleHeight = PANEL_H - 88;
        if (totalHeight > visibleHeight) {
            scrollOffset -= scrollY * 15;
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - visibleHeight));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
