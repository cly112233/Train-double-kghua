package com.kghua.npcai.mail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

public class Mail {
   public final UUID id;
   public String sender;
   public String title;
   public String content;
   public List<ItemStack> attachments;
   public List<String> claimCommands;
   public boolean claimed;
   public boolean read;
   public long sentAt;
   public long expiresAt;
   private static final Gson GSON = new GsonBuilder().create();

   public Mail(UUID id, String sender, String title, String content, List<ItemStack> attachments, List<String> claimCommands, long sentAt, long expiresAt) {
      this.id = id;
      this.sender = sender;
      this.title = title;
      this.content = content;
      this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
      this.claimCommands = claimCommands != null ? new ArrayList<>(claimCommands) : new ArrayList<>();
      this.claimed = false;
      this.read = false;
      this.sentAt = sentAt;
      this.expiresAt = expiresAt;
   }

   public boolean hasRewards() {
      return !this.attachments.isEmpty() || !this.claimCommands.isEmpty();
   }

   public boolean canDelete() {
      return this.claimed || !this.hasRewards();
   }

   public boolean isExpired() {
      return this.expiresAt > 0L && System.currentTimeMillis() > this.expiresAt;
   }

   public CompoundTag toNbt(Provider provider) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("Id", this.id);
      tag.putString("Sender", this.sender);
      tag.putString("Title", this.title);
      tag.putString("Content", this.content);
      tag.putBoolean("Claimed", this.claimed);
      tag.putBoolean("Read", this.read);
      tag.putLong("SentAt", this.sentAt);
      tag.putLong("ExpiresAt", this.expiresAt);
      ListTag itemsTag = new ListTag();

      for (ItemStack stack : this.attachments) {
         itemsTag.add(stack.saveOptional(provider));
      }

      tag.put("Attachments", itemsTag);
      ListTag cmdsTag = new ListTag();

      for (String cmd : this.claimCommands) {
         CompoundTag cmdTag = new CompoundTag();
         cmdTag.putString("Cmd", cmd);
         cmdsTag.add(cmdTag);
      }

      tag.put("Commands", cmdsTag);
      return tag;
   }

   public static Mail fromNbt(CompoundTag tag, Provider provider) {
      UUID id = tag.getUUID("Id");
      String sender = tag.getString("Sender");
      String title = tag.getString("Title");
      String content = tag.getString("Content");
      long sentAt = tag.getLong("SentAt");
      long expiresAt = tag.getLong("ExpiresAt");
      List<ItemStack> attachments = new ArrayList<>();
      ListTag itemsTag = tag.getList("Attachments", 10);

      for (int i = 0; i < itemsTag.size(); i++) {
         ItemStack stack = ItemStack.parseOptional(provider, itemsTag.getCompound(i));
         if (!stack.isEmpty()) {
            attachments.add(stack);
         }
      }

      List<String> commands = new ArrayList<>();
      ListTag cmdsTag = tag.getList("Commands", 10);

      for (int ix = 0; ix < cmdsTag.size(); ix++) {
         commands.add(cmdsTag.getCompound(ix).getString("Cmd"));
      }

      Mail mail = new Mail(id, sender, title, content, attachments, commands, sentAt, expiresAt);
      mail.claimed = tag.getBoolean("Claimed");
      mail.read = tag.getBoolean("Read");
      return mail;
   }

   public String toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("id", this.id.toString());
      obj.addProperty("sender", this.sender);
      obj.addProperty("title", this.title);
      obj.addProperty("content", this.content);
      obj.addProperty("claimed", this.claimed);
      obj.addProperty("read", this.read);
      obj.addProperty("sentAt", this.sentAt);
      obj.addProperty("expiresAt", this.expiresAt);
      JsonArray cmds = new JsonArray();

      for (String cmd : this.claimCommands) {
         cmds.add(cmd);
      }

      obj.add("commands", cmds);
      return GSON.toJson(obj);
   }

   public static Mail fromJsonMeta(String json) {
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      UUID id = UUID.fromString(obj.get("id").getAsString());
      String sender = obj.has("sender") ? obj.get("sender").getAsString() : "System";
      String title = obj.has("title") ? obj.get("title").getAsString() : "";
      String content = obj.has("content") ? obj.get("content").getAsString() : "";
      long sentAt = obj.has("sentAt") ? obj.get("sentAt").getAsLong() : 0L;
      long expiresAt = obj.has("expiresAt") ? obj.get("expiresAt").getAsLong() : 0L;
      List<String> commands = new ArrayList<>();
      if (obj.has("commands")) {
         for (JsonElement e : obj.getAsJsonArray("commands")) {
            commands.add(e.getAsString());
         }
      }

      Mail mail = new Mail(id, sender, title, content, new ArrayList<>(), commands, sentAt, expiresAt);
      mail.claimed = obj.has("claimed") && obj.get("claimed").getAsBoolean();
      mail.read = obj.has("read") && obj.get("read").getAsBoolean();
      return mail;
   }
}
