package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Contribution;
import com.kghua.npcai.data.ContributionStorage;
import com.kghua.npcai.network.RequestContributionsPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 投稿分类选择页：角色投稿 / 修饰符投稿 / 其他玩家投稿。
 */
public class ContributionCategoryScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 240;
    private static final int BTN_W = 180;
    private static final int BTN_H = 32;
    private static final int BTN_GAP = 12;

    private final int entityId;
    private final String npcName;
    private final Screen parentScreen;
    private final List<Contribution> contributions = new ArrayList<>();
    private Button roleButton;
    private Button modifierButton;

    public ContributionCategoryScreen(int entityId, String npcName) {
        this(entityId, npcName, null);
    }

    public ContributionCategoryScreen(int entityId, String npcName, Screen parentScreen) {
        super(Component.literal("投稿"));
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

        // 打开时请求最新投稿数据（用于本期已投稿数量统计）
        ClientPlayNetworking.send(new RequestContributionsPacket());
        int btnY = py + 42;
        this.roleButton = new NoShadowButton(px + (PANEL_W - BTN_W) / 2, btnY, BTN_W, BTN_H,
            Component.literal("角色投稿"), btn -> this.minecraft.setScreen(new ContributionSubmitScreen(entityId, npcName, Contribution.TYPE_ROLE, this)));
        this.addRenderableWidget(this.roleButton);
        btnY += BTN_H + BTN_GAP;
        this.modifierButton = new NoShadowButton(px + (PANEL_W - BTN_W) / 2, btnY, BTN_W, BTN_H,
            Component.literal("修饰符投稿"), btn -> this.minecraft.setScreen(new ContributionSubmitScreen(entityId, npcName, Contribution.TYPE_MODIFIER, this)));
        this.addRenderableWidget(this.modifierButton);
        btnY += BTN_H + BTN_GAP;
        this.addRenderableWidget(new NoShadowButton(px + (PANEL_W - BTN_W) / 2, btnY, BTN_W, BTN_H,
            Component.literal("其他玩家投稿"), btn -> this.minecraft.setScreen(new ContributionBrowseScreen(entityId, npcName, this))));
    }

    public void setContributions(List<Contribution> list) {
        this.contributions.clear();
        this.contributions.addAll(list);
    }

    /** 本期两个分区（角色+修饰符）合计已投稿数量 */
    private int myPeriodSubmissionCount() {
        if (this.minecraft == null || this.minecraft.player == null) return 0;
        java.util.UUID me = this.minecraft.player.getUUID();
        int currentPeriod = Contribution.getCurrentPeriod();
        int count = 0;
        for (Contribution c : contributions) {
            if (c.getPeriod() == currentPeriod && me.equals(c.getAuthorId())) count++;
        }
        return count;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        int titleW = this.font.width("投稿");
        graphics.drawString(this.font, Component.literal("投稿"), px + (PANEL_W - titleW) / 2, py + 13, 0xFFFFFFFF, false);

        // 本期投稿计数：达到上限时禁用角色/修饰符投稿入口
        int count = myPeriodSubmissionCount();
        boolean full = count >= ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD;
        if (roleButton != null) roleButton.active = !full;
        if (modifierButton != null) modifierButton.active = !full;
        String countText = "本期已投稿 " + count + "/" + ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD;
        graphics.drawString(this.font, Component.literal(countText),
            px + (PANEL_W - this.font.width(countText)) / 2, py + PANEL_H - 26,
            full ? 0xFFCC00 : 0xFFFFFFFF, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
