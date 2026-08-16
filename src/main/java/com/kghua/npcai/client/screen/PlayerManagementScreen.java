package com.kghua.npcai.client.screen;

import com.kghua.npcai.network.ManagePlayerPacket;
import com.kghua.npcai.network.SyncPlayerListPacket;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 管理单个玩家的子页面。
 */
public class PlayerManagementScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 270;


    private final SyncPlayerListPacket.PlayerInfo player;
    private final Screen parentScreen;

    private Button npcAdminButton;
    private Button confirmNpcAdminButton;
    private Button cancelNpcAdminButton;
    private Button opButton;
    private Button confirmOpButton;
    private Button cancelOpButton;
    private Button mapGroupButton;
    private Button confirmMapGroupButton;
    private Button cancelMapGroupButton;

    private boolean confirmingNpcAdmin = false;
    private boolean confirmingOp = false;
    private boolean confirmingMapGroup = false;

    public PlayerManagementScreen(SyncPlayerListPacket.PlayerInfo player, Screen parentScreen) {
        super(Component.literal("玩家设置"));
        this.player = player;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        // 返回按钮在面板左上角（返回管理端）
        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        this.npcAdminButton = new NoShadowButton(px + 70, py + 136, 120, 20,
            Component.literal(npcAdminLabel()), btn -> startConfirmNpcAdmin());
        this.addRenderableWidget(this.npcAdminButton);

        this.confirmNpcAdminButton = new NoShadowButton(px + 70, py + 136, 58, 20,
            Component.literal("确认"), btn -> confirmNpcAdmin());
        this.confirmNpcAdminButton.visible = false;
        this.confirmNpcAdminButton.active = false;
        this.addRenderableWidget(this.confirmNpcAdminButton);

        this.cancelNpcAdminButton = new NoShadowButton(px + 132, py + 136, 58, 20,
            Component.literal("取消"), btn -> cancelConfirmNpcAdmin());
        this.cancelNpcAdminButton.visible = false;
        this.cancelNpcAdminButton.active = false;
        this.addRenderableWidget(this.cancelNpcAdminButton);

        this.opButton = new NoShadowButton(px + 70, py + 160, 120, 20,
            Component.literal(opLabel()), btn -> startConfirmOp());
        this.addRenderableWidget(this.opButton);

        this.confirmOpButton = new NoShadowButton(px + 70, py + 160, 58, 20,
            Component.literal("确认"), btn -> confirmOp());
        this.confirmOpButton.visible = false;
        this.confirmOpButton.active = false;
        this.addRenderableWidget(this.confirmOpButton);

        this.cancelOpButton = new NoShadowButton(px + 132, py + 160, 58, 20,
            Component.literal("取消"), btn -> cancelConfirmOp());
        this.cancelOpButton.visible = false;
        this.cancelOpButton.active = false;
        this.addRenderableWidget(this.cancelOpButton);

        this.mapGroupButton = new NoShadowButton(px + 70, py + 184, 120, 20,
            Component.literal(mapGroupLabel()), btn -> startConfirmMapGroup());
        this.addRenderableWidget(this.mapGroupButton);

        this.confirmMapGroupButton = new NoShadowButton(px + 70, py + 184, 58, 20,
            Component.literal("确认"), btn -> confirmMapGroup());
        this.confirmMapGroupButton.visible = false;
        this.confirmMapGroupButton.active = false;
        this.addRenderableWidget(this.confirmMapGroupButton);

        this.cancelMapGroupButton = new NoShadowButton(px + 132, py + 184, 58, 20,
            Component.literal("取消"), btn -> cancelConfirmMapGroup());
        this.cancelMapGroupButton.visible = false;
        this.cancelMapGroupButton.active = false;
        this.addRenderableWidget(this.cancelMapGroupButton);

    }

    private void backToParent() {
        if (this.minecraft != null && parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            onClose();
        }
    }

    private String npcAdminLabel() {
        return player.npcAdmin() ? "取消NPC管理" : "给予NPC管理权限";
    }

    private String opLabel() {
        return player.op() ? "取消管理员" : "设置为管理员";
    }

    private String mapGroupLabel() {
        return player.mapGroup() ? "取消地图组成员" : "添加为地图组成员";
    }

    private void startConfirmNpcAdmin() {
        confirmingNpcAdmin = true;
        confirmingOp = false;
        confirmingMapGroup = false;
        updateVisibility();
    }

    private void cancelConfirmNpcAdmin() {
        confirmingNpcAdmin = false;
        updateVisibility();
    }

    private void confirmNpcAdmin() {
        ClientPlayNetworking.send(new ManagePlayerPacket(player.id(), "npcadmin", ""));
        backToParent();
    }

    private void startConfirmOp() {
        confirmingOp = true;
        confirmingNpcAdmin = false;
        confirmingMapGroup = false;
        updateVisibility();
    }

    private void cancelConfirmOp() {
        confirmingOp = false;
        updateVisibility();
    }

    private void confirmOp() {
        ClientPlayNetworking.send(new ManagePlayerPacket(player.id(), "op", ""));
        backToParent();
    }

    private void startConfirmMapGroup() {
        confirmingMapGroup = true;
        confirmingNpcAdmin = false;
        confirmingOp = false;
        updateVisibility();
    }

    private void cancelConfirmMapGroup() {
        confirmingMapGroup = false;
        updateVisibility();
    }

    private void confirmMapGroup() {
        ClientPlayNetworking.send(new ManagePlayerPacket(player.id(), "mapgroup", ""));
        backToParent();
    }

    private void updateVisibility() {
        boolean idle = !confirmingNpcAdmin && !confirmingOp && !confirmingMapGroup;
        npcAdminButton.visible = idle;
        npcAdminButton.active = idle;
        opButton.visible = idle;
        opButton.active = idle;
        mapGroupButton.visible = idle;
        mapGroupButton.active = idle;

        confirmNpcAdminButton.visible = confirmingNpcAdmin;
        confirmNpcAdminButton.active = confirmingNpcAdmin;
        cancelNpcAdminButton.visible = confirmingNpcAdmin;
        cancelNpcAdminButton.active = confirmingNpcAdmin;

        confirmOpButton.visible = confirmingOp;
        confirmOpButton.active = confirmingOp;
        cancelOpButton.visible = confirmingOp;
        cancelOpButton.active = confirmingOp;

        confirmMapGroupButton.visible = confirmingMapGroup;
        confirmMapGroupButton.active = confirmingMapGroup;
        cancelMapGroupButton.visible = confirmingMapGroup;
        cancelMapGroupButton.active = confirmingMapGroup;
    }

    private ResourceLocation getPlayerSkinTexture() {
        GameProfile profile = new GameProfile(player.id(), player.name());
        var playerSkin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
        if (playerSkin != null) {
            return playerSkin.texture();
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    private int parseColor(String color) {
        if (color == null || color.isEmpty()) return 0xFFFFFFFF;
        String c = color;
        if (c.startsWith("#")) c = c.substring(1);
        try {
            return 0xFF000000 | Integer.parseInt(c, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);

        // 头像上移，位于标题下方左侧
        PlayerFaceRenderer.draw(graphics, getPlayerSkinTexture(), px + 14, py + 38, 32);

        int nameX = px + 56;
        int nameY = py + 42;
        int nameColor = player.playerColor().isEmpty() ? 0xFF333333 : parseColor(player.playerColor());
        graphics.drawString(this.font, Component.literal(player.name()), nameX, nameY, nameColor, false);

        int badgeX = nameX + this.font.width(player.name()) + 6;
        int badgeY = nameY - 2;
        if (player.npcAdmin()) {
            drawBadge(graphics, "N", badgeX, badgeY, 0xFFFF8800, 0xFFCC6600);
            badgeX += 18;
        }
        if (player.op()) {
            drawBadge(graphics, "管", badgeX, badgeY, 0xFFFFFF00, 0xFFFFCC00);
            badgeX += 18;
        }
        if (player.mapGroup()) {
            drawBadge(graphics, "地", badgeX, badgeY, 0xFF55FF55, 0xFF00AA00);
        }

        graphics.drawString(this.font, Component.literal("NPC管理"), px + 12, py + 140, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("OP权限"), px + 12, py + 164, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("地图组"), px + 14, py + 188, 0xFFFFFFFF, false);

        if (confirmingNpcAdmin) {
            graphics.drawString(this.font, Component.literal("确认更改NPC管理权限？"), px + 70, py + 240, 0xFFFF6666, false);
        } else if (confirmingOp) {
            graphics.drawString(this.font, Component.literal("确认更改管理员权限？"), px + 70, py + 240, 0xFFFF6666, false);
        } else if (confirmingMapGroup) {
            graphics.drawString(this.font, Component.literal("确认更改地图组成员权限？"), px + 70, py + 240, 0xFFFF6666, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawBadge(GuiGraphics graphics, String text, int x, int y, int bgColor, int borderColor) {
        graphics.fill(x, y, x + 14, y + 14, bgColor);
        graphics.renderOutline(x, y, 14, 14, borderColor);
        int textW = this.font.width(text);
        graphics.drawString(this.font, Component.literal(text), x + (14 - textW) / 2, y + 3, 0xFF333333, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
