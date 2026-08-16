package com.kghua.npcai.client.screen;

import com.kghua.npcai.network.SendMailPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 管理员发布邮箱页面。
 */
public class SendMailScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 260;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private EditBox titleBox;
    private EditBox contentBox;
    private EditBox namesBox;
    private EditBox startBox;
    private EditBox endBox;

    private int sendMode = 0; // 0=全部, 1=白名单, 2=黑名单
    private Button modeButton;

    public SendMailScreen() {
        super(Component.literal("发布邮箱"));
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> onClose()));

        this.titleBox = new EditBox(this.font, px + 70, py + 36, 220, 16, Component.literal(""));
        this.titleBox.setMaxLength(64);
        this.addRenderableWidget(this.titleBox);

        this.contentBox = new EditBox(this.font, px + 70, py + 58, 220, 16, Component.literal(""));
        this.contentBox.setMaxLength(256);
        this.addRenderableWidget(this.contentBox);

        this.modeButton = new NoShadowButton(px + 70, py + 80, 100, 18,
            Component.literal(modeLabel()), btn -> toggleMode());
        this.addRenderableWidget(this.modeButton);

        this.namesBox = new EditBox(this.font, px + 70, py + 104, 220, 16, Component.literal(""));
        this.namesBox.setMaxLength(256);
        this.namesBox.setHint(Component.literal("逗号分隔玩家名"));
        this.addRenderableWidget(this.namesBox);

        LocalDateTime now = LocalDateTime.now();
        this.startBox = new EditBox(this.font, px + 70, py + 128, 100, 16, Component.literal(""));
        this.startBox.setValue(now.format(FORMATTER));
        this.addRenderableWidget(this.startBox);

        this.endBox = new EditBox(this.font, px + 205, py + 128, 100, 16, Component.literal(""));
        this.endBox.setValue(now.plusDays(7).format(FORMATTER));
        this.addRenderableWidget(this.endBox);

        this.addRenderableWidget(new NoShadowButton(px + 70, py + 220, 70, 20,
            Component.literal("发布"), btn -> submit()));
        this.addRenderableWidget(new NoShadowButton(px + 180, py + 220, 70, 20,
            Component.literal("取消"), btn -> onClose()));
    }

    private void toggleMode() {
        sendMode = (sendMode + 1) % 3;
        if (modeButton != null) {
            modeButton.setMessage(Component.literal(modeLabel()));
        }
    }

    private String modeLabel() {
        return switch (sendMode) {
            case 0 -> "发送：全部";
            case 1 -> "发送：白名单";
            case 2 -> "发送：黑名单";
            default -> "发送：全部";
        };
    }

    private void submit() {
        String title = titleBox.getValue().trim();
        String content = contentBox.getValue().trim();
        if (title.isEmpty() || content.isEmpty()) {
            sendHint("§c标题和内容不能为空");
            return;
        }

        // 命令奖励已移除，身份卡奖励以编辑区为准（此旧屏幕不再提供身份卡输入，奖励全0）
        int[] cards = new int[4];

        List<String> names = new ArrayList<>();
        if (sendMode != 0) {
            String namesText = namesBox.getValue().trim();
            if (!namesText.isEmpty()) {
                names.addAll(Arrays.asList(namesText.split(",")));
                names.replaceAll(String::trim);
            }
        }

        long startAt = parseTime(startBox.getValue().trim());
        long endAt = parseTime(endBox.getValue().trim());
        long expiresAt = endAt > startAt ? endAt : 0;

        ClientPlayNetworking.send(new SendMailPacket(title, content, cards, sendMode, names, startAt, expiresAt, 0));
        onClose();
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
        graphics.drawString(this.font, Component.literal("发布邮箱"), px + 70, py + 14, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("标题"), px + 10, py + 40, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("内容"), px + 10, py + 62, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("名单"), px + 10, py + 108, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("开始"), px + 10, py + 132, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("结束"), px + 170, py + 132, 0xFFFFFFFF, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
