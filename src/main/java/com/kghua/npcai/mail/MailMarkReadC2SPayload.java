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

public record MailMarkReadC2SPayload(UUID mailId) implements CustomPacketPayload {
   public static final Type<MailMarkReadC2SPayload> ID = new Type(ResourceLocation.fromNamespaceAndPath("western_cowboy", "mail_mark_read"));
   public static final StreamCodec<FriendlyByteBuf, MailMarkReadC2SPayload> CODEC = StreamCodec.ofMember(
      MailMarkReadC2SPayload::write, MailMarkReadC2SPayload::read
   );

   public void write(FriendlyByteBuf buf) {
      buf.writeUUID(this.mailId);
   }

   public static MailMarkReadC2SPayload read(FriendlyByteBuf buf) {
      return new MailMarkReadC2SPayload(buf.readUUID());
   }

   public Type<MailMarkReadC2SPayload> type() {
      return ID;
   }

   public static class Receiver implements PlayPayloadHandler<MailMarkReadC2SPayload> {
      public void receive(@NotNull MailMarkReadC2SPayload payload, @NotNull Context context) {
         MailboxComponent mailbox = (MailboxComponent)MailboxComponent.KEY.get(context.player());
         mailbox.markRead(payload.mailId());
      }
   }
}
