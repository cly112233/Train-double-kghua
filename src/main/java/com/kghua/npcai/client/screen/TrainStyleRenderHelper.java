package com.kghua.npcai.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 列车模组风格 UI 渲染辅助类。
 * 面板背景与边框颜色来自列车邮箱界面，卡片颜色按用户要求使用浅黄底+深黄边。
 */
public final class TrainStyleRenderHelper {
    // 列车邮箱界面同款深褐红背景
    public static final int PANEL_BG = 0xD81A1008;
    // 深黄色边框（用户指定）
    public static final int PANEL_BORDER = 0xFFCC9900;
    // 浅黄色卡片背景
    public static final int CARD_BG = 0xFFF0D878;
    // 选中/高亮卡片背景
    public static final int CARD_SELECTED_BG = 0xFFFFE0A0;
    // 深黄色卡片边框
    public static final int CARD_BORDER = 0xFFCC9900;

    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    private static final ResourceLocation TEXT_FIELD = ResourceLocation.withDefaultNamespace("widget/text_field");
    private static final ResourceLocation TEXT_FIELD_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/text_field_highlighted");
    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("widget/scroller");

    private TrainStyleRenderHelper() {
    }

    public static void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        graphics.renderOutline(x, y, width, height, PANEL_BORDER);
    }

    public static void renderCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, CARD_BG);
        graphics.renderOutline(x, y, width, height, CARD_BORDER);
    }

    public static void renderSelectedCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, CARD_SELECTED_BG);
        graphics.renderOutline(x, y, width, height, CARD_BORDER);
    }

    public static void renderPanelTitle(GuiGraphics graphics, Font font, String title, int x, int y, int width) {
        int titleWidth = font.width(title);
        graphics.drawString(font, Component.literal(title), x + (width - titleWidth) / 2, y, 0xFFFFFFFF, false);
    }

    public static void renderTextField(GuiGraphics graphics, EditBox editBox, int x, int y, int width, int height) {
        ResourceLocation texture = (editBox != null && editBox.isFocused()) ? TEXT_FIELD_HIGHLIGHTED : TEXT_FIELD;
        graphics.blitSprite(texture, x, y, width, height);
    }

    public static void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height,
                                       double scrollOffset, int contentHeight, int visibleHeight) {
        if (contentHeight <= visibleHeight) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, 0x66201C18);
        int thumbH = Math.max(16, visibleHeight * height / contentHeight);
        int maxScroll = Math.max(1, contentHeight - visibleHeight);
        int thumbY = y + (int) (scrollOffset * (height - thumbH) / maxScroll);
        graphics.blitSprite(SCROLLER, x, thumbY, width, thumbH);
    }

    public static void renderInlineButton(GuiGraphics graphics, Font font, String text,
                                          int x, int y, int width, int height,
                                          boolean active, boolean hovered) {
        ResourceLocation texture;
        if (!active) {
            texture = BUTTON_DISABLED;
        } else if (hovered) {
            texture = BUTTON_HIGHLIGHTED;
        } else {
            texture = BUTTON;
        }
        graphics.blitSprite(texture, x, y, width, height);
        int color = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        int textWidth = font.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        if (textWidth <= width - 4) {
            graphics.drawString(font, Component.literal(text), textX, textY, color, false);
        } else {
            graphics.enableScissor(x + 2, y, x + width - 2, y + height);
            graphics.drawString(font, Component.literal(text), x + 2, textY, color, false);
            graphics.disableScissor();
        }
    }
}
