package com.kghua.npcai.mail;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayPayloadHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record MailDeleteAllReadC2SPayload() implements CustomPacketPayload {
   public static final Type<MailDeleteAllReadC2SPayload> ID = new Type(ResourceLocation.fromNamespaceAndPath("western_cowboy", "mail_delete_all_read"));
   public static final StreamCodec<FriendlyByteBuf, MailDeleteAllReadC2SPayload> CODEC = CustomPacketPayload.codec(
      MailDeleteAllReadC2SPayload::encode, MailDeleteAllReadC2SPayload::decode
   );
   public static final MailDeleteAllReadC2SPayload INSTANCE = new MailDeleteAllReadC2SPayload();

   public static void encode(MailDeleteAllReadC2SPayload payload, FriendlyByteBuf buf) {
   }

   public static MailDeleteAllReadC2SPayload decode(FriendlyByteBuf buf) {
      return INSTANCE;
   }

   public Type<MailDeleteAllReadC2SPayload> type() {
      return ID;
   }

   public static class Receiver implements PlayPayloadHandler<MailDeleteAllReadC2SPayload> {
      public void receive(@NotNull MailDeleteAllReadC2SPayload payload, @NotNull Context context) {
         MailboxComponent mailbox = (MailboxComponent)MailboxComponent.KEY.get(context.player());
         mailbox.deleteAllClaimed();
      }
   }
}
