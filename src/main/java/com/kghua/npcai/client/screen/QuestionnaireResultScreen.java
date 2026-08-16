package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.network.ExportQuestionnairePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 问卷结果查看页：展示每个玩家的填写记录。
 */
public class QuestionnaireResultScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 260;
    private static final int CARD_GAP = 6;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Questionnaire questionnaire;
    private double scrollOffset = 0;

    public QuestionnaireResultScreen(Questionnaire questionnaire) {
        super(Component.literal("问卷结果"));
        this.questionnaire = questionnaire;
    }

    public void updateQuestionnaire(List<Questionnaire> list) {
        UUID id = questionnaire.getId();
        for (Questionnaire q : list) {
            if (q.getId().equals(id)) {
                this.questionnaire = q;
                break;
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 70, py + PANEL_H - 32, 70, 20,
            Component.literal("导"), btn -> export()));
        this.addRenderableWidget(new NoShadowButton(px + 180, py + PANEL_H - 32, 70, 20,
            Component.literal("返回"), btn -> onClose()));
    }

    private void export() {
        ClientPlayNetworking.send(new ExportQuestionnairePacket(questionnaire.getId()));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("§a已导出问卷结果"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("问卷结果：" + questionnaire.getTitle()), px + 10, py + 10, 0xFFFFFFFF, false);

        renderResponseList(graphics, px + 10, py + 32, PANEL_W - 20, PANEL_H - 72);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderResponseList(GuiGraphics graphics, int x, int y, int w, int h) {
        List<Questionnaire.Response> responses = questionnaire.getResponses();
        int totalHeight = 0;
        for (Questionnaire.Response r : responses) {
            totalHeight += cardHeight(r) + CARD_GAP;
        }
        if (totalHeight > h && scrollOffset > totalHeight - h) {
            scrollOffset = Math.max(0, totalHeight - h);
        }

        int currentY = y - (int) scrollOffset;
        for (Questionnaire.Response r : responses) {
            int cardH = cardHeight(r);
            if (currentY + cardH >= y && currentY <= y + h) {
                TrainStyleRenderHelper.renderCard(graphics, x, currentY, w, cardH);
                graphics.drawString(this.font, Component.literal(r.playerName), x + 8, currentY + 6, 0x333333, false);
                graphics.drawString(this.font, Component.literal(formatTime(r.respondedAt)), x + 8, currentY + 18, 0x666666, false);

                List<String> questions = questionnaire.getQuestions();
                int lineY = currentY + 34;
                for (int i = 0; i < questions.size() && i < r.answers.size(); i++) {
                    String q = questions.get(i);
                    String a = r.answers.get(i);
                    if (a.isEmpty()) a = "（未填写）";

                    String qText = (i + 1) + ". " + q;
                    if (this.font.width(qText) > w - 16) {
                        qText = this.font.plainSubstrByWidth(qText, w - 16) + "...";
                    }
                    graphics.drawString(this.font, Component.literal(qText), x + 8, lineY, 0x333333, false);
                    lineY += 12;

                    String aText = "   " + a;
                    if (this.font.width(aText) > w - 16) {
                        aText = this.font.plainSubstrByWidth(aText, w - 16) + "...";
                    }
                    graphics.drawString(this.font, Component.literal(aText), x + 8, lineY, 0x666666, false);
                    lineY += 12;
                }
            }
            currentY += cardH + CARD_GAP;
        }

        if (responses.isEmpty()) {
            graphics.drawString(this.font, Component.literal("暂无填写记录"), x + 8, y + 10, 0x666666, false);
        }
    }

    private int cardHeight(Questionnaire.Response r) {
        int questionCount = Math.min(questionnaire.getQuestions().size(), r.answers.size());
        return 34 + questionCount * 24;
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "无";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.ofHours(8)).format(FORMATTER);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalHeight = 0;
        for (Questionnaire.Response r : questionnaire.getResponses()) {
            totalHeight += cardHeight(r) + CARD_GAP;
        }
        int visibleHeight = PANEL_H - 72;
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
