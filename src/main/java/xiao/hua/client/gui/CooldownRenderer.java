package xiao.hua.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class CooldownRenderer {

    public static void renderHud(@NotNull LocalPlayer player, GuiGraphics context) {
        int selectedSlot = player.getInventory().selected;
        ItemStack heldStack = player.getInventory().getItem(selectedSlot);

        if (heldStack.isEmpty()) return;

        Item heldItem = heldStack.getItem();
        var manager = player.getCooldowns();

        if (!manager.isOnCooldown(heldItem)) return;

        Object cooldownsField = getField(manager, "cooldowns");
        if (!(cooldownsField instanceof java.util.Map)) return;
        
        @SuppressWarnings("unchecked")
        java.util.Map<Item, Object> cooldowns = (java.util.Map<Item, Object>) cooldownsField;
        Object entry = cooldowns.get(heldItem);
        if (entry == null) return;

        long endTick = getLongField(entry, "endTime");
        int tick = getIntField(manager, "tickCount");
        
        float remainingTicks = endTick - (tick + 1.0F);
        if (remainingTicks <= 0f) return;

        float remainingSeconds = remainingTicks / 20f;

        String timeText;
        if (remainingSeconds >= 60f) {
            int minutes = (int) (remainingSeconds / 60f);
            int seconds = (int) Math.ceil(remainingSeconds % 60f);
            if (seconds == 60) {
                minutes++;
                seconds = 0;
            }
            timeText = String.format("%d:%02d", minutes, seconds);
        } else if (remainingSeconds >= 10f) {
            int seconds = (int) Math.ceil(remainingSeconds);
            timeText = String.format("%ds", seconds);
        } else {
            timeText = String.format("%.1fs", remainingSeconds);
        }

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        var renderer = net.minecraft.client.Minecraft.getInstance().font;
        int slotCenterX = screenWidth / 2 - 90 + selectedSlot * 20 + 2 + 8;
        int textWidth = renderer.width(timeText);
        int textX = slotCenterX - textWidth / 2;

        int textY = screenHeight - 22 - 4 - 9;

        context.pose().pushPose();
        context.pose().translate(0, 0, 200);
        context.drawString(renderer, timeText, textX, textY, 0xFFFFFFFF, false);
        context.pose().popPose();
    }

    private static Object getField(Object instance, String fieldName) {
        try {
            java.lang.reflect.Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            return null;
        }
    }

    private static long getLongField(Object instance, String fieldName) {
        try {
            java.lang.reflect.Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getLong(instance);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getIntField(Object instance, String fieldName) {
        try {
            java.lang.reflect.Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(instance);
        } catch (Exception e) {
            return 0;
        }
    }
}