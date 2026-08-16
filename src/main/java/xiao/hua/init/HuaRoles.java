package xiao.hua.init;

import io.wifi.starrailexpress.api.TMMRoles;
import xiao.hua.Huarolemods;
import xiao.hua.roles.VengeanceAgent;

public class HuaRoles {
    public static VengeanceAgent VENGEANCE_AGENT;

    public static void register() {
        Huarolemods.LOGGER.info("Registering HuaRoleMods roles...");
        VENGEANCE_AGENT = (VengeanceAgent)TMMRoles.registerRole(new VengeanceAgent());
        VENGEANCE_AGENT.setComponentKey(Huarolemods.VENGEANCE_AGENT_COMPONENT);
        Huarolemods.LOGGER.info("HuaRoleMods roles registered successfully!");
    }
}