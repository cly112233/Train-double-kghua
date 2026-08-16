package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Contribution;
import com.kghua.npcai.network.ApproveContributionPacket;
import com.kghua.npcai.network.ExportQuestionnairePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端投稿详情页：显示投稿全部内容 + 导出按钮。
 */
public class ContributionDetailScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 280;

    private final Contribution contribution;
    private final Screen parentScreen;
    private double scrollOffset = 0;

    public ContributionDetailScreen(Contribution contribution, Screen parentScreen) {
        super(Component.literal("投稿详情"));
        this.contribution = contribution;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> {
                // 返回上一级页面（管理端），不是退出所有
                if (this.minecraft != null && parentScreen != null) {
                    this.minecraft.setScreen(parentScreen);
                } else {
                    onClose();
                }
            }));

        this.addRenderableWidget(new NoShadowButton(px + 10, py + PANEL_H - 32, 90, 20,
            Component.literal("导出文档"), btn -> export()));

        // 审核按钮（仅未审核作品显示；已审核通过的作品不再显示审核按钮）
        if (!contribution.isApproved()) {
            this.addRenderableWidget(new NoShadowButton(px + 106, py + PANEL_H - 32, 80, 20,
                Component.literal("审核通过"), btn -> approve(true)));
            this.addRenderableWidget(new NoShadowButton(px + 192, py + PANEL_H - 32, 80, 20,
                Component.literal("审核不通过"), btn -> approve(false)));
        }
    }

    /** 审核操作：发送审核请求 → 刷新管理端列表 → 返回面板 */
    private void approve(boolean approved) {
        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPlayNetworking.send(new ApproveContributionPacket(contribution.getId(), approved));
        }
        if (parentScreen instanceof NpcAdminScreen admin) {
            admin.requestContributionsRefresh();
        }
        if (this.minecraft != null && parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            onClose();
        }
    }

    private void export() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand(
                "playnpc exportcontribution " + contribution.getId());
            this.minecraft.player.sendSystemMessage(Component.literal("§a已请求导出投稿文档"));
        }
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

        // 构建详情行
        List<String> lines = new ArrayList<>();
        lines.add("类型: " + contribution.getType());
        if (!contribution.getFaction().isEmpty()) lines.add("阵营: " + contribution.getFaction());
        lines.add("投稿玩家: " + contribution.getAuthorName());
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
