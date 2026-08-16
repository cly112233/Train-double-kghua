package com.cowboymod.network;

import com.cowboymod.CowboyMod;
import com.google.gson.JsonObject;
import com.kghua.npcai.webbridge.WebBridge;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * C2S：客户端离屏渲染好的皮肤图标 PNG（每张一条）。
 * 服务端落盘 lottery_skin_data/skins/ 并经 webbridge 上传网站展示。
 */
public record SkinIconUploadPacket(String file, byte[] png) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkinIconUploadPacket> ID = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(CowboyMod.MOD_ID, "skin_icon_upload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkinIconUploadPacket> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.file);
            buf.writeByteArray(p.png);
        },
        buf -> new SkinIconUploadPacket(buf.readUtf(), buf.readByteArray())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            MinecraftServer server = context.player().server;
            server.execute(() -> handle(server, payload.file, payload.png));
        });
    }

    private static void handle(MinecraftServer server, String file, byte[] png) {
        // 文件名白名单：类型_皮肤.png（均为小写字母数字下划线）
        if (file == null || !file.matches("[a-z0-9_]+_[a-z0-9_]+[.]png")) return;

        try {
            Path dir = server.getServerDirectory().resolve("lottery_skin_data").resolve("skins");
            Files.createDirectories(dir);
            Path target = dir.resolve(file).normalize();
            if (!target.startsWith(dir)) return;
            Files.write(target, png);

            // 上传网站（base64，事件通道与 chat.game 同构）
            JsonObject ev = new JsonObject();
            ev.addProperty("type", "event");
            JsonObject body = new JsonObject();
            body.addProperty("type", "skin.icon");
            JsonObject data = new JsonObject();
            data.addProperty("file", file);
            data.addProperty("png", Base64.getEncoder().encodeToString(png));
            body.add("data", data);
            ev.add("event", body);
            WebBridge.send(ev);
        } catch (Exception e) {
            CowboyMod.LOGGER.warn("[skin-icon] save/upload failed for {}: {}", file, e.toString());
        }
    }
}
