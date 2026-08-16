package com.kghua.npcai.mail;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record OpenMailboxScreenPayload() implements CustomPacketPayload {
   public static final Type<OpenMailboxScreenPayload> ID = new Type(ResourceLocation.fromNamespaceAndPath("western_cowboy", "open_mailbox_screen"));
   public static final StreamCodec<FriendlyByteBuf, OpenMailboxScreenPayload> CODEC = CustomPacketPayload.codec(
      OpenMailboxScreenPayload::encode, OpenMailboxScreenPayload::decode
   );
   public static final OpenMailboxScreenPayload INSTANCE = new OpenMailboxScreenPayload();

   public static void encode(OpenMailboxScreenPayload payload, FriendlyByteBuf buf) {
   }

   public static OpenMailboxScreenPayload decode(FriendlyByteBuf buf) {
      return INSTANCE;
   }

   public Type<OpenMailboxScreenPayload> type() {
      return ID;
   }
}
