package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.TeleportPoint;
import com.kghua.npcai.network.TeleportRequestPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TeleportScreen extends Screen {
    private final int entityId;
    private final String npcName;
    private final String category;
    private final List<TeleportPoint> allPoints = new ArrayList<>();
    private EditBox searchBox;
    private String searchText = "";
    private double scrollOffset = 0;

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;
    private static final int CARD_H = 32;
    private static final int CARD_GAP = 2; // 左右间距约为原来的1/3
    private static final int COLUMNS = 3;  // 一行最多3个卡片

    public TeleportScreen(int entityId, String npcName, String category) {
        super(Component.literal(category + "传送点"));
        this.entityId = entityId;
        this.npcName = npcName;
        this.category = category;
    }

    // 兼容旧调用
    public TeleportScreen(int entityId, String npcName) {
        this(entityId, npcName, "其他");
    }

    public void setTeleportPoints(List<TeleportPoint> points) {
        this.allPoints.clear();
        // 过滤此分类
        for (TeleportPoint p : points) {
            String cat = p.category() != null ? p.category() : "其他";
            if (cat.equals(this.category)) {
                this.allPoints.add(p);
            }
        }
    }

    private List<TeleportPoint> getFilteredPoints() {
        if (searchText.isEmpty()) return allPoints;
        String lower = searchText.toLowerCase();
        return allPoints.stream()
            .filter(p -> p.name().toLowerCase().contains(lower))
            .collect(Collectors.toList());
    }

    public int getEntityId() {
        return entityId;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToCategory()));

        this.searchBox = new EditBox(this.font, px + 10, py + 32, PANEL_W - 20, 16, Component.literal(""));
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.literal("搜索传送点..."));
        this.searchBox.setResponder(text -> {
            this.searchText = text;
            this.scrollOffset = 0;
        });
        this.addRenderableWidget(this.searchBox);
    }

    private void backToCategory() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TeleportCategoryScreen(entityId, npcName));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        Component title = Component.literal(npcName + " - 传送点");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, px + (PANEL_W - titleWidth) / 2, py + 6, 0xFFFFFFFF, false);

        // 内容区边界：顶部 = 搜索框底部，左右/底部 = 面板自身边框（组件超出即消失）
        int contentTop = py + 52; // 搜索框(py+32高16)底部 + 4px
        int contentBottom = py + PANEL_H - 4;
        graphics.enableScissor(px + 2, contentTop, px + PANEL_W - 2, contentBottom);

        int contentX = px + 10;
        int contentW = PANEL_W - 20;

        List<TeleportPoint> filtered = getFilteredPoints();

        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int rows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        int totalHeight = rows * (CARD_H + CARD_GAP);
        int visibleHeight = contentBottom - contentTop;
        if (totalHeight > visibleHeight && scrollOffset > totalHeight - visibleHeight) {
            scrollOffset = Math.max(0, totalHeight - visibleHeight);
        }

        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < filtered.size(); i++) {
            TeleportPoint p = filtered.get(i);
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);

            TrainStyleRenderHelper.renderCard(graphics, cardX, cardY, cardW, CARD_H);
            // 卡片标题（小卡片，截断+关键词高亮）
            String name = p.name();
            if (this.font.width(name) > cardW - 10) {
                name = this.font.plainSubstrByWidth(name, cardW - 10) + "...";
            }
            drawHighlightedText(graphics, name, cardX + 4, cardY + 5, 0x333333, 0xFF55FF55);
            String coord = String.format("%.0f,%.0f,%.0f", p.x(), p.y(), p.z());
            if (this.font.width(coord) > cardW - 10) {
                coord = this.font.plainSubstrByWidth(coord, cardW - 10) + "...";
            }
            graphics.drawString(this.font, Component.literal(coord), cardX + 4, cardY + 18, 0x666666, false);
        }

        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawHighlightedText(GuiGraphics graphics, String text, int x, int y, int normalColor, int highlightColor) {
        if (searchText.isEmpty()) {
            graphics.drawString(this.font, Component.literal(text), x, y, normalColor, false);
            return;
        }
        String lower = text.toLowerCase();
        String lowerSearch = searchText.toLowerCase();
        int idx = lower.indexOf(lowerSearch);
        if (idx < 0) {
            graphics.drawString(this.font, Component.literal(text), x, y, normalColor, false);
            return;
        }
        // 前半部分（正常色）
        String before = text.substring(0, idx);
        int curX = x;
        if (!before.isEmpty()) {
            graphics.drawString(this.font, Component.literal(before), curX, y, normalColor, false);
            curX += this.font.width(before);
        }
        // 关键词（黄色高亮）
        String keyword = text.substring(idx, idx + searchText.length());
        graphics.drawString(this.font, Component.literal(keyword), curX, y, highlightColor, false);
        curX += this.font.width(keyword);
        // 后半部分
        String after = text.substring(idx + searchText.length());
        if (!after.isEmpty()) {
            graphics.drawString(this.font, Component.literal(after), curX, y, normalColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int contentTop = py + 52;
        int contentBottom = py + PANEL_H - 4;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;

        List<TeleportPoint> filtered = getFilteredPoints();
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < filtered.size(); i++) {
            TeleportPoint p = filtered.get(i);
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < contentTop || cardY > contentBottom) continue;
            if (mouseX >= cardX && mouseX <= cardX + cardW
                && mouseY >= cardY && mouseY <= cardY + CARD_H) {
                // 对局进行中禁止传送（NPC管理员豁免）
                if (com.kghua.npcai.client.ClientCache.isGameInProgress()
                    && !com.kghua.npcai.client.ClientCache.isNpcAdmin()) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c游戏中无法使用传送"));
                    }
                    return true;
                }
                ClientPlayNetworking.send(new TeleportRequestPacket(entityId, p.name()));
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = (getFilteredPoints().size() + COLUMNS - 1) / COLUMNS;
        int totalHeight = rows * (CARD_H + CARD_GAP);
        int visibleHeight = PANEL_H - 52;
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
