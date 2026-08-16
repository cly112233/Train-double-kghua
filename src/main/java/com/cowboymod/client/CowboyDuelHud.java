package com.cowboymod.client;

import com.cowboymod.CowboyMod;
import com.cowboymod.WesternCowboyComponent;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CowboyDuelHud {

    public static void register() {
        // Only renders for cowboy players
        org.agmas.noellesroles.client.event.RoleHudRenderCallback.EVENT.register(
                CowboyMod.COWBOY_ROLE_ID,
                (FakeGuiGraphics g, net.minecraft.client.DeltaTracker tickDelta) -> render(g)
        );
    }

    private static void render(FakeGuiGraphics g) {
        var c = Minecraft.getInstance();
        if (c.player == null) return;
        var comp = WesternCowboyComponent.get(c.player);

        int sw = g.guiWidth();
        int sh = g.guiHeight();

        // 3-2-1 countdown
        if (comp.getDuelState() == WesternCowboyComponent.DuelState.COUNTDOWN) {
            int s = (comp.getCountdownTicks() + 19) / 20;
            String txt = String.valueOf(Math.max(1, s));
            g.drawString(c.font, Component.literal(txt),
                    sw / 2 - c.font.width(txt) / 2, sh / 2 - 40, 0xFFFFAA00);
        }

        // 35s fight timer
        if (comp.getDuelState() == WesternCowboyComponent.DuelState.FIGHTING) {
            int s = (comp.getDuelTimerTicks() + 19) / 20;
            String txt = String.valueOf(s);
            g.drawString(c.font, Component.literal(txt),
                    sw / 2 - c.font.width(txt) / 2, sh - 48, 0xFFFFFFFF);
        }

        // Shield layers from SREHumanoidArmorPlayerComponent
        int shields = comp.getShieldLayers();
        if (shields > 0) {
            String txt = "🛡 x" + shields;
            g.drawString(c.font, Component.literal(txt),
                    sw - c.font.width(txt) - 10, sh - 56, 0xFF55FFFF);
        }
    }
}
