package com.cowboymod.client;

import com.cowboymod.CowboyMod;
import com.cowboymod.entity.CowboyPuppetEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;

public class CowboyModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CowboyMod.LOGGER.info("Western Cowboy client initializing...");
        CowboyDuelHud.register();
        // 皮肤图标导出已停用（2026-08-16）：官方皮肤/抽奖系统断开，贴图来源（habitrain jar）不再使用。
        // 自建皮肤系统时重新启用 SkinIconExporter.init()（服务端 SkinIconUploadPacket 与
        // 网站 wsGateway skin.icon 管道均保留，可直接复用）。
        // SkinIconExporter.init();

        EntityRendererRegistry.register(CowboyMod.COWBOY_PUPPET, ctx ->
                new EntityRenderer<CowboyPuppetEntity>(ctx) {
                    @Override public ResourceLocation getTextureLocation(CowboyPuppetEntity e) { return null; }
                    @Override public void render(CowboyPuppetEntity e, float yaw, float tickDelta,
                                                   PoseStack ms, MultiBufferSource vcp, int light) {}
                });

        CowboyMod.LOGGER.info("Western Cowboy client ready!");

        // Initialize Vengeance Agent client (HuaRoleMods addon)
        new xiao.hua.HuarolemodsClient().onInitializeClient();
    }
}
