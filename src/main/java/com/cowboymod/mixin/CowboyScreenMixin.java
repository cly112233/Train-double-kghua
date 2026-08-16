package com.cowboymod.mixin;

import com.cowboymod.CowboyMod;
import com.cowboymod.client.CowboyPlayerWidget;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.PlayerPaginationHelper;
import org.agmas.noellesroles.client.RoleScreenHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(LimitedInventoryScreen.class)
public abstract class CowboyScreenMixin
        extends LimitedHandledScreen<InventoryMenu>
        implements PlayerPaginationHelper.ScreenWithChildren {

    @Shadow @Final public LocalPlayer player;

    @Unique private RoleScreenHelper<PlayerInfo> roleScreenHelper;

    @Unique private static final PlayerPaginationHelper.PaginationTextProvider TEXT_PROVIDER =
            new PlayerPaginationHelper.PaginationTextProvider() {
                public String getPageTranslationKey() { return "cowboy.screen.page"; }
                public String getPrevTranslationKey() { return "cowboy.screen.prev"; }
                public String getNextTranslationKey() { return "cowboy.screen.next"; }
            };

    public CowboyScreenMixin(InventoryMenu handler, Inventory inv, Component title) {
        super(handler, inv, title);
    }

    // ===== ScreenWithChildren (compiled against real interface — 100% match) =====
    @Override
    public void addDrawableChild(Button w) { super.addRenderableWidget(w); }
    @Override
    public void removeDrawableChild(Button w) { super.removeWidget(w); }
    @Override
    public void clearChildren() { super.clearWidgets(); }

    // ===== Mixin injections =====
    @Inject(method = "init", at = @At("RETURN"))
    private void cowboy$onInit(CallbackInfo ci) {
        getRoleScreenHelper().onInit(this);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void cowboy$onRender(GuiGraphics ctx, int mx, int my, float d, CallbackInfo ci) {
        getRoleScreenHelper().onRender(ctx, this);
    }

    // ===== Helpers =====
    @Unique
    private RoleScreenHelper<PlayerInfo> getRoleScreenHelper() {
        if (roleScreenHelper == null) {
            roleScreenHelper = new RoleScreenHelper<>(
                    player, CowboyMod.WESTERN_COWBOY,
                    this::createWidget, TEXT_PROVIDER, null, this::getEligible);
        }
        return roleScreenHelper;
    }

    @Unique
    private CowboyPlayerWidget createWidget(int x, int y, PlayerInfo e, int idx) {
        var c = Minecraft.getInstance();
        if (c.player == null || c.level == null) return null;
        CowboyPlayerWidget w = new CowboyPlayerWidget(
                (LimitedInventoryScreen) (Object) this, x, y,
                e.getProfile().getId(), e, idx);
        addDrawableChild(w);
        return w;
    }

    @Unique
    private List<PlayerInfo> getEligible() {
        var c = Minecraft.getInstance();
        if (c.player == null || c.getConnection() == null || c.level == null) return List.of();
        UUID me = c.player.getUUID();
        double max = 18.0 * 18.0;
        List<PlayerInfo> out = new ArrayList<>();
        for (var e : c.getConnection().getListedOnlinePlayers()) {
            if (e == null) continue;
            UUID id = e.getProfile().getId();
            if (id.equals(me)) continue;
            var wp = c.level.players().stream()
                    .filter(p -> p.getUUID().equals(id)).findFirst().orElse(null);
            if (wp == null || !wp.isAlive() || wp.isSpectator()) continue;
            if (c.player.distanceToSqr(wp) > max) continue;
            out.add(e);
        }
        return out;
    }
}
