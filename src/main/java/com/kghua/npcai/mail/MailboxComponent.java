package com.kghua.npcai.mail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailboxComponent implements AutoSyncedComponent, ServerTickingComponent {
   private static final Logger LOGGER = LoggerFactory.getLogger(MailboxComponent.class);
   private static final Gson GSON = new GsonBuilder().create();
   public static final ComponentKey<MailboxComponent> KEY = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath("western_cowboy", "mailbox"), MailboxComponent.class);
   private static final String DB_KEY = "mailbox";
   private static final int MAX_MAILS = 100;
   private static final int SYNC_INTERVAL = 20;
   private final Player player;
   private final List<Mail> mails = new ArrayList<>();
   private boolean dirty = false;
   private int tickCounter = 0;
   private boolean dbLoaded = false;
   private boolean databaseLoadPending = false;

   public MailboxComponent(Player player) {
      this.player = player;
   }

   public Player getPlayer() {
      return this.player;
   }

   public List<Mail> getMails() {
      return Collections.unmodifiableList(this.mails);
   }

   public int getUnreadCount() {
      int count = 0;

      for (Mail m : this.mails) {
         if (!m.read && !m.isExpired()) {
            count++;
         }
      }

      return count;
   }

   public int getClaimableCount() {
      int count = 0;

      for (Mail m : this.mails) {
         if (m.hasRewards() && !m.claimed && !m.isExpired()) {
            count++;
         }
      }

      return count;
   }

   public void sendMail(Mail mail) {
      if (this.mails.size() >= 100) {
         this.pruneOldest();
      }

      if (this.mails.size() >= 100) {
         LOGGER.warn("Player {} mailbox full, dropping mail {}", this.player.getName().getString(), mail.id);
      } else {
         this.mails.add(mail);
         this.markDirty();
      }
   }

   public void markRead(UUID mailId) {
      for (Mail m : this.mails) {
         if (m.id.equals(mailId) && !m.read) {
            m.read = true;
            this.markDirty();
            return;
         }
      }
   }

   public boolean claimMail(UUID mailId) {
      if (this.player instanceof ServerPlayer serverPlayer) {
         for (Mail m : this.mails) {
            if (m.id.equals(mailId) && !m.claimed && m.hasRewards() && !m.isExpired()) {
               for (ItemStack stack : m.attachments) {
                  ItemStack copy = stack.copy();
                  if (!serverPlayer.getInventory().add(copy)) {
                     serverPlayer.drop(copy, false);
                  }
               }

               this.executeClaimCommands(serverPlayer, m.claimCommands);
               m.claimed = true;
               m.read = true;
               this.markDirty();
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public int claimAll() {
      if (!(this.player instanceof ServerPlayer)) {
         return 0;
      } else {
         int count = 0;

         for (Mail m : this.mails) {
            if (!m.claimed && m.hasRewards() && !m.isExpired() && this.claimMail(m.id)) {
               count++;
            }
         }

         return count;
      }
   }

   public boolean deleteMail(UUID mailId) {
      Iterator<Mail> it = this.mails.iterator();

      while (it.hasNext()) {
         Mail m = it.next();
         if (m.id.equals(mailId) && m.canDelete()) {
            it.remove();
            this.markDirty();
            return true;
         }
      }

      return false;
   }

   public int deleteAllClaimed() {
      int count = 0;
      Iterator<Mail> it = this.mails.iterator();

      while (it.hasNext()) {
         Mail m = it.next();
         if (m.canDelete() && m.read) {
            it.remove();
            count++;
         }
      }

      if (count > 0) {
         this.markDirty();
      }

      return count;
   }

   public void clearAllMails() {
      if (!this.mails.isEmpty()) {
         this.mails.clear();
         this.markDirty();
      }
   }

   private void markDirty() {
      this.dirty = true;
   }

   private void pruneOldest() {
      this.mails.removeIf(Mail::isExpired);
      if (this.mails.size() >= 100) {
         this.mails.removeIf(m -> m.claimed);
      }
   }

   private void executeClaimCommands(ServerPlayer serverPlayer, List<String> commands) {
      MinecraftServer server = serverPlayer.getServer();
      if (server != null) {
         String playerName = serverPlayer.getName().getString();
         CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);

         for (String cmd : commands) {
            String resolved = cmd.replace("{player}", playerName);

            try {
               server.getCommands().performPrefixedCommand(source, resolved);
            } catch (Exception var10) {
               LOGGER.warn("Failed to execute mail claim command '{}' for player {}", new Object[]{resolved, playerName, var10});
            }
         }
      }
   }

   public void sync() {
      KEY.sync(this.player);
   }

   public boolean shouldSyncWith(ServerPlayer player) {
      return this.player == player;
   }

   public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
      CompoundTag tag = new CompoundTag();
      this.writeToSyncNbt(tag, buf.registryAccess());
      buf.writeNbt(tag);
   }

   public void applySyncPacket(RegistryFriendlyByteBuf buf) {
      CompoundTag tag = buf.readNbt();
      if (tag != null) {
         this.readFromSyncNbt(tag, buf.registryAccess());
      }
   }

   public void writeToSyncNbt(CompoundTag tag, Provider provider) {
      ListTag list = new ListTag();

      for (Mail m : this.mails) {
         if (!m.isExpired()) {
            list.add(m.toNbt(provider));
         }
      }

      tag.put("Mails", list);
   }

   public void readFromSyncNbt(CompoundTag tag, Provider provider) {
      this.mails.clear();
      ListTag list = tag.getList("Mails", 10);

      for (int i = 0; i < list.size(); i++) {
         this.mails.add(Mail.fromNbt(list.getCompound(i), provider));
      }
   }

   public void readFromNbt(CompoundTag tag, Provider provider) {
      this.mails.clear();
      if (tag.contains("Mails", 9)) {
         ListTag list = tag.getList("Mails", 10);

         for (int i = 0; i < list.size(); i++) {
            Mail m = Mail.fromNbt(list.getCompound(i), provider);
            if (!m.isExpired()) {
               this.mails.add(m);
            }
         }
      }
   }

   public void writeToNbt(CompoundTag tag, Provider provider) {
      ListTag list = new ListTag();

      for (Mail m : this.mails) {
         if (!m.isExpired()) {
            list.add(m.toNbt(provider));
         }
      }

      tag.put("Mails", list);
   }

   public void serverTick() {
      if (this.player instanceof ServerPlayer) {
         if (!this.dbLoaded) {
            this.dbLoaded = true;
            this.loadFromDatabase();
         }

         this.tickCounter++;
         if (this.tickCounter >= 20 && this.dirty) {
            this.tickCounter = 0;
            this.sync();
            this.saveToDatabase();
         }
      }
   }

   private void loadFromDatabase() {
      if (MysqlPlayerDataStore.isAvailable()) {
         this.databaseLoadPending = true;
         MysqlPlayerDataStore.loadBatchAsync(this.player.getUUID(), List.of("mailbox")).thenAccept(records -> {
            MysqlPlayerDataStore.SyncRecord record = records.get("mailbox");
            if (this.player instanceof ServerPlayer sp && sp.getServer() != null) {
               sp.getServer().execute(() -> {
                  this.databaseLoadPending = false;
                  if (record != null && record.payload() != null && !record.payload().isEmpty()) {
                     try {
                        List<Mail> loaded = this.deserializeFromJson(record.payload());
                        this.mergeFromDatabase(loaded);
                        this.sync();
                     } catch (Exception var3x) {
                        LOGGER.warn("Failed to load mailbox from DB for player {}", this.player.getName().getString(), var3x);
                     }
                  }
               });
            }
         }).exceptionally(throwable -> {
            this.databaseLoadPending = false;
            LOGGER.warn("Failed to load mailbox from DB for player {}", this.player.getName().getString(), throwable);
            return null;
         });
      }
   }

   private void mergeFromDatabase(List<Mail> loaded) {
      boolean changed = false;

      for (Mail dbMail : loaded) {
         if (!dbMail.isExpired()) {
            boolean exists = false;

            for (Mail local : this.mails) {
               if (local.id.equals(dbMail.id)) {
                  exists = true;
                  if (dbMail.claimed && !local.claimed) {
                     local.claimed = true;
                     changed = true;
                  }

                  if (dbMail.read && !local.read) {
                     local.read = true;
                     changed = true;
                  }
                  break;
               }
            }

            if (!exists && this.mails.size() < 100) {
               this.mails.add(dbMail);
               changed = true;
            }
         }
      }

      if (changed) {
         this.markDirty();
      }
   }

   private boolean saveToDatabase() {
      if (MysqlPlayerDataStore.isAvailable() && !this.databaseLoadPending) {
         String json = this.serializeToJson();
         long now = System.currentTimeMillis();
         this.dirty = false;
         MysqlPlayerDataStore.saveBatchAsync(this.player.getUUID(), Map.of("mailbox", json), now).whenComplete((success, throwable) -> {
            if (throwable != null) {
               this.dirty = true;
               LOGGER.warn("Failed to save mailbox to DB for player {}", this.player.getName().getString(), throwable);
            } else {
               if (!Boolean.TRUE.equals(success)) {
                  this.dirty = true;
                  LOGGER.warn("Mailbox DB save for player {} was rejected by remote revision; reloading first.", this.player.getName().getString());
                  this.loadFromDatabase();
               }
            }
         });
         return true;
      } else {
         return false;
      }
   }

   public boolean flushDatabaseSyncBlocking() {
      if (MysqlPlayerDataStore.isAvailable() && !this.databaseLoadPending) {
         boolean success = MysqlPlayerDataStore.saveBatchBlocking(
            this.player.getUUID(), Map.of("mailbox", this.serializeToJson()), System.currentTimeMillis(), 4000L
         );
         if (success) {
            this.dirty = false;
            this.tickCounter = 0;
         } else {
            this.dirty = true;
            this.loadFromDatabase();
         }

         return success;
      } else {
         return false;
      }
   }

   private String serializeToJson() {
      JsonArray arr = new JsonArray();

      for (Mail m : this.mails) {
         if (!m.isExpired()) {
            arr.add(JsonParser.parseString(m.toJson()));
         }
      }

      return GSON.toJson(arr);
   }

   private List<Mail> deserializeFromJson(String json) {
      List<Mail> result = new ArrayList<>();

      for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
         try {
            result.add(Mail.fromJsonMeta(e.toString()));
         } catch (Exception var7) {
            LOGGER.warn("Failed to parse mail from JSON", var7);
         }
      }

      return result;
   }
}
