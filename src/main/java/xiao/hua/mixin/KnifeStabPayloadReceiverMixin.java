package xiao.hua.mixin;

import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xiao.hua.init.HuaItems;

@Mixin(KnifeStabPayload.Receiver.class)
public class KnifeStabPayloadReceiverMixin {

    @Redirect(method = "receive",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V",
                       ordinal = 0))
    private void redirectAddCooldown(net.minecraft.world.item.ItemCooldowns instance,
                                     net.minecraft.world.item.Item item,
                                     int cooldown,
                                     KnifeStabPayload payload,
                                     net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (player.getMainHandItem().is(HuaItems.VENGEANCE_KNIFE)) {
            int totalPlayers = player.serverLevel().players().size();
            int seconds = Math.max(10, Math.round((float) (35 - 5.0 * totalPlayers / 6)));
            instance.addCooldown(HuaItems.VENGEANCE_KNIFE, seconds * 20);
        } else {
            instance.addCooldown(item, cooldown);
        }
    }
}