package com.kghua.npcai.client;

import com.kghua.npcai.entity.CustomerServiceNpcEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public class CustomerServiceNpcRenderer extends LivingEntityRenderer<CustomerServiceNpcEntity, PlayerModel<CustomerServiceNpcEntity>> {
    private final PlayerModel<CustomerServiceNpcEntity> classicModel;
    private final PlayerModel<CustomerServiceNpcEntity> slimModel;

    public CustomerServiceNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.classicModel = this.model;
        this.slimModel = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(CustomerServiceNpcEntity entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = isSlimModel(entity) ? slimModel : classicModel;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private boolean isSlimModel(CustomerServiceNpcEntity entity) {
        GameProfile profile = entity.getSkinProfile();
        if (profile == null) return false;
        try {
            PlayerSkin playerSkin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
            return playerSkin != null && playerSkin.model() == PlayerSkin.Model.SLIM;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CustomerServiceNpcEntity entity) {
        GameProfile profile = entity.getSkinProfile();
        if (profile != null) {
            var playerSkin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
            if (playerSkin != null) {
                return playerSkin.texture();
            }
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }
}
