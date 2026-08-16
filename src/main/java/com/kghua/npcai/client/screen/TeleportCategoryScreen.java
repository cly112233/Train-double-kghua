package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.TeleportPoint;
import com.kghua.npcai.network.RequestNpcDataPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 传送点分类选择页：小图 / 中图 / 大图 / 其他。
 */
public class TeleportCategoryScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 260;
    private static final int BTN_W = 160;
    private static final int BTN_H = 30;
    private static final int BTN_GAP = 10;

    private final int entityId;
    private final String npcName;
    private final Screen parentScreen;

    public TeleportCategoryScreen(int entityId, String npcName) {
        this(entityId, npcName, null);
    }

    public TeleportCategoryScreen(int entityId, String npcName, Screen parentScreen) {
        super(Component.literal("选择传送点分类"));
        this.entityId = entityId;
        this.npcName = npcName;
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

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        int btnY = py + 40;
        for (int i = 0; i < TeleportPoint.CATEGORIES.length; i++) {
            final String cat = TeleportPoint.CATEGORIES[i];
            this.addRenderableWidget(new NoShadowButton(px + (PANEL_W - BTN_W) / 2, btnY, BTN_W, BTN_H,
                Component.literal(cat), btn -> openCategory(cat)));
            btnY += BTN_H + BTN_GAP;
        }

        // 修改模式（地图组成员）：类似管理端传送点面板，可编辑名称
        this.addRenderableWidget(new NoShadowButton(px + (PANEL_W - BTN_W) / 2, btnY + 4, BTN_W, BTN_H,
            Component.literal("修改模式"), btn -> {
                // 对局进行中禁止进入修改模式（NPC管理员豁免）
                if (com.kghua.npcai.client.ClientCache.isGameInProgress()
                    && !com.kghua.npcai.client.ClientCache.isNpcAdmin()) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c游戏中无法使用传送"));
                    }
                    return;
                }
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new MapTeleportEditScreen(entityId, npcName, this));
                }
            }));
    }

    private void openCategory(String category) {
        ClientPlayNetworking.send(new RequestNpcDataPacket(entityId));
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TeleportScreen(entityId, npcName, category));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        int titleW = this.font.width("传送点分类");
        graphics.drawString(this.font, Component.literal("传送点分类"), px + (PANEL_W - titleW) / 2, py + 13, 0xFFFFFFFF, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
