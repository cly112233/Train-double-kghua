package com.cowboymod.mixin;

import com.cowboymod.WesternCowboyComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

@Mixin(RefugeeComponent.class)
public class RefugeeSaveMixin {

    @Inject(method = "SavePlayersStats", at = @At("HEAD"))
    private void cancelDuelsBeforeRefugeeSave(CallbackInfo ci) {
        for (var comp : WesternCowboyComponent.getAllActive()) {
            if (comp.isInArena()) comp.cancelDuelForRefugee();
        }
    }
}
