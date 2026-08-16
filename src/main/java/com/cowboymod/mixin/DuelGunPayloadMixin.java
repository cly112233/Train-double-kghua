package com.cowboymod.mixin;

import com.cowboymod.WesternCowboyComponent;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunShootPayload.Receiver.class)
public class DuelGunPayloadMixin {

    @Inject(method = "receive(Lio/wifi/starrailexpress/network/original/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void onDuelGunHit(GunShootPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer shooter = context.player();
        if (shooter == null) return;
        Entity target = shooter.serverLevel().getEntity(payload.target());
        if (!(target instanceof ServerPlayer victim)) return;

        if (WesternCowboyComponent.onDuelHit(shooter, victim)) {
            ci.cancel();
        }
    }
}
