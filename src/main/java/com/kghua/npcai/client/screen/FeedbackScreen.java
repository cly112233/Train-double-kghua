package com.kghua.npcai.client.screen;

import com.kghua.npcai.network.SubmitFeedbackPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FeedbackScreen extends Screen {
    private final int entityId;
    private final String npcName;
    private MultiLineEditBox contentBox;
    private boolean anonymous = false;
    private Button anonymousButton;

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 220;

    public FeedbackScreen(int entityId, String npcName) {
        super(Component.literal("反馈建议"));
        this.entityId = entityId;
        this.npcName = npcName;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.contentBox = new MultiLineEditBox(
            this.font,
            px + 10,
            py + 68,
            PANEL_W - 20,
            110,
            Component.empty(),
            Component.literal("")
        );
        this.contentBox.setValue("");
        this.contentBox.setFocused(true);
        this.addRenderableWidget(this.contentBox);

        this.anonymousButton = new NoShadowButton(px + 10, py + 30, 80, 20,
            Component.literal("匿名：关"), btn -> toggleAnonymous());
        this.addRenderableWidget(this.anonymousButton);

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToChat()));

        this.addRenderableWidget(new NoShadowButton(px + 115, py + PANEL_H - 30, 70, 20,
            Component.literal("提交"), btn -> submit()));
    }

    private void toggleAnonymous() {
        anonymous = !anonymous;
        anonymousButton.setMessage(Component.literal(anonymous ? "匿名：开" : "匿名：关"));
    }

    private void backToChat() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CustomerChatScreen(npcName, entityId));
        }
    }

    private void submit() {
        String content = contentBox.getValue().trim();
        if (content.isEmpty()) return;
        if (content.length() > 500) {
            content = content.substring(0, 500);
        }
        ClientPlayNetworking.send(new SubmitFeedbackPacket(entityId, anonymous, content));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        Component title = Component.literal("反馈建议");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, px + (PANEL_W - titleWidth) / 2, py + 12, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("反馈内容"), px + 10, py + 56, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("最多 500 字"), px + 100, py + 34, 0xFFCCCCCC, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
