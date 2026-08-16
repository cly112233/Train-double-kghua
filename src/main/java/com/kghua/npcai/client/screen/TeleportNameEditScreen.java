package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.TeleportPoint;
import com.kghua.npcai.network.AddTeleportPointPacket;
import com.kghua.npcai.network.RemoveTeleportPointPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 地图组修改模式：传送点编辑页。
 * 可修改名称与类型（分类），坐标不可改，无删除。
 */
public class TeleportNameEditScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 210;

    private final int entityId;
    private final TeleportPoint point;
    private final Screen parentScreen;
    private EditBox nameBox;
    private String selectedCategory;
    private Button categoryButton;

    public TeleportNameEditScreen(int entityId, TeleportPoint point, Screen parentScreen) {
        super(Component.literal("编辑传送点名称"));
        this.entityId = entityId;
        this.point = point;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        this.nameBox = new EditBox(this.font, px + 60, py + 44, PANEL_W - 80, 18, Component.literal(""));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(point.name());
        this.addRenderableWidget(this.nameBox);

        // 类型（分类）：单个按键点击循环切换
        this.selectedCategory = point.category() != null ? point.category() : "其他";
        this.categoryButton = new NoShadowButton(px + 60, py + 74, PANEL_W - 80, 16,
            Component.literal(""), btn -> {
                int idx = java.util.Arrays.asList(TeleportPoint.CATEGORIES).indexOf(selectedCategory);
                selectedCategory = TeleportPoint.CATEGORIES[(idx + 1) % TeleportPoint.CATEGORIES.length];
                refreshCategoryButton();
            });
        this.addRenderableWidget(this.categoryButton);
        refreshCategoryButton();

        this.addRenderableWidget(new NoShadowButton(px + 50, py + 140, 70, 20,
            Component.literal("保存"), btn -> save()));
        this.addRenderableWidget(new NoShadowButton(px + 140, py + 140, 70, 20,
            Component.literal("取消"), btn -> backToParent()));
    }

    private void refreshCategoryButton() {
        if (categoryButton != null) {
            categoryButton.setMessage(Component.literal(selectedCategory));
        }
    }

    private void backToParent() {
        if (this.minecraft != null && parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            onClose();
        }
    }

    private void save() {
        String newName = nameBox.getValue().trim();
        if (newName.isEmpty()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§c名称不能为空"));
            }
            return;
        }
        // 改名/改类型：删除旧名 + 添加新名（坐标保持不变）
        if (!newName.equals(point.name())) {
            ClientPlayNetworking.send(new RemoveTeleportPointPacket(entityId, point.name()));
        }
        ClientPlayNetworking.send(new AddTeleportPointPacket(
            entityId, newName, point.x(), point.y(), point.z(), selectedCategory));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("§a已保存"));
        }
        backToParent();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("编辑传送点"), px + 66, py + 12, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("名称"), px + 14, py + 48, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("类型"), px + 14, py + 77, 0xFFFFFFFF, false);
        // 坐标只读显示
        String coord = String.format("%.0f, %.0f, %.0f", point.x(), point.y(), point.z());
        graphics.drawString(this.font, Component.literal("坐标: " + coord), px + 14, py + 106, 0xAAAAAA, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
