package com.kghua.npcai.client.screen;

import com.kghua.npcai.network.CreateQuestionnairePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 管理员添加问卷页面。
 */
public class AddQuestionnaireScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 260;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private EditBox titleBox;
    private final List<EditBox> questionBoxes = new ArrayList<>();
    private final List<EditBox> hintBoxes = new ArrayList<>();
    private EditBox startBox;
    private EditBox endBox;
    private final Screen parentScreen;

    public AddQuestionnaireScreen() {
        this(null);
    }

    public AddQuestionnaireScreen(Screen parentScreen) {
        super(Component.literal("添加问卷"));
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

        this.titleBox = new EditBox(this.font, px + 70, py + 28, 220, 16, Component.literal(""));
        this.titleBox.setMaxLength(64);
        this.addRenderableWidget(this.titleBox);

        for (int i = 0; i < 6; i++) {
            EditBox qBox = new EditBox(this.font, px + 70, py + 52 + i * 22, 130, 16, Component.literal(""));
            qBox.setMaxLength(128);
            qBox.setHint(Component.literal("问题" + (i + 1)));
            this.questionBoxes.add(qBox);
            this.addRenderableWidget(qBox);

            EditBox hBox = new EditBox(this.font, px + 205, py + 52 + i * 22, 85, 16, Component.literal(""));
            hBox.setMaxLength(128);
            hBox.setHint(Component.literal("提示"));
            this.hintBoxes.add(hBox);
            this.addRenderableWidget(hBox);
        }

        LocalDateTime now = LocalDateTime.now();
        this.startBox = new EditBox(this.font, px + 70, py + 188, 100, 16, Component.literal(""));
        this.startBox.setValue(now.format(FORMATTER));
        this.addRenderableWidget(this.startBox);

        this.endBox = new EditBox(this.font, px + 205, py + 188, 100, 16, Component.literal(""));
        this.endBox.setValue(now.plusDays(7).format(FORMATTER));
        this.addRenderableWidget(this.endBox);

        this.addRenderableWidget(new NoShadowButton(px + 70, py + 220, 70, 20,
            Component.literal("发布"), btn -> submit()));
        this.addRenderableWidget(new NoShadowButton(px + 180, py + 220, 70, 20,
            Component.literal("取消"), btn -> backToParent()));
    }

    private void submit() {
        String title = titleBox.getValue().trim();
        if (title.isEmpty()) {
            sendHint("§c问卷标题不能为空");
            return;
        }

        List<String> questions = new ArrayList<>();
        List<String> hints = new ArrayList<>();
        for (int i = 0; i < questionBoxes.size(); i++) {
            String q = questionBoxes.get(i).getValue().trim();
            if (!q.isEmpty()) {
                questions.add(q);
                hints.add(hintBoxes.get(i).getValue().trim());
            }
        }

        if (questions.isEmpty()) {
            sendHint("§c至少填写一个问题");
            return;
        }

        long startAt = parseTime(startBox.getValue().trim());
        long endAt = parseTime(endBox.getValue().trim());

        ClientPlayNetworking.send(new CreateQuestionnairePacket(
            UUID.randomUUID(), title, questions, hints, startAt, endAt
        ));
        backToParent();
    }

    private long parseTime(String text) {
        try {
            return LocalDateTime.parse(text, FORMATTER).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private void sendHint(String msg) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal(msg));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("添加问卷"), px + 10, py + 10, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("标题"), px + 10, py + 32, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("问题 / 提示"), px + 10, py + 56, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("开始"), px + 10, py + 192, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("结束"), px + 170, py + 192, 0xFFFFFFFF, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
