package xiao.hua.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xiao.hua.client.gui.CooldownRenderer;
import xiao.hua.client.gui.KnifeCrosshairRenderer;

@Mixin(Gui.class)
public class IngameGuiMixin {

    @Shadow
    protected Minecraft minecraft;

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void renderCustomCrosshair(GuiGraphics context, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player != null && player.getMainHandItem().is(xiao.hua.init.HuaItems.VENGEANCE_KNIFE)) {
            KnifeCrosshairRenderer.renderCrosshair(this.minecraft, player, context);
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderKnifeCooldown(GuiGraphics context, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player != null && player.getMainHandItem().is(xiao.hua.init.HuaItems.VENGEANCE_KNIFE)) {
            CooldownRenderer.renderHud(player, context);
        }
    }
}