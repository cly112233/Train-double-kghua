package com.cowboymod.mixin;

import com.cowboymod.WesternCowboyComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Before Fool meeting starts, cancel any ongoing duels for participants.
 * This ensures duel cleanup happens BEFORE the meeting teleport,
 * avoiding puppets left in the arena and other conflicts.
 */
@Mixin(TarotAssemblyManager.class)
public class FoolMeetingBlocker {

    @Inject(method = "startAssembly", at = @At("HEAD"))
    private static void cancelDuelsBeforeMeeting(ServerPlayer player, CallbackInfo ci) {
        if (player == null) return;
        // Cancel ALL active duels before meeting starts (any participant)
        for (var comp : WesternCowboyComponent.getAllActive()) {
            if (comp.isInArena()) comp.cancelDuelForRefugee();
        }
    }
}
