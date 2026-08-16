package xiao.hua.cca;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import xiao.hua.Huarolemods;
import xiao.hua.roles.VengeanceAgentComponent;

public class HuaRoleComponents implements EntityComponentInitializer {
    public static final ResourceLocation VENGEANCE_AGENT_ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "vengeance_agent");

    @Override
    public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
        Huarolemods.VENGEANCE_AGENT_COMPONENT = ComponentRegistry.getOrCreate(VENGEANCE_AGENT_ID, VengeanceAgentComponent.class);
        registry.beginRegistration(Player.class, Huarolemods.VENGEANCE_AGENT_COMPONENT)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(VengeanceAgentComponent::new);
    }
}