package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Contribution;
import com.kghua.npcai.network.LikeContributionPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 投稿查看页：显示内容 + 投稿玩家id + 点赞/取消点赞按钮。
 */
public class ContributionViewScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 280;

    private final Contribution contribution;
    private final ContributionBrowseScreen browseScreen;
    private double scrollOffset = 0;

    public ContributionViewScreen(Contribution contribution, ContributionBrowseScreen browseScreen) {
        super(Component.literal("投稿详情"));
        this.contribution = contribution;
        this.browseScreen = browseScreen;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(browseScreen);
                }
            }));

        // 不能给自己的投稿点赞（按钮置灰不可点击）
        boolean isOwn = this.minecraft != null && this.minecraft.player != null
            && contribution.getAuthorId().equals(this.minecraft.player.getUUID());
        NoShadowButton likeBtn = new NoShadowButton(px + (PANEL_W - 80) / 2, py + PANEL_H - 32, 80, 20,
            Component.literal(likeButtonLabel()), btn -> toggleLike());
        if (isOwn) {
            likeBtn.setMessage(Component.literal("不能给自己点赞"));
            likeBtn.active = false;
        }
        this.addRenderableWidget(likeBtn);
    }

    private String likeButtonLabel() {
        return contribution.getLikes() > 0 && browseScreen.isLiked(contribution.getId())
            ? "取消点赞" : "点赞";
    }

    private void toggleLike() {
        ClientPlayNetworking.send(new LikeContributionPacket(contribution.getId()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);

        String title = contribution.getTitle();
        if (this.font.width(title) > PANEL_W - 80) {
            title = this.font.plainSubstrByWidth(title, PANEL_W - 80) + "...";
        }
        graphics.drawString(this.font, Component.literal(title), px + 66, py + 12, 0xFFFFFFFF, false);

        int contentTop = py + 34;
        int contentH = PANEL_H - 74;
        int x = px + 10;
        int w = PANEL_W - 20;

        List<String> lines = new ArrayList<>();
        lines.add("投稿玩家: " + contribution.getAuthorName());
        if (!contribution.getFaction().isEmpty()) lines.add("阵营: " + contribution.getFaction());
        lines.add("点赞数: " + contribution.getLikes());
        if (!contribution.getShortDesc().isEmpty()) lines.add("简介: " + contribution.getShortDesc());
        if (!contribution.getDescription().isEmpty()) lines.add("描述: " + contribution.getDescription());
        if (!contribution.getShop().isEmpty()) lines.add("商店: " + contribution.getShop());
        if (!contribution.getBackground().isEmpty()) lines.add("背景: " + contribution.getBackground());

        int totalHeight = 0;
        for (String line : lines) {
            totalHeight += wrapLines(line, w - 8).size() * 11 + 2;
        }
        if (totalHeight > contentH && scrollOffset > totalHeight - contentH) {
            scrollOffset = Math.max(0, totalHeight - contentH);
        }

        int y = contentTop - (int) scrollOffset;
        for (String line : lines) {
            List<String> wrapped = wrapLines(line, w - 8);
            for (String wl : wrapped) {
                if (y + 11 >= contentTop && y <= contentTop + contentH) {
                    graphics.drawString(this.font, Component.literal(wl), x + 4, y, 0xFFFFFF, false);
                }
                y += 11;
            }
            y += 2;
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private List<String> wrapLines(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            String test = current.toString() + c;
            if (this.font.width(test) > maxWidth && current.length() > 0) {
                result.add(current.toString());
                current = new StringBuilder().append(c);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= scrollY * 15;
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
