package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.network.BindMailQuestionnairePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 问卷绑定弹窗：以卡片形式显示所有结束时间未到的问卷（不论是否已到开始时间），
 * 点击卡片即绑定（携带管理端当前邮件编辑区的模板快照），返回主面板后按钮显示当前绑定的问卷。
 */
public class MailBindScreen extends Screen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 300;
    private static final int CARD_H = 40;
    private static final int CARD_GAP = 4;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NpcAdminScreen parentScreen;
    private final List<Questionnaire> questionnaires = new ArrayList<>();
    private double scrollOffset = 0;
    private Button unbindButton;

    public MailBindScreen(NpcAdminScreen parentScreen, List<Questionnaire> questionnaires) {
        super(Component.literal("问卷绑定"));
        this.parentScreen = parentScreen;
        this.questionnaires.addAll(questionnaires);
    }

    /** 服务端同步问卷数据（打开弹窗时自动请求刷新） */
    public void setQuestionnaires(List<Questionnaire> list) {
        this.questionnaires.clear();
        for (Questionnaire q : list) {
            this.questionnaires.add(q);
        }
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        // 刷新问卷列表（结束时间未到的问卷才会展示）
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.kghua.npcai.network.RequestQuestionnairesPacket());

        // 解除绑定按钮（当前有绑定时显示）
        this.unbindButton = new NoShadowButton(px + PANEL_W - 108, py + 10, 100, 18,
            Component.literal("解除绑定"), btn -> unbind());
        this.unbindButton.visible = !parentScreen.getBoundMailQuestionnaireId().isEmpty();
        this.addRenderableWidget(this.unbindButton);
    }

    private void backToParent() {
        if (this.minecraft != null && parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            onClose();
        }
    }

    private void unbind() {
        ClientPlayNetworking.send(new BindMailQuestionnairePacket("", "", "", new int[4], 0, 0));
        parentScreen.setMailBinding("", "");
        backToParent();
    }

    private void bind(Questionnaire q) {
        // 仅本地选中问卷（编辑区内容不受影响）；发布（发送模式=绑定问卷）时才把模板快照发给服务端保存
        parentScreen.setMailBinding(q.getId().toString(), q.getTitle());
        backToParent();
    }

    /** 结束时间之前（未结束）的问卷才可绑定，不论开始时间 */
    private List<Questionnaire> bindable() {
        long now = System.currentTimeMillis();
        List<Questionnaire> list = new ArrayList<>();
        for (Questionnaire q : questionnaires) {
            if (q.getEndAt() == 0 || q.getEndAt() > now) {
                list.add(q);
            }
        }
        return list;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("问卷绑定"), px + PANEL_W / 2 - 40, py + 14, 0xFFFFFFFF, false);

        // 当前绑定状态
        String boundId = parentScreen.getBoundMailQuestionnaireId();
        String boundTitle = parentScreen.getBoundMailQuestionnaireTitle();
        String status = boundId.isEmpty() ? "当前未选择问卷" : "当前选择：" + boundTitle;
        graphics.drawString(this.font, Component.literal(status), px + 70, py + 14, 0xFFAAAAAA, false);

        // 卡片列表（结束时间之前的所有问卷）
        List<Questionnaire> list = bindable();
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int contentTop = py + 36;
        int contentH = PANEL_H - 46;
        int totalHeight = list.size() * (CARD_H + CARD_GAP);
        if (totalHeight > contentH && scrollOffset > totalHeight - contentH) {
            scrollOffset = Math.max(0, totalHeight - contentH);
        }
        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < list.size(); i++) {
            Questionnaire q = list.get(i);
            int cardY = currentY + i * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < contentTop || cardY > contentTop + contentH) continue;

            boolean selected = q.getId().toString().equals(boundId);
            if (selected) {
                TrainStyleRenderHelper.renderSelectedCard(graphics, contentX, cardY, contentW, CARD_H);
            } else {
                TrainStyleRenderHelper.renderCard(graphics, contentX, cardY, contentW, CARD_H);
            }

            String title = q.getTitle();
            if (this.font.width(title) > contentW - 110) {
                title = this.font.plainSubstrByWidth(title, contentW - 110) + "...";
            }
            graphics.drawString(this.font, Component.literal(selected ? "§a✓ " + title : title),
                contentX + 8, cardY + 6, selected ? 0x2E7D32 : 0x333333, false);

            String time = formatTime(q.getStartAt()) + " 至 " + formatTime(q.getEndAt()) + " | 已答 " + q.getResponses().size() + " 人";
            if (this.font.width(time) > contentW - 16) {
                time = this.font.plainSubstrByWidth(time, contentW - 16) + "...";
            }
            graphics.drawString(this.font, Component.literal(time), contentX + 8, cardY + 22, 0x666666, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "不限";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(TIME_FMT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            List<Questionnaire> list = bindable();
            int contentX = px + 10;
            int contentW = PANEL_W - 20;
            int contentTop = py + 36;
            int currentY = contentTop - (int) scrollOffset;
            for (int i = 0; i < list.size(); i++) {
                int cardY = currentY + i * (CARD_H + CARD_GAP);
                if (mouseY >= cardY && mouseY <= cardY + CARD_H
                    && mouseX >= contentX && mouseX <= contentX + contentW) {
                    bind(list.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentH = PANEL_H - 46;
        int totalHeight = bindable().size() * (CARD_H + CARD_GAP);
        if (totalHeight > contentH) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - scrollY * 12, totalHeight - contentH));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
