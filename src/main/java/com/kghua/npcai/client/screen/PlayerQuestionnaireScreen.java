package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.network.HideQuestionnairePacket;
import com.kghua.npcai.network.RequestQuestionnairesPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 玩家端问卷列表：显示当前可填写的问卷。
 */
public class PlayerQuestionnaireScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 220;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 6;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final int entityId;
    private final String npcName;
    private final List<Questionnaire> questionnaires = new ArrayList<>();
    private double scrollOffset = 0;

    public PlayerQuestionnaireScreen(int entityId, String npcName) {
        super(Component.literal("问卷"));
        this.entityId = entityId;
        this.npcName = npcName;
    }

    @Override
    protected void init() {
        super.init();
        ClientPlayNetworking.send(new RequestQuestionnairesPacket());

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToChat()));
    }

    private void backToChat() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CustomerChatScreen(npcName, entityId));
        }
    }

    public void setQuestionnaires(List<Questionnaire> list) {
        this.questionnaires.clear();
        long now = System.currentTimeMillis();
        for (Questionnaire q : list) {
            if (q.isActive()) {
                this.questionnaires.add(q);
            }
        }
    }

    private boolean hasResponded(Questionnaire q) {
        if (this.minecraft == null || this.minecraft.player == null) return false;
        return q.hasResponded(this.minecraft.player.getName().getString());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        Component title = Component.literal("可填写问卷");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, px + (PANEL_W - titleWidth) / 2, py + 12, 0xFFFFFFFF, false);

        renderList(graphics, px + 10, py + 32, PANEL_W - 20, PANEL_H - 72, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderList(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        int totalHeight = questionnaires.size() * (CARD_H + CARD_GAP);
        if (totalHeight > h && scrollOffset > totalHeight - h) {
            scrollOffset = Math.max(0, totalHeight - h);
        }

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < questionnaires.size(); i++) {
            Questionnaire q = questionnaires.get(i);
            boolean responded = hasResponded(q);
            int cardY = currentY + i * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < y || cardY > y + h) continue;

            if (responded) {
                TrainStyleRenderHelper.renderSelectedCard(graphics, x, cardY, w, CARD_H);
            } else {
                TrainStyleRenderHelper.renderCard(graphics, x, cardY, w, CARD_H);
            }

            int titleMaxW = responded ? w - 44 : w - 16;
            // 绑定邮箱的问卷：标题旁显示（奖励）提示
            boolean rewarded = q.getId().toString()
                .equals(com.kghua.npcai.client.ClientCache.getBoundMailQuestionnaireId());
            String title = q.getTitle() + (rewarded ? "（奖励）" : "");
            if (this.font.width(title) > titleMaxW) {
                title = this.font.plainSubstrByWidth(title, titleMaxW) + "...";
            }
            int titleColor = rewarded ? 0xFFDAA520 : (responded ? 0xFF888888 : 0x333333);
            graphics.drawString(this.font, Component.literal(title), x + 8, cardY + 6, titleColor, false);

            String subText;
            int subColor;
            if (responded) {
                subText = "已填写 | 截止 " + formatTime(q.getEndAt());
                subColor = 0xFF888888;
            } else {
                subText = q.getQuestions().size() + " 个问题 | 截止 " + formatTime(q.getEndAt());
                subColor = 0x666666;
            }
            graphics.drawString(this.font, Component.literal(subText), x + 8, cardY + 22, subColor, false);

            if (responded) {
                int delW = 34;
                int delH = 14;
                int delX = x + w - delW - 2;
                int delY = cardY + 6;
                TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "删除", delX, delY, delW, delH,
                    true, isMouseOver(delX, delY, delW, delH, mouseX, mouseY));
            }
        }

        if (questionnaires.isEmpty()) {
            graphics.drawString(this.font, Component.literal("暂无有效问卷"), x + 8, y + 10, 0xFFCCCCCC, false);
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "无";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.ofHours(8)).format(FORMATTER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            int contentTop = py + 32;
            int contentX = px + 10;
            int contentW = PANEL_W - 20;
            int currentY = contentTop - (int) scrollOffset;

            for (int i = 0; i < questionnaires.size(); i++) {
                Questionnaire q = questionnaires.get(i);
                int cardY = currentY + i * (CARD_H + CARD_GAP);
                if (mouseY < cardY || mouseY > cardY + CARD_H
                    || mouseX < contentX || mouseX > contentX + contentW) {
                    continue;
                }
                if (hasResponded(q)) {
                    int delX = contentX + contentW - 36;
                    int delY = cardY + 6;
                    if (mouseX >= delX && mouseX <= delX + 34 && mouseY >= delY && mouseY <= delY + 14) {
                        ClientPlayNetworking.send(new HideQuestionnairePacket(q.getId()));
                        questionnaires.remove(i);
                        if (scrollOffset > 0) {
                            int totalHeight = questionnaires.size() * (CARD_H + CARD_GAP);
                            int visibleHeight = PANEL_H - 72;
                            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, totalHeight - visibleHeight)));
                        }
                    }
                    return true;
                }
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new PlayerQuestionnaireFillScreen(entityId, npcName, q));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalHeight = questionnaires.size() * (CARD_H + CARD_GAP);
        int visibleHeight = PANEL_H - 72;
        if (totalHeight > visibleHeight) {
            scrollOffset -= scrollY * 15;
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - visibleHeight));
        }
        return true;
    }

    private boolean isMouseOver(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
