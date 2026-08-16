package com.kghua.npcai.mail;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * 邮箱系统网络注册（服务端 + 客户端）。
 * 从旧版基座 io.wifi.starrailexpress.register.SREPayloadRegister / SREReceiverRegister 移植。
 * 服务端：NpcAiMod.onInitialize 调用 registerServer()；
 * 客户端：NpcAiClient.onInitializeClient 调用 registerClient()。
 */
public final class MailNetwork {

    private MailNetwork() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(OpenMailboxScreenPayload.ID, OpenMailboxScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MailClaimC2SPayload.ID, MailClaimC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MailClaimAllC2SPayload.ID, MailClaimAllC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MailDeleteC2SPayload.ID, MailDeleteC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MailDeleteAllReadC2SPayload.ID, MailDeleteAllReadC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MailMarkReadC2SPayload.ID, MailMarkReadC2SPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MailClaimC2SPayload.ID, new MailClaimC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(MailClaimAllC2SPayload.ID, new MailClaimAllC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(MailDeleteC2SPayload.ID, new MailDeleteC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(MailDeleteAllReadC2SPayload.ID, new MailDeleteAllReadC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(MailMarkReadC2SPayload.ID, new MailMarkReadC2SPayload.Receiver());
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenMailboxScreenPayload.ID, (payload, context) -> {
            Minecraft.getInstance().setScreen(new MailboxScreen());
        });
    }
}
