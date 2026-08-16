package com.cowboymod.network;

import com.cowboymod.CowboyMod;
import com.cowboymod.WesternCowboyComponent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * C2S packet sent when the cowboy selects a duel target from the inventory GUI.
 * Reference: PartyKillerC2SPacket, SwapperC2SPacket
 */
public record CowboyDuelPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CowboyDuelPacket> ID =
        new CustomPacketPayload.Type<>(CowboyMod.COWBOY_ROLE_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CowboyDuelPacket> CODEC =
        StreamCodec.of(
            (buf, value) -> buf.writeUUID(value.targetUuid),
            buf -> new CowboyDuelPacket(buf.readUUID())
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    // ===== Registration & InteractionHandler =====

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> handle(player, payload.targetUuid));
        });
    }

    private static void handle(ServerPlayer player, UUID targetUuid) {
        WesternCowboyComponent comp = WesternCowboyComponent.get(player);
        if (comp == null) return;

        String errorKey = comp.tryStartDuel(player, targetUuid);
        if (errorKey != null) {
            String msg = switch (errorKey) {
                case "error.cooldown" -> "§c决斗冷却中";
                case "error.not_enough_gold" -> "§c金币不足（需要200）";
                case "error.too_far" -> "§c目标太远（18格内）";
                case "error.not_on_ground" -> "§c目标不在地面上";
                case "error.madness" -> "§c对方已经疯魔了，我打不过他。";
                case "error.otherworld" -> "§c对方正身处里世界，无法拉入决斗。";
                case "error.target" -> "§c目标无效或已死亡";
                default -> "§c无法发起决斗";
            };
            player.sendSystemMessage(Component.literal(msg), true);
        }
    }
}
