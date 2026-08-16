package xiao.hua.client.hud;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import xiao.hua.Huarolemods;
import xiao.hua.roles.VengeanceAgentComponent;

import java.util.UUID;

public class VengeanceAgentHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(Huarolemods.id("vengeance_agent"), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null)
                return;
            VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(player);
            if (component == null || !component.isLensEnabled())
                return;
            context.pose().pushPose();
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int x = 8;
            int y = screenHeight - 52;
            if (component.getCurrentLensTarget() != null) {
                String targetName = getPlayerName(component.getCurrentLensTarget());
                Component targetText = Component.translatable("hud.huarolemods.vengeance_agent.target", targetName).withStyle(ChatFormatting.DARK_RED);
                context.drawString(client.font, targetText, x, y, -1);
            } else {
                Component noTargetText = Component.translatable("hud.huarolemods.vengeance_agent.no_target").withStyle(ChatFormatting.GRAY);
                context.drawString(client.font, noTargetText, x, y, -8355712);
            }
            Component hintText = Component.translatable("hud.huarolemods.vengeance_agent.hint").withStyle(ChatFormatting.GRAY);
            context.drawString(client.font, hintText, x, y + 12, -8355712);
            long cooldownRemaining = component.getLensCooldownRemaining();
            if (cooldownRemaining > 0L) {
                int seconds = (int) (cooldownRemaining / 1000L);
                Component cooldownText = Component.translatable("hud.huarolemods.vengeance_agent.cooldown", seconds).withStyle(ChatFormatting.RED);
                context.drawString(client.font, cooldownText, x, y + 24, -65536);
            } else {
                Component readyText = Component.translatable("hud.huarolemods.vengeance_agent.ready").withStyle(ChatFormatting.GREEN);
                context.drawString(client.font, readyText, x, y + 24, -16711936);
            }
            context.pose().popPose();
        });
    }

    private static String getPlayerName(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        for (net.minecraft.world.entity.player.Player onlinePlayer : client.level.players()) {
            if (onlinePlayer.getUUID().equals(uuid)) {
                return onlinePlayer.getName().getString();
            }
        }
        return uuid.toString().substring(0, 8);
    }
}