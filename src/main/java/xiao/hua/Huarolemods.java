package xiao.hua;

import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xiao.hua.client.screen.HuaScreenHandlers;
import xiao.hua.framework.EventRegistry;
import xiao.hua.framework.FrameworkBootstrap;
import xiao.hua.init.HuaItems;
import xiao.hua.init.HuaRoles;
import xiao.hua.init.HuaShops;
import xiao.hua.roles.VengeanceAgentComponent;

public class Huarolemods implements ModInitializer {
    public static final String MOD_ID = "huarolemods";
    public static final Logger LOGGER = LoggerFactory.getLogger("huarolemods");
    public static ComponentKey<VengeanceAgentComponent> VENGEANCE_AGENT_COMPONENT;

    @Override
    public void onInitialize() {
        LOGGER.info("HuaRoleMods initializing...");
        FrameworkBootstrap.initialize();
        HuaItems.register();
        HuaRoles.register();
        TMMRoles.addRoleComponents(VENGEANCE_AGENT_COMPONENT);
        HuaShops.register();
        HuaScreenHandlers.init();
        EventRegistry.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> LOGGER.info("HuaRoleMods framework fully loaded!"));
        LOGGER.info("HuaRoleMods initialization complete!");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("huarolemods", path);
    }
}