package com.cowboymod.client;

import com.cowboymod.WesternCowboyComponent;
import com.cowboymod.network.CowboyDuelPacket;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.UUID;

public class CowboyPlayerWidget extends Button {

    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerInfo targetPlayerEntry;

    public CowboyPlayerWidget(LimitedInventoryScreen screen, int x, int y,
                               UUID uuid, PlayerInfo entry, int index) {
        super(x, y, 16, 16, Component.empty(),
                btn -> {},
                Button.DEFAULT_NARRATION);
        this.screen = screen;
        this.targetUUID = uuid;
        this.targetPlayerEntry = entry;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        // Client-side validation
        var target = client.level.players().stream()
                .filter(p -> p.getUUID().equals(targetUUID)).findFirst().orElse(null);
        if (target == null) {
            client.player.displayClientMessage(Component.literal("§c目标玩家不在范围内"), true);
            return;
        }
        if (client.player.distanceToSqr(target) > 18.0 * 18.0) {
            client.player.displayClientMessage(Component.literal("§c目标太远（18格内）"), true);
            return;
        }

        ClientPlayNetworking.send(new CowboyDuelPacket(targetUUID));
        client.setScreen(null);
    }

    @Override
    protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.renderWidget(ctx, mouseX, mouseY, delta);

        var client = Minecraft.getInstance();
        if (client.player == null || targetPlayerEntry == null) return;

        var skin = targetPlayerEntry.getSkin();
        if (skin == null || client.font == null) return;
        var skinId = skin.texture();
        if (skinId == null) return;

        // Check cooldown (shared static map in singleplayer)
        var comp = WesternCowboyComponent.get(client.player);
        int cd = comp.getCooldownTicks();
        boolean onCd = cd > 0;

        if (onCd) {
            ctx.setColor(0.25f, 0.25f, 0.25f, 0.5f);
        }

        // Tool background — same as Voodoo: blitSprite with ShopEntry.Type.TOOL texture
        ctx.blitSprite(ShopEntry.Type.TOOL.getTexture(), getX() - 7, getY() - 7, 30, 30);

        // 16x16 player head using PlayerFaceRenderer
        PlayerFaceRenderer.draw(ctx, skinId, getX(), getY(), 16);

        // Restore opacity
        ctx.setColor(1f, 1f, 1f, 1f);

        // Hover: highlight + tooltip name (same as Voodoo)
        if (isHovered()) {
            this.drawShopSlotHighlight(ctx, getX(), getY(), 0);
            String name = targetPlayerEntry.getProfile().getName();
            if (name != null && !name.isEmpty()) {
                ctx.renderTooltip(client.font, Component.nullToEmpty(name), getX() - 4 - 10, getY() - 9);
            }
        }

        // Cooldown countdown (red text, like Voodoo)
        if (onCd) {
            int seconds = (cd + 19) / 20;
            String txt = String.valueOf(seconds);
            ctx.drawString(client.font, txt,
                    getX(), getY(), Color.RED.getRGB(), true);
        }
    }

    private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
        int color = -1862287543;
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, z);
        context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, z);
        context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, z);
    }
}
