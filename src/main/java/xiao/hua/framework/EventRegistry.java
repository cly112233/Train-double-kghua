package xiao.hua.framework;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import xiao.hua.Huarolemods;
import xiao.hua.roles.VengeanceAgent;

public class EventRegistry {
    public static void register() {
        Huarolemods.LOGGER.info("Registering event listeners...");
        ServerPlayerEvents.AFTER_RESPAWN.register(EventRegistry::onPlayerRespawn);
        ServerPlayerEvents.COPY_FROM.register(EventRegistry::onPlayerCopyFrom);
        ServerTickEvents.END_SERVER_TICK.register(EventRegistry::onServerTick);
        registerRoleEvents();
        Huarolemods.LOGGER.info("Event listeners registered successfully");
    }

    private static void registerRoleEvents() {
        io.wifi.starrailexpress.event.AllowShootRevolverDrop.EVENT.register((shooter, target) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(shooter.level());
            SRERole role = gameWorld.getRole(shooter);
            if (role instanceof VengeanceAgent) {
                return shooter.getRandom().nextFloat() < 0.5f ? TrueFalseResult.TRUE : TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
    }

    private static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        SkillManager.resetCooldowns(newPlayer);
    }

    private static void onPlayerCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        SkillManager.copyCooldowns(oldPlayer, newPlayer);
    }

    private static void onServerTick(MinecraftServer server) {}
}
