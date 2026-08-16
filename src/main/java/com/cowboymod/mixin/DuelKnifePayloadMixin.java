package com.cowboymod.mixin;

import com.cowboymod.WesternCowboyComponent;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KnifeStabPayload.Receiver.class)
public class DuelKnifePayloadMixin {

    @Inject(method = "receive(Lio/wifi/starrailexpress/network/original/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void onDuelKnifeHit(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer stabber = context.player();
        if (stabber == null) return;
        Entity target = stabber.serverLevel().getEntity(payload.target());
        if (!(target instanceof ServerPlayer victim)) return;

        if (WesternCowboyComponent.onDuelHit(stabber, victim)) {
            ci.cancel();
        }
    }
}
