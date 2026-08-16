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

public record MailClaimC2SPayload(UUID mailId) implements CustomPacketPayload {
   public static final Type<MailClaimC2SPayload> ID = new Type(ResourceLocation.fromNamespaceAndPath("western_cowboy", "mail_claim"));
   public static final StreamCodec<FriendlyByteBuf, MailClaimC2SPayload> CODEC = StreamCodec.ofMember(MailClaimC2SPayload::write, MailClaimC2SPayload::read);

   public void write(FriendlyByteBuf buf) {
      buf.writeUUID(this.mailId);
   }

   public static MailClaimC2SPayload read(FriendlyByteBuf buf) {
      return new MailClaimC2SPayload(buf.readUUID());
   }

   public Type<MailClaimC2SPayload> type() {
      return ID;
   }

   public static class Receiver implements PlayPayloadHandler<MailClaimC2SPayload> {
      public void receive(@NotNull MailClaimC2SPayload payload, @NotNull Context context) {
         MailboxComponent mailbox = (MailboxComponent)MailboxComponent.KEY.get(context.player());
         mailbox.claimMail(payload.mailId());
      }
   }
}
