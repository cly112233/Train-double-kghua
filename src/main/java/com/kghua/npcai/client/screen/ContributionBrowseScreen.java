package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Contribution;
import com.kghua.npcai.network.LikeContributionPacket;
import com.kghua.npcai.network.RequestContributionsPacket;
import com.kghua.npcai.network.SyncContributionsPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 其他玩家投稿浏览页：角色/修饰符切换板块，卡片按点赞数排序，详情+点赞。
 */
public class ContributionBrowseScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 300;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 6;
    private static final int COLUMNS = 3;

    private final int entityId;
    private final String npcName;
    private final List<Contribution> contributions = new ArrayList<>();
    private int subTab = 0; // 0=角色, 1=修饰符
    private double scrollOffset = 0;
    private int remainingLikes = 3;
    private final Screen parentScreen;

    public ContributionBrowseScreen(int entityId, String npcName) {
        this(entityId, npcName, null);
    }

    public ContributionBrowseScreen(int entityId, String npcName, Screen parentScreen) {
        super(Component.literal("玩家投稿"));
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

    private final java.util.Set<String> likedIds = new java.util.HashSet<>();

    public void setContributions(List<Contribution> list) {
        this.contributions.clear();
        this.contributions.addAll(list);
    }

    public void setRemainingLikes(int remaining) {
        this.remainingLikes = remaining;
    }

    public void setLikedIds(List<String> ids) {
        likedIds.clear();
        likedIds.addAll(ids);
    }

    public boolean isLiked(java.util.UUID contributionId) {
        return likedIds.contains(contributionId.toString());
    }

    public int getRemainingLikes() {
        return remainingLikes;
    }

    private List<Contribution> getFiltered() {
        String selected = Contribution.TYPES[subTab];
        int currentPeriod = Contribution.getCurrentPeriod();
        List<Contribution> result = new ArrayList<>();
        for (Contribution c : contributions) {
            // 用户端只能看当前期数且审核通过的投稿（未审核/驳回的不显示）
            if (c.getType().equals(selected) && c.getPeriod() == currentPeriod && c.isApproved()) result.add(c);
        }
        result.sort((a, b) -> Integer.compare(b.getLikes(), a.getLikes()));
        return result;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        // 角色/修饰符切换（标题下方，居中对齐）
        int tabW = 70;
        int tabGap = 8;
        int tabTotal = tabW * 2 + tabGap;
        int tabStartX = px + (PANEL_W - tabTotal) / 2;
        for (int i = 0; i < Contribution.TYPES.length; i++) {
            final int idx = i;
            this.addRenderableWidget(new NoShadowButton(tabStartX + i * (tabW + tabGap), py + 32, tabW, 18,
                Component.literal(Contribution.TYPES[i]), btn -> {
                    subTab = idx;
                    scrollOffset = 0;
                }));
        }

        ClientPlayNetworking.send(new RequestContributionsPacket());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);

        // 顶部标题：第X期
        String periodTitle = "第" + Contribution.getCurrentPeriod() + "期投稿";
        int periodW = this.font.width(periodTitle);
        graphics.drawString(this.font, Component.literal(periodTitle),
            px + (PANEL_W - periodW) / 2, py + 12, 0xFFFFCC00, false);

        // 右上角剩余点赞次数
        String likeInfo = "今日剩余点赞次数：" + remainingLikes + "次";
        graphics.drawString(this.font, Component.literal(likeInfo),
            px + PANEL_W - this.font.width(likeInfo) - 8, py + 12, 0x55FF55, false);

        int contentTop = py + 58;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int contentH = PANEL_H - 68;

        List<Contribution> filtered = getFiltered();
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int totalHeight = ((filtered.size() + COLUMNS - 1) / COLUMNS) * (CARD_H + CARD_GAP);
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
            Contribution c = filtered.get(i);
            TrainStyleRenderHelper.renderCard(graphics, cardX, cardY, cardW, CARD_H);

            String title = c.getTitle();
            if (this.font.width(title) > cardW - 12) {
                title = this.font.plainSubstrByWidth(title, cardW - 12) + "...";
            }
            graphics.drawString(this.font, Component.literal(title), cardX + 6, cardY + 6, 0x333333, false);
            String author = "by " + c.getAuthorName();
            if (this.font.width(author) > cardW - 12) {
                author = this.font.plainSubstrByWidth(author, cardW - 12) + "...";
            }
            graphics.drawString(this.font, Component.literal(author), cardX + 6, cardY + 20, 0x666666, false);

            String likes = "❤ " + c.getLikes();
            graphics.drawString(this.font, Component.literal(likes),
                cardX + cardW - this.font.width(likes) - 4, cardY + CARD_H - 12, 0xCC0000, false);
        }

        if (filtered.isEmpty()) {
            graphics.drawString(this.font, Component.literal("暂无投稿"), contentX + 8, contentTop + 10, 0x666666, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int contentTop = py + 58;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int contentH = PANEL_H - 68;
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;

        List<Contribution> filtered = getFiltered();
        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < filtered.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (mouseY >= cardY && mouseY <= cardY + CARD_H
                && mouseX >= cardX && mouseX <= cardX + cardW) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new ContributionViewScreen(filtered.get(i), this));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<Contribution> filtered = getFiltered();
        int totalHeight = ((filtered.size() + COLUMNS - 1) / COLUMNS) * (CARD_H + CARD_GAP);
        int visibleHeight = PANEL_H - 44;
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
