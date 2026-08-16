package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.TeleportPoint;
import com.kghua.npcai.network.RequestNpcDataPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图组修改模式：类似管理端传送点面板。
 * - 顶部四个板块按键
 * - 搜索栏（同搜索功能，关键词高亮）
 * - 传送点卡片：点击进入编辑名称页（不能改坐标、无删除）
 */
public class MapTeleportEditScreen extends Screen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 320;
    private static final int CARD_H = 32;
    private static final int CARD_GAP = 2; // 左右间距为原来的1/3
    private static final int COLUMNS = 3;  // 一行最多3个

    private final int entityId;
    private final String npcName;
    private final Screen parentScreen;
    private final List<TeleportPoint> points = new ArrayList<>();
    private int categoryIndex = 0;
    private EditBox searchBox;
    private String searchText = "";
    private double scrollOffset = 0;

    public MapTeleportEditScreen(int entityId, String npcName, Screen parentScreen) {
        super(Component.literal("传送点修改"));
        this.entityId = entityId;
        this.npcName = npcName;
        this.parentScreen = parentScreen;
    }

    public void setTeleportPoints(List<TeleportPoint> list) {
        this.points.clear();
        this.points.addAll(list);
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        // 四个板块按键（顶部）+ 添加传送点（最右侧，与管理端一致）
        int tabY = py + 34;
        int addW = 96;
        int tabW = Math.max(60, (PANEL_W - 20 - addW - 6) / 4 - 2);
        for (int i = 0; i < TeleportPoint.CATEGORIES.length; i++) {
            final int idx = i;
            this.addRenderableWidget(new NoShadowButton(px + 10 + i * (tabW + 2), tabY, tabW, 16,
                Component.literal(TeleportPoint.CATEGORIES[i]), btn -> {
                    categoryIndex = idx;
                    searchText = "";
                    if (searchBox != null) searchBox.setValue("");
                    scrollOffset = 0;
                }));
        }
        this.addRenderableWidget(new NoShadowButton(px + PANEL_W - addW - 10, tabY, addW, 16,
            Component.literal("+ 添加传送点"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new AddTeleportPointScreen(entityId, null, this));
                }
            }));

        // 搜索栏
        this.searchBox = new EditBox(this.font, px + 10, tabY + 20, PANEL_W - 20, 16, Component.literal(""));
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.literal("搜索传送点..."));
        this.searchBox.setResponder(text -> {
            this.searchText = text;
            this.scrollOffset = 0;
        });
        this.addRenderableWidget(this.searchBox);

        ClientPlayNetworking.send(new RequestNpcDataPacket(entityId));
    }

    private void backToParent() {
        if (this.minecraft != null && parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            onClose();
        }
    }

    private List<TeleportPoint> getFiltered() {
        String selected = TeleportPoint.CATEGORIES[categoryIndex];
        List<TeleportPoint> result = new ArrayList<>();
        for (TeleportPoint p : points) {
            String cat = p.category() != null ? p.category() : "其他";
            if (!cat.equals(selected)) continue;
            if (!searchText.isEmpty() && !p.name().toLowerCase().contains(searchText.toLowerCase())) continue;
            result.add(p);
        }
        return result;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("传送点修改"), px + 66, py + 12, 0xFFFFFFFF, false);

        // 内容区结界
        graphics.enableScissor(px + 4, py + 76, px + PANEL_W - 4, py + PANEL_H - 4);

        int contentTop = py + 76;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int contentH = PANEL_H - 86;

        List<TeleportPoint> filtered = getFiltered();
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int rows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        int totalHeight = rows * (CARD_H + CARD_GAP);
        if (totalHeight > contentH && scrollOffset > totalHeight - contentH) {
            scrollOffset = Math.max(0, totalHeight - contentH);
        }

        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < filtered.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < contentTop || cardY > contentTop + contentH) continue;
            TeleportPoint p = filtered.get(i);
            TrainStyleRenderHelper.renderCard(graphics, cardX, cardY, cardW, CARD_H);
            // 名称（搜索关键词高亮）
            String name = p.name();
            if (!searchText.isEmpty()) {
                drawHighlighted(graphics, name, cardX + 4, cardY + 5, searchText);
            } else {
                if (this.font.width(name) > cardW - 10) {
                    name = this.font.plainSubstrByWidth(name, cardW - 10) + "...";
                }
                graphics.drawString(this.font, Component.literal(name), cardX + 4, cardY + 5, 0x333333, false);
            }
            String coord = String.format("%.0f,%.0f,%.0f", p.x(), p.y(), p.z());
            if (this.font.width(coord) > cardW - 10) {
                coord = this.font.plainSubstrByWidth(coord, cardW - 10) + "...";
            }
            graphics.drawString(this.font, Component.literal(coord), cardX + 4, cardY + 18, 0x666666, false);
        }

        if (filtered.isEmpty()) {
            graphics.drawString(this.font, Component.literal("暂无传送点"), contentX + 8, contentTop + 10, 0x666666, false);
        }

        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawHighlighted(GuiGraphics graphics, String text, int x, int y, String keyword) {
        String lower = text.toLowerCase();
        String lowerSearch = keyword.toLowerCase();
        int idx = lower.indexOf(lowerSearch);
        if (idx < 0) {
            graphics.drawString(this.font, Component.literal(text), x, y, 0x333333, false);
            return;
        }
        String before = text.substring(0, idx);
        int curX = x;
        if (!before.isEmpty()) {
            graphics.drawString(this.font, Component.literal(before), curX, y, 0x333333, false);
            curX += this.font.width(before);
        }
        String keywordPart = text.substring(idx, idx + keyword.length());
        graphics.drawString(this.font, Component.literal(keywordPart), curX, y, 0x55FF55, false);
        curX += this.font.width(keywordPart);
        String after = text.substring(idx + keyword.length());
        if (!after.isEmpty()) {
            graphics.drawString(this.font, Component.literal(after), curX, y, 0x333333, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int contentTop = py + 76;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;

        List<TeleportPoint> filtered = getFiltered();
        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < filtered.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (mouseY >= cardY && mouseY <= cardY + CARD_H
                && mouseX >= cardX && mouseX <= cardX + cardW) {
                // 点击卡片 → 编辑名称页
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new TeleportNameEditScreen(entityId, filtered.get(i), this));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<TeleportPoint> filtered = getFiltered();
        int rows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        int totalHeight = rows * (CARD_H + CARD_GAP);
        int visibleHeight = PANEL_H - 86;
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
