package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.network.SubmitQuestionnaireResponsePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家填写问卷页面。
 */
public class PlayerQuestionnaireFillScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 260;

    private final int entityId;
    private final String npcName;
    private final Questionnaire questionnaire;
    private final List<MultiLineEditBox> answerBoxes = new ArrayList<>();
    private final List<String> questionLabels = new ArrayList<>();
    private final List<Integer> questionLabelYs = new ArrayList<>();
    private final List<Integer> questionLabelBaseYs = new ArrayList<>();
    private final List<Integer> answerBoxBaseYs = new ArrayList<>();
    private double scrollOffset = 0;

    public PlayerQuestionnaireFillScreen(int entityId, String npcName, Questionnaire questionnaire) {
        super(Component.literal("填写问卷"));
        this.entityId = entityId;
        this.npcName = npcName;
        this.questionnaire = questionnaire;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToList()));

        int startY = py + 42;
        List<String> questions = questionnaire.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            String question = questions.get(i);
            String hint = questionnaire.getHint(i);
            questionLabels.add(question);
            questionLabelYs.add(startY);
            questionLabelBaseYs.add(startY);

            int boxY = startY + 12;
            answerBoxBaseYs.add(boxY);
            MultiLineEditBox box = new MultiLineEditBox(this.font, px + 10, boxY, PANEL_W - 20, 44, Component.literal(hint.isEmpty() ? "请输入答案" : hint), Component.literal(""));
            box.setValue("");
            this.answerBoxes.add(box);
            this.addRenderableWidget(box);
            startY += 70;
        }

        this.addRenderableWidget(new NoShadowButton(px + 120, py + PANEL_H - 28, 60, 20,
            Component.literal("提交"), btn -> submit()));

        applyScroll();
    }

    private void backToList() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new PlayerQuestionnaireScreen(entityId, npcName));
        }
    }

    private void submit() {
        List<String> answers = new ArrayList<>();
        for (MultiLineEditBox box : answerBoxes) {
            answers.add(box.getValue().trim());
        }
        ClientPlayNetworking.send(new SubmitQuestionnaireResponsePacket(questionnaire.getId(), answers));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        // 绑定邮箱的问卷：标题旁显示（奖励）提示
        boolean rewarded = questionnaire.getId().toString()
            .equals(com.kghua.npcai.client.ClientCache.getBoundMailQuestionnaireId());
        Component title = Component.literal(questionnaire.getTitle() + (rewarded ? "（奖励）" : ""));
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, px + (PANEL_W - titleWidth) / 2, py + 12,
            rewarded ? 0xFFDAA520 : 0xFFFFFFFF, false);

        int contentTop = py + 42;
        int contentBottom = py + PANEL_H - 28;

        for (int i = 0; i < questionLabels.size(); i++) {
            int labelY = questionLabelYs.get(i);
            if (labelY + 8 > contentTop && labelY < contentBottom) {
                graphics.drawString(this.font, Component.literal(questionLabels.get(i)), px + 10, labelY, 0x333333, false);
            }
        }

        renderScrollbar(graphics, px + PANEL_W - 6, contentTop, 4, contentBottom - contentTop);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int w, int h) {
        TrainStyleRenderHelper.renderScrollbar(graphics, x, y, w, h, scrollOffset, getTotalContentHeight(), h);
    }

    private void applyScroll() {
        if (answerBoxes.isEmpty()) return;
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int contentTop = py + 42;
        int contentBottom = py + PANEL_H - 28;
        int visibleHeight = contentBottom - contentTop;
        int totalHeight = getTotalContentHeight();

        if (totalHeight <= visibleHeight) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - visibleHeight));
        }

        for (int i = 0; i < answerBoxes.size(); i++) {
            MultiLineEditBox box = answerBoxes.get(i);
            int boxY = answerBoxBaseYs.get(i) - (int) scrollOffset;
            box.setY(boxY);
            box.visible = boxY + 44 > contentTop && boxY < contentBottom;
            questionLabelYs.set(i, questionLabelBaseYs.get(i) - (int) scrollOffset);
        }
    }

    private int getTotalContentHeight() {
        if (answerBoxBaseYs.isEmpty()) return 0;
        int firstLabelY = questionLabelBaseYs.get(0);
        int lastBoxY = answerBoxBaseYs.get(answerBoxBaseYs.size() - 1);
        return lastBoxY + 44 - firstLabelY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalHeight = getTotalContentHeight();
        int visibleHeight = PANEL_H - 70;
        if (totalHeight > visibleHeight) {
            scrollOffset -= scrollY * 15;
            applyScroll();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
