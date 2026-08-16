package xiao.hua.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import io.wifi.starrailexpress.content.item.KnifeItem;
import xiao.hua.init.HuaItems;

public class KnifeCrosshairRenderer {
    private static final ResourceLocation CROSSHAIR = SRE.watheId("hud/crosshair");
    private static final ResourceLocation CROSSHAIR_TARGET = SRE.watheId("hud/crosshair_target");

    public static void renderCrosshair(@NotNull Minecraft client, @NotNull LocalPlayer player, GuiGraphics context) {
        if (!client.options.getCameraType().isFirstPerson()) return;
        
        boolean target = false;
        context.pose().pushPose();
        context.pose().translate(context.guiWidth() / 2f - 1.5f, context.guiHeight() / 2f - 1.5f, 0);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, 
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, 
                GlStateManager.SourceFactor.ONE, 
                GlStateManager.DestFactor.ZERO);
        
        ItemStack mainHandStack = player.getMainHandItem();
        
        if (mainHandStack.is(HuaItems.VENGEANCE_KNIFE)) {
            var manager = player.getCooldowns();
            
            if (!manager.isOnCooldown(HuaItems.VENGEANCE_KNIFE) && isKnifeTargetingEntity(player)) {
                target = true;
            }
        }
        
        if (target) {
            context.blitSprite(CROSSHAIR_TARGET, 0, 0, 3, 3);
        } else {
            context.blitSprite(CROSSHAIR, 0, 0, 3, 3);
        }
        
        context.pose().popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static boolean isKnifeTargetingEntity(Player player) {
        HitResult result = KnifeItem.getKnifeTarget(player);
        return result instanceof EntityHitResult;
    }
}