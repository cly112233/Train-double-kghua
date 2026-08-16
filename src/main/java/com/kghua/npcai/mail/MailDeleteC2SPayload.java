package com.kghua.npcai.mail;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayPayloadHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record MailDeleteC2SPayload(UUID mailId) implements CustomPacketPayload {
   public static final Type<MailDeleteC2SPayload> ID = new Type(ResourceLocation.fromNamespaceAndPath("western_cowboy", "mail_delete"));
   public static final StreamCodec<FriendlyByteBuf, MailDeleteC2SPayload> CODEC = StreamCodec.ofMember(MailDeleteC2SPayload::write, MailDeleteC2SPayload::read);

   public void write(FriendlyByteBuf buf) {
      buf.writeUUID(this.mailId);
   }

   public static MailDeleteC2SPayload read(FriendlyByteBuf buf) {
      return new MailDeleteC2SPayload(buf.readUUID());
   }

   public Type<MailDeleteC2SPayload> type() {
      return ID;
   }

   public static class Receiver implements PlayPayloadHandler<MailDeleteC2SPayload> {
      public void receive(@NotNull MailDeleteC2SPayload payload, @NotNull Context context) {
         MailboxComponent mailbox = (MailboxComponent)MailboxComponent.KEY.get(context.player());
         mailbox.deleteMail(payload.mailId());
      }
   }
}
