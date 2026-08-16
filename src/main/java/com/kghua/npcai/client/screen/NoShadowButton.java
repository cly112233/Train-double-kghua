package com.kghua.npcai.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 不带文字阴影的按钮，使用原版 widget 按钮精灵渲染。
 */
public class NoShadowButton extends Button {
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");

    public NoShadowButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation texture;
        if (!this.active) {
            texture = BUTTON_DISABLED;
        } else if (this.isHoveredOrFocused()) {
            texture = BUTTON_HIGHLIGHTED;
        } else {
            texture = BUTTON;
        }
        graphics.blitSprite(texture, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        int color = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
        this.renderString(graphics, Minecraft.getInstance().font, color);
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        Component message = this.getMessage();
        int textWidth = font.width(message);
        int y = this.getY() + (this.getHeight() - 8) / 2;
        int padding = 2;
        int minX = this.getX() + padding;
        int maxX = this.getX() + this.getWidth() - padding;
        int availableWidth = maxX - minX;

        if (textWidth <= availableWidth) {
            int x = this.getX() + (this.getWidth() - textWidth) / 2;
            graphics.drawString(font, message, x, y, color, false);
        } else {
            // 文字超长时滚动显示，仍不带阴影
            int overflow = textWidth - availableWidth;
            double time = System.nanoTime() / 1_000_000_000.0;
            double scrollOffset = (Math.sin(time / 1.5) + 1.0) / 2.0 * overflow;
            graphics.enableScissor(minX, this.getY(), maxX, this.getY() + this.getHeight());
            graphics.drawString(font, message, minX - (int) scrollOffset, y, color, false);
            graphics.disableScissor();
        }
    }
}
