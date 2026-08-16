package xiao.hua;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

import xiao.hua.client.hud.VengeanceAgentHud;
import xiao.hua.client.screen.HuaScreenHandlers;
import xiao.hua.init.HuaRoles;
import xiao.hua.roles.VengeanceAgent;
import xiao.hua.roles.VengeanceAgentComponent;

public class HuarolemodsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Huarolemods.LOGGER.info("HuaRoleMods client initializing...");
        registerScreens();
        registerInstinctHighlight();
        registerHud();
        Huarolemods.LOGGER.info("HuaRoleMods client initialization complete!");
    }

    private void registerScreens() {
        MenuScreens.register(HuaScreenHandlers.LENS_VIEW_SCREEN_HANDLER, xiao.hua.client.screen.LensViewScreen::new);
    }

    private void registerInstinctHighlight() {
        // 冤仇代行：购买委托后，合约目标（活人+尸体）持续紫色高光，无视距离与本能开关。
        // 新基座 API：CommonInstinctEvents 分 BEFORE/MIDDLE/AFTER 三个阶段，
        // ALIVE_COMMON_BEFORE_EVENT 最先检查、首个非 pass 结果生效 →
        // 紫色注册在 BEFORE 阶段，永不被其他直觉颜色（乌鸦/鬼眼/杀手红等）覆盖。
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, hasInstinct) -> {
            if (!isVengeanceAgentContractTarget(target)) {
                return TrueFalseAndCustomResult.pass();
            }
            return TrueFalseAndCustomResult.custom(VengeanceAgent.CONTRACT_TARGET_PURPLE_COLOR);
        });
        CommonInstinctEvents.SPECTATOR_COMMON_EVENT.register((self, target, hasInstinct) -> {
            if (!isVengeanceAgentContractTarget(target)) {
                return TrueFalseAndCustomResult.pass();
            }
            return TrueFalseAndCustomResult.custom(VengeanceAgent.CONTRACT_TARGET_PURPLE_COLOR);
        });
    }

    private static boolean isVengeanceAgentContractTarget(Entity target) {
        if (!(target instanceof Player targetPlayer) && !(target instanceof PlayerBodyEntity body)) {
            return false;
        }
        if (SREClient.gameComponent == null) {
            return false;
        }
        Player self = Minecraft.getInstance().player;
        if (self == null) {
            return false;
        }
        SRERole selfRole = SREClient.gameComponent.getRole(self);
        if (selfRole == null || !selfRole.identifier().equals(HuaRoles.VENGEANCE_AGENT.identifier())) {
            return false;
        }

        VengeanceAgentComponent component = VengeanceAgentComponent.getKey().maybeGet(self).orElse(null);
        if (component == null) {
            return false;
        }

        UUID targetUuid = null;
        if (target instanceof Player player) {
            targetUuid = player.getUUID();
        } else if (target instanceof PlayerBodyEntity body) {
            targetUuid = body.getPlayerUuid();
        }
        return targetUuid != null && component.isContractTarget(targetUuid);
    }

    private void registerHud() {
        VengeanceAgentHud.register();
    }
}
