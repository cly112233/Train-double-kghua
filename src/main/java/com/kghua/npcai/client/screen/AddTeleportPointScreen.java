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

public class AddTeleportPointScreen extends Screen {
    private final int entityId;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private Button categoryButton;
    private int categoryIndex = 0;
    // 编辑模式：传入现有点则预填，提交时先删旧点再加新点
    private final TeleportPoint editingPoint;
    private int editCategoryIndex = -1;
    // 上一级页面（返回时回到管理端）
    private final Screen parentScreen;

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 180;

    public AddTeleportPointScreen(int entityId) {
        this(entityId, null, null);
    }

    public AddTeleportPointScreen(int entityId, TeleportPoint editingPoint) {
        this(entityId, editingPoint, null);
    }

    public AddTeleportPointScreen(int entityId, TeleportPoint editingPoint, Screen parentScreen) {
        super(Component.literal(editingPoint != null ? "编辑传送点" : "添加传送点"));
        this.entityId = entityId;
        this.editingPoint = editingPoint;
        this.parentScreen = parentScreen;
        if (editingPoint != null) {
            String cat = editingPoint.category();
            for (int i = 0; i < TeleportPoint.CATEGORIES.length; i++) {
                if (TeleportPoint.CATEGORIES[i].equals(cat)) {
                    this.editCategoryIndex = i;
                    break;
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.nameBox = new EditBox(this.font, px + 70, py + 30, 130, 18, Component.literal(""));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(editingPoint != null ? editingPoint.name() : "");
        this.addRenderableWidget(this.nameBox);

        this.xBox = new EditBox(this.font, px + 70, py + 55, 130, 18, Component.literal(""));
        this.xBox.setMaxLength(20);
        this.addRenderableWidget(this.xBox);

        this.yBox = new EditBox(this.font, px + 70, py + 80, 130, 18, Component.literal(""));
        this.yBox.setMaxLength(20);
        this.addRenderableWidget(this.yBox);

        this.zBox = new EditBox(this.font, px + 70, py + 105, 130, 18, Component.literal(""));
        this.zBox.setMaxLength(20);
        this.addRenderableWidget(this.zBox);

        this.categoryButton = new NoShadowButton(px + 70, py + 128, 130, 18,
            Component.literal("分类: " + TeleportPoint.CATEGORIES[categoryIndex]), btn -> {
                categoryIndex = (categoryIndex + 1) % TeleportPoint.CATEGORIES.length;
                categoryButton.setMessage(Component.literal("分类: " + TeleportPoint.CATEGORIES[categoryIndex]));
            });
        this.addRenderableWidget(this.categoryButton);

        this.addRenderableWidget(new NoShadowButton(px + 30, py + 152, 70, 20,
            Component.literal("确认"), btn -> submit()));
        this.addRenderableWidget(new NoShadowButton(px + 120, py + 152, 70, 20,
            Component.literal("取消"), btn -> backToParent()));

        if (editingPoint != null) {
            // 编辑模式：预填坐标和分类
            this.xBox.setValue(formatCoord(editingPoint.x()));
            this.yBox.setValue(formatCoord(editingPoint.y()));
            this.zBox.setValue(formatCoord(editingPoint.z()));
            if (editCategoryIndex >= 0) {
                this.categoryIndex = editCategoryIndex;
                this.categoryButton.setMessage(Component.literal("分类: " + TeleportPoint.CATEGORIES[categoryIndex]));
            }
        } else if (this.minecraft != null && this.minecraft.player != null) {
            this.xBox.setValue(String.format("%.2f", this.minecraft.player.getX()));
            this.yBox.setValue(String.format("%.2f", this.minecraft.player.getY()));
            this.zBox.setValue(String.format("%.2f", this.minecraft.player.getZ()));
        }
    }

    private String formatCoord(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }

    private void backToParent() {
        if (this.minecraft != null && parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            onClose();
        }
    }

    private void submit() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        try {
            double x = Double.parseDouble(xBox.getValue().trim());
            double y = Double.parseDouble(yBox.getValue().trim());
            double z = Double.parseDouble(zBox.getValue().trim());
            String category = TeleportPoint.CATEGORIES[categoryIndex];
            if (editingPoint != null && !editingPoint.name().equals(name)) {
                // 编辑且改名：先删旧点再加新点
                ClientPlayNetworking.send(new RemoveTeleportPointPacket(entityId, editingPoint.name()));
            }
            ClientPlayNetworking.send(new AddTeleportPointPacket(entityId, name, x, y, z, category));
        } catch (NumberFormatException e) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§c坐标格式错误"));
            }
        }
        backToParent();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("添加传送点"), px + 10, py + 10, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("名称"), px + 10, py + 34, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("X"), px + 10, py + 59, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("Y"), px + 10, py + 84, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("Z"), px + 10, py + 109, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("分类"), px + 10, py + 131, 0xFFFFFFFF, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
