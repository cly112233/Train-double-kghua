package com.kghua.npcai.mail;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayPayloadHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record MailClaimAllC2SPayload() implements CustomPacketPayload {
   public static final Type<MailClaimAllC2SPayload> ID = new Type(ResourceLocation.fromNamespaceAndPath("western_cowboy", "mail_claim_all"));
   public static final StreamCodec<FriendlyByteBuf, MailClaimAllC2SPayload> CODEC = CustomPacketPayload.codec(
      MailClaimAllC2SPayload::encode, MailClaimAllC2SPayload::decode
   );
   public static final MailClaimAllC2SPayload INSTANCE = new MailClaimAllC2SPayload();

   public static void encode(MailClaimAllC2SPayload payload, FriendlyByteBuf buf) {
   }

   public static MailClaimAllC2SPayload decode(FriendlyByteBuf buf) {
      return INSTANCE;
   }

   public Type<MailClaimAllC2SPayload> type() {
      return ID;
   }

   public static class Receiver implements PlayPayloadHandler<MailClaimAllC2SPayload> {
      public void receive(@NotNull MailClaimAllC2SPayload payload, @NotNull Context context) {
         MailboxComponent mailbox = (MailboxComponent)MailboxComponent.KEY.get(context.player());
         mailbox.claimAll();
      }
   }
}
