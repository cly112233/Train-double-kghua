package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.network.DeleteQuestionnairePacket;
import com.kghua.npcai.network.ExportQuestionnairePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * 管理端问卷详情页：显示标题、问题与提示，不显示玩家填写记录。
 */
public class QuestionnaireDetailScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 260;

    private final Questionnaire questionnaire;
    private final Screen parentScreen;
    private double scrollOffset = 0;
    private boolean pendingDelete = false;
    private boolean deleteConfirmClicked = false;
    private boolean pendingDeleteConfirm = false;

    public QuestionnaireDetailScreen(Questionnaire questionnaire) {
        this(questionnaire, null);
    }

    public QuestionnaireDetailScreen(Questionnaire questionnaire, Screen parentScreen) {
        super(Component.literal("问卷详情"));
        this.questionnaire = questionnaire;
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

        // 左上角返回按钮（返回管理端）
        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        // 底部按钮
        this.addRenderableWidget(new NoShadowButton(px + 40, py + PANEL_H - 32, 80, 20,
            Component.literal("导出文档"), btn -> export()));
        this.addRenderableWidget(new NoShadowButton(px + 180, py + PANEL_H - 32, 80, 20,
            Component.literal("删除问卷"), btn -> pendingDelete = true));
    }

    public void updateQuestionnaire(List<Questionnaire> list) {
        UUID id = questionnaire.getId();
        for (Questionnaire q : list) {
            if (q.getId().equals(id)) {
                // 反射替换引用（简单起见直接重新赋值）
                // 由于 java 字段是 final，这里使用传入列表中找到的问卷
                return; // questionnaire 引用已在构造时设置，列表同步时无需替换
            }
        }
        // 问卷已被删除 -> 关闭详情页
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void export() {
        ClientPlayNetworking.send(new ExportQuestionnairePacket(questionnaire.getId()));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("§a已导出问卷文档"));
        }
    }

    private void deleteQuestionnaire() {
        ClientPlayNetworking.send(new DeleteQuestionnairePacket(questionnaire.getId()));
        pendingDelete = false;
        backToParent();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);

        // 标题居中
        String title = questionnaire.getTitle();
        if (title.isEmpty()) title = "未命名问卷";
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, Component.literal(title),
            px + (PANEL_W - titleWidth) / 2, py + 12, 0xFFFFFFFF, false);

        // 问题与提示列表
        renderQuestionList(graphics, px + 10, py + 32, PANEL_W - 20, PANEL_H - 72, mouseX, mouseY);

        // 删除确认弹窗
        if (pendingDelete) {
            renderDeleteConfirm(graphics, px + PANEL_W / 2 - 110, py + PANEL_H / 2 - 30, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderQuestionList(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<String> questions = questionnaire.getQuestions();
        List<String> hints = questionnaire.getHints();

        if (questions.isEmpty()) {
            graphics.drawString(this.font, Component.literal("暂无问题"), x + 8, y + 10, 0x666666, false);
            return;
        }

        int totalHeight = 0;
        for (int i = 0; i < questions.size(); i++) {
            totalHeight += questionItemHeight(i);
        }
        if (totalHeight > h && scrollOffset > totalHeight - h) {
            scrollOffset = Math.max(0, totalHeight - h);
        }

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < questions.size(); i++) {
            int itemH = questionItemHeight(i);
            if (currentY + itemH < y || currentY > y + h) {
                currentY += itemH;
                continue;
            }

            TrainStyleRenderHelper.renderCard(graphics, x, currentY, w, itemH);

            String qText = (i + 1) + ". " + questions.get(i);
            List<String> qLines = splitText(qText, w - 16);
            int lineY = currentY + 6;
            for (String line : qLines) {
                graphics.drawString(this.font, Component.literal(line), x + 8, lineY, 0x333333, false);
                lineY += 12;
            }

            String hint = i < hints.size() ? hints.get(i) : "";
            if (!hint.isEmpty()) {
                String hText = "提示: " + hint;
                List<String> hLines = splitText(hText, w - 16);
                for (String line : hLines) {
                    graphics.drawString(this.font, Component.literal(line), x + 8, lineY, 0x666666, false);
                    lineY += 12;
                }
            }

            currentY += itemH;
        }
    }

    private List<String> splitText(String text, int maxWidth) {
        // 使用字体手动拆分，返回纯文本行列表
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (char c : text.toCharArray()) {
            String test = currentLine.toString() + c;
            if (this.font.width(test) > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder().append(c);
            } else {
                currentLine.append(c);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private int questionItemHeight(int index) {
        List<String> questions = questionnaire.getQuestions();
        List<String> hints = questionnaire.getHints();
        String q = (index + 1) + ". " + questions.get(index);
        int lines = splitText(q, PANEL_W - 36).size();

        String hint = index < hints.size() ? hints.get(index) : "";
        if (!hint.isEmpty()) {
            lines += splitText("提示: " + hint, PANEL_W - 36).size();
        }

        return 12 + lines * 12;
    }

    private void renderDeleteConfirm(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        TrainStyleRenderHelper.renderPanel(graphics, x, y, 220, 60);
        graphics.drawString(this.font, Component.literal("确认删除该问卷？"), x + 10, y + 10, 0xFFFFFFFF, false);

        int my = y + 32;
        int confirmX = x + 40;
        int cancelX = x + 120;
        TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "确认", confirmX, my, 50, 20,
            true, isMouseOver(confirmX, my, 50, 20, mouseX, mouseY));
        TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "取消", cancelX, my, 50, 20,
            true, isMouseOver(cancelX, my, 50, 20, mouseX, mouseY));

        if (deleteConfirmClicked) {
            deleteConfirmClicked = false;
            if (pendingDeleteConfirm) {
                pendingDeleteConfirm = false;
                deleteQuestionnaire();
            } else {
                pendingDelete = false;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingDelete) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            int x = px + PANEL_W / 2 - 110;
            int y = py + PANEL_H / 2 - 30;
            int my = y + 32;
            int confirmX = x + 40;
            int cancelX = x + 120;
            if (mouseY >= my && mouseY <= my + 20) {
                if (mouseX >= confirmX && mouseX <= confirmX + 50) {
                    deleteConfirmClicked = true;
                    pendingDeleteConfirm = true;
                } else if (mouseX >= cancelX && mouseX <= cancelX + 50) {
                    deleteConfirmClicked = true;
                    pendingDeleteConfirm = false;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<String> questions = questionnaire.getQuestions();
        int totalHeight = 0;
        for (int i = 0; i < questions.size(); i++) {
            totalHeight += questionItemHeight(i);
        }
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
