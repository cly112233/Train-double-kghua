package xiao.hua.client.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import xiao.hua.Huarolemods;

import java.util.UUID;

public class HuaScreenHandlers {
    public static final ExtendedScreenHandlerType<LensViewScreenHandler, UUID> LENS_VIEW_SCREEN_HANDLER =
        Registry.register(BuiltInRegistries.MENU, Huarolemods.id("lens_view"),
            new ExtendedScreenHandlerType<>(LensViewScreenHandler::new, UUIDUtil.STREAM_CODEC));

    public static void init() {
        // registration happens in the static field above
    }
}