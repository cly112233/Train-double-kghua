package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.MailRecord;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 邮件详情页：展示已发送邮件的完整信息。
 */
public class MailDetailScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 260;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MailRecord mail;

    public MailDetailScreen(MailRecord mail) {
        super(Component.literal("邮件详情"));
        this.mail = mail;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 125, py + PANEL_H - 32, 70, 20,
            Component.literal("返回"), btn -> onClose()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("邮件详情"), px + 10, py + 10, 0xFFFFFFFF, false);

        int line = py + 36;
        int lineHeight = 16;

        graphics.drawString(this.font, Component.literal("标题：" + mail.getTitle()), px + 12, line, 0xFFFFFFFF, false);
        line += lineHeight;

        String content = mail.getContent();
        if (content.isEmpty()) content = "（无内容）";
        graphics.drawString(this.font, Component.literal("内容：" + content), px + 12, line, 0xFFFFFFFF, false);
        line += lineHeight;

        String rewards = buildRewardSummary(mail);
        if (rewards.isEmpty()) rewards = "（无）";
        graphics.drawString(this.font, Component.literal("身份卡奖励：" + rewards), px + 12, line, 0xFFFFFFFF, false);
        line += lineHeight;

        String mode = switch (mail.getSendMode()) {
            case 0 -> "全部";
            case 1 -> "白名单";
            case 2 -> "黑名单";
            default -> "全部";
        };
        graphics.drawString(this.font, Component.literal("发送模式：" + mode), px + 12, line, 0xFFFFFFFF, false);
        line += lineHeight;

        String names = String.join(", ", mail.getPlayerNames());
        if (names.isEmpty()) names = "（无）";
        String nameLabel = "玩家名单：" + names;
        if (this.font.width(nameLabel) > PANEL_W - 24) {
            nameLabel = this.font.plainSubstrByWidth(nameLabel, PANEL_W - 24) + "...";
        }
        graphics.drawString(this.font, Component.literal(nameLabel), px + 12, line, 0xFFFFFFFF, false);
        line += lineHeight;

        graphics.drawString(this.font, Component.literal("开始时间：" + formatTime(mail.getStartAt())), px + 12, line, 0xFFFFFFFF, false);
        line += lineHeight;

        graphics.drawString(this.font, Component.literal("结束时间：" + formatTime(mail.getEndAt())), px + 12, line, 0xFFFFFFFF, false);
        line += lineHeight;

        graphics.drawString(this.font, Component.literal("发送时间：" + formatTime(mail.getSentAt())), px + 12, line, 0xFFFFFFFF, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    /** 构建身份卡+抽奖奖励摘要文本（全部为0返回空字符串） */
    private String buildRewardSummary(MailRecord mail) {
        int[] cards = mail.getCards();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4 && i < cards.length; i++) {
            if (cards[i] > 0) {
                sb.append(com.kghua.npcai.data.ContributionRewardSettings.CARD_LABELS[i])
                    .append("×").append(cards[i]).append(" ");
            }
        }
        if (mail.getLotteryCount() > 0) {
            sb.append("抽奖次数×").append(mail.getLotteryCount());
        }
        return sb.toString().trim();
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "无";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.ofHours(8)).format(FORMATTER);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
