package com.kghua.npcai;

import com.kghua.npcai.command.PlayNpcCommand;
import com.kghua.npcai.command.TallaiCommand;
import com.kghua.npcai.data.*;
import com.kghua.npcai.server.DeathEventHandler;
import com.kghua.npcai.server.TitleManager;
import com.kghua.npcai.mailbridge.MailBridge;
import com.kghua.npcai.server.config.NpcAiServerConfig;
import com.kghua.npcai.webbridge.WebBridge;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
// import io.wifi.starrailexpress.util.ItemSkinManager; // 已断开（2026-08-16）：抽奖系统停用后无引用
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import net.minecraft.commands.CommandSourceStack;
import com.kghua.npcai.entity.CustomerServiceNpcEntity;
import com.kghua.npcai.item.NpcAdminToolItem;
import com.kghua.npcai.network.*;
import com.kghua.npcai.player.PlayerPendingTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NpcAiMod implements ModInitializer {
    public static final String MOD_ID = "western_cowboy";
    public static final Logger LOGGER = LoggerFactory.getLogger("western_cowboy.npcai");

    @SuppressWarnings("unchecked")
    public static final EntityType<CustomerServiceNpcEntity> CUSTOMER_SERVICE_NPC =
        EntityType.Builder.<CustomerServiceNpcEntity>of(CustomerServiceNpcEntity::new, MobCategory.MISC)
            .sized(0.6f, 1.8f)
            .clientTrackingRange(64)
            .updateInterval(3)
            .build("customer_service_npc");

    public static final Item NPC_ADMIN_TOOL = new NpcAdminToolItem();

    // 小脑榜方块：3×4 / 5×7 / 7×9 / 9×9，显示行数由方块高度决定
    // 屏障方块样式：地图上不染色、方块本体不可见（RenderShape.INVISIBLE），
    // noOcclusion 与屏障方块一致——贴邻的其他方块不会被透视/挖空面，保留可挖掘拆除
    private static final net.minecraft.world.level.block.state.BlockBehaviour.Properties BOARD_PROPS =
        net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
            .mapColor(net.minecraft.world.level.material.MapColor.NONE)
            .noOcclusion()
            .strength(1.8f)
            .requiresCorrectToolForDrops()
            .sound(net.minecraft.world.level.block.SoundType.STONE);
    public static final Block CEREBELLUM_BOARD_BLOCK = new com.kghua.npcai.block.CerebellumBoardBlock(BOARD_PROPS, 3, 4);
    public static final Block CEREBELLUM_BOARD_MEDIUM_BLOCK = new com.kghua.npcai.block.CerebellumBoardBlock(BOARD_PROPS, 5, 7);
    public static final Block CEREBELLUM_BOARD_LARGE_BLOCK = new com.kghua.npcai.block.CerebellumBoardBlock(BOARD_PROPS, 7, 9);
    public static final Block CEREBELLUM_BOARD_XL_BLOCK = new com.kghua.npcai.block.CerebellumBoardBlock(BOARD_PROPS, 9, 9);
    public static final net.minecraft.world.level.block.entity.BlockEntityType<com.kghua.npcai.block.CerebellumBoardBlockEntity> CEREBELLUM_BOARD_BE =
        net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
            com.kghua.npcai.block.CerebellumBoardBlockEntity::new,
            CEREBELLUM_BOARD_BLOCK, CEREBELLUM_BOARD_MEDIUM_BLOCK, CEREBELLUM_BOARD_LARGE_BLOCK,
            CEREBELLUM_BOARD_XL_BLOCK).build(null);

    @Override
    public void onInitialize() {
        LOGGER.info("NPC AI service initializing...");

        registerPayloadTypes();

        Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "customer_service_npc"),
            CUSTOMER_SERVICE_NPC
        );
        FabricDefaultAttributeRegistry.register(CUSTOMER_SERVICE_NPC, CustomerServiceNpcEntity.createAttributes());

        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "npc_admin_tool"),
            NPC_ADMIN_TOOL
        );

        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board"), CEREBELLUM_BOARD_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board"),
            new net.minecraft.world.item.BlockItem(CEREBELLUM_BOARD_BLOCK, new net.minecraft.world.item.Item.Properties()));
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board_medium"), CEREBELLUM_BOARD_MEDIUM_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board_medium"),
            new net.minecraft.world.item.BlockItem(CEREBELLUM_BOARD_MEDIUM_BLOCK, new net.minecraft.world.item.Item.Properties()));
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board_large"), CEREBELLUM_BOARD_LARGE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board_large"),
            new net.minecraft.world.item.BlockItem(CEREBELLUM_BOARD_LARGE_BLOCK, new net.minecraft.world.item.Item.Properties()));
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board_xl"), CEREBELLUM_BOARD_XL_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board_xl"),
            new net.minecraft.world.item.BlockItem(CEREBELLUM_BOARD_XL_BLOCK, new net.minecraft.world.item.Item.Properties()));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.OP_BLOCKS).register(entries -> {
            entries.accept(NPC_ADMIN_TOOL);
            entries.accept(CEREBELLUM_BOARD_BLOCK);
            entries.accept(CEREBELLUM_BOARD_MEDIUM_BLOCK);
            entries.accept(CEREBELLUM_BOARD_LARGE_BLOCK);
            entries.accept(CEREBELLUM_BOARD_XL_BLOCK);
        });
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cerebellum_board"), CEREBELLUM_BOARD_BE);

        PlayerPendingTracker.registerEvents();

        registerNpcChunkLoadEvent();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TallaiCommand.register(dispatcher);
            PlayNpcCommand.register(dispatcher);
        });

        registerServerNetworkHandlers();
        DeathEventHandler.register();

        // 玩家进入服务器时，将服务端默认 AI API URL 同步给客户端
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String defaultUrl = NpcAiServerConfig.getDefaultAiApiUrl();
            sender.sendPacket(new SyncAiConfigPacket(defaultUrl,
                PlayerMapGroupStorage.isMember(handler.getPlayer().getUUID()),
                NpcAdminStorage.isAdmin(handler.getPlayer().getUUID()),
                handler.getPlayer().hasPermissions(2)));
            // 同步小脑设置与排行榜数据（小脑榜方块依赖）
            sender.sendPacket(new SyncCerebellumSettingsPacket(
                CerebellumStorage.getSettings(), buildCerebellumLeaderboard(server)));

            // 同步问卷绑定邮箱状态（管理端按钮显示当前绑定问卷）
            MailBindingStorage.Binding mb = MailBindingStorage.get();
            sender.sendPacket(new SyncMailBindingPacket(mb.questionnaireId, bindingTitle(mb.questionnaireId)));

            // 同步投稿奖励设置（管理端投稿奖励分区显示）
            sender.sendPacket(new SyncContributionRewardsPacket(ContributionRewardStorage.getSettings()));

            // 投递未过期且该玩家尚未收到的邮件（含投稿奖励邮箱）
            deliverPendingMailToPlayer(handler.getPlayer());

            // 补发离线期间获得的投稿奖励（每期前三名：身份卡/抽奖次数需在线发放，邮箱已随上线投递）
            ServerPlayer joining = handler.getPlayer();
            ContributionRewardStorage.PendingReward pending = ContributionRewardStorage.takePending(joining.getUUID());
            if (pending != null) {
                grantReward(joining, pending.cards, pending.lottery);
                joining.sendSystemMessage(Component.literal("§6投稿奖励已发送至邮箱，请查收！"));
            }

            // 上线即应用称号（有称号的立即入队；无称号不触碰任何队伍）
            TitleManager.ensurePlayerTitle(joining);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            PlayerPendingTracker.load();
            PlayerMapGroupStorage.load();
            NpcAdminStorage.load();
            MailBindingStorage.load();
            ContributionRewardStorage.load();
            TitleStorage.load();
            // 清理称号系统自己名下的空队伍（离线改名残留/崩溃残留；绝不动其他队伍）
            TitleManager.cleanupOrphanTeams(server);
            // 预加载服务端 AI 配置，确保默认值已就绪
            NpcAiServerConfig.getDefaultAiApiUrl();
            // 预加载 NPC 单例数据，避免区块加载时触发磁盘扫描
            NpcDataManager.loadAll();
            // 全局传送点（所有 NPC 共享；首次加载自动从历史 NPC 数据迁移）
            TeleportPointStorage.load();
        });

        // 服务器启动完成后预生成 NPC，避免玩家进入时区块加载触发卡顿
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            NpcAdminStorage.ensureBuiltinAdmin(server);
            ensureCerebellumObjectives(server);
            // 启动时补结算：服务器离线期间到期未结算的投稿期
            autoSettleContributionPeriods(server);
            // 启动时重生所有未删除的NPC数据（支持多个NPC）
            for (NpcData data : NpcDataManager.loadAll()) {
                if (!data.hasPosition() || data.isDeleted()) continue;
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.dimension().location().toString().equals(data.getLevel())) {
                        // 确保目标区块已加载后再生成
                        level.getChunkSource().getChunk(
                            Mth.floor(data.getX()) >> 4,
                            Mth.floor(data.getZ()) >> 4,
                            true
                        );
                        if (level.getEntity(data.getNpcUuid()) == null) {
                            spawnNpcFromData(level, data);
                        }
                        break;
                    }
                }
            }

            // 网站互通桥接：mod 作为 WS 客户端连出（配置在 npcai-server.json 的 web_bridge 节）
            WebBridge.start(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                // 每10分钟整分时刻（分钟%10==0）提醒有未完成事项的玩家
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                if (now.getMinute() % 10 == 0 && now.getSecond() < 2 && lastReminderMinute != now.getMinute()) {
                    lastReminderMinute = now.getMinute();
                    sendPendingReminders(server);
                }
                // 每秒：有称号的玩家确保队伍存在/前缀正确/已入队（效果等同每刻 join）
                TitleManager.ensureAllTitles(server);
            }
            if (server.getTickCount() % 6000 == 0) { // 每 5 分钟保存一次
                NpcDataManager.saveAll();
                PlayerMapGroupStorage.save();
                TitleStorage.save();
            }
            // 每2秒检测小脑计分板变化并广播（管理员 /scoreboard 指令修改实时同步到管理端与小脑榜方块）
            if (server.getTickCount() % 40 == 0) {
                broadcastCerebellumIfChanged(server);
            }
            // 每60秒检测：投稿期数到期自动结算（每期时间一到自动结算前三名奖励）
            if (server.getTickCount() % 1200 == 0) {
                autoSettleContributionPeriods(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            NpcDataManager.saveAll();
            PlayerPendingTracker.save();
            PlayerMapGroupStorage.save();
            TitleStorage.save();
            WebBridge.stop();
        });

        LOGGER.info("NPC AI service initialized!");
    }

    private static String lastCerebellumDigest = "";

    private static void broadcastCerebellumIfChanged(MinecraftServer server) {
        try {
            var scoreboard = server.getScoreboard();
            var currentObj = scoreboard.getObjective("kgxnbang");
            var punishObj = scoreboard.getObjective("kgxnbang_punish");
            if (currentObj == null && punishObj == null) return;

            StringBuilder sb = new StringBuilder();
            for (var holder : scoreboard.getTrackedPlayers()) {
                String name = holder.getScoreboardName();
                if (name == null) continue;
                sb.append(name).append('=');
                try {
                    if (currentObj != null) sb.append(scoreboard.getOrCreatePlayerScore(holder, currentObj).get());
                } catch (Exception ignored) {
                }
                sb.append(',');
                try {
                    if (punishObj != null) sb.append(scoreboard.getOrCreatePlayerScore(holder, punishObj).get());
                } catch (Exception ignored) {
                }
                sb.append(';');
            }
            String digest = sb.toString();
            if (digest.equals(lastCerebellumDigest)) return;
            lastCerebellumDigest = digest;

            SyncCerebellumSettingsPacket sync = new SyncCerebellumSettingsPacket(
                CerebellumStorage.getSettings(), buildCerebellumLeaderboard(server));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, sync);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to broadcast cerebellum", e);
        }
    }

    private void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(RequestNpcDataPacket.TYPE, RequestNpcDataPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportRequestPacket.TYPE, TeleportRequestPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AddTeleportPointPacket.TYPE, AddTeleportPointPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveTeleportPointPacket.TYPE, RemoveTeleportPointPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveNpcSettingsPacket.TYPE, SaveNpcSettingsPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SubmitFeedbackPacket.TYPE, SubmitFeedbackPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CreateQuestionnairePacket.TYPE, CreateQuestionnairePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteQuestionnairePacket.TYPE, DeleteQuestionnairePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ExportQuestionnairePacket.TYPE, ExportQuestionnairePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SubmitQuestionnaireResponsePacket.TYPE, SubmitQuestionnaireResponsePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SendMailPacket.TYPE, SendMailPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(BindMailQuestionnairePacket.TYPE, BindMailQuestionnairePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestPlayerListPacket.TYPE, RequestPlayerListPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ManagePlayerPacket.TYPE, ManagePlayerPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestFeedbackPacket.TYPE, RequestFeedbackPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ExportFeedbackPacket.TYPE, ExportFeedbackPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMailsPacket.TYPE, RequestMailsPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenMailboxPacket.TYPE, OpenMailboxPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteMailPacket.TYPE, DeleteMailPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestQuestionnairesPacket.TYPE, RequestQuestionnairesPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(HideQuestionnairePacket.TYPE, HideQuestionnairePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveCompensationRulesPacket.TYPE, SaveCompensationRulesPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveCerebellumSettingsPacket.TYPE, SaveCerebellumSettingsPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveContributionRewardsPacket.TYPE, SaveContributionRewardsPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteNpcPacket.TYPE, DeleteNpcPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SubmitContributionPacket.TYPE, SubmitContributionPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestContributionsPacket.TYPE, RequestContributionsPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(LikeContributionPacket.TYPE, LikeContributionPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ApproveContributionPacket.TYPE, ApproveContributionPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncEquipmentPacket.TYPE, SyncEquipmentPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ExportCerebellumPacket.TYPE, ExportCerebellumPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ExecuteAiCommandPacket.TYPE, ExecuteAiCommandPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMapTeleportPacket.TYPE, RequestMapTeleportPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestTitlePacket.TYPE, RequestTitlePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveTitlePacket.TYPE, SaveTitlePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenMapTeleportPacket.TYPE, OpenMapTeleportPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncTitlePacket.TYPE, SyncTitlePacket.CODEC);

        PayloadTypeRegistry.playS2C().register(OpenNpcChatPacket.TYPE, OpenNpcChatPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenNpcAdminPacket.TYPE, OpenNpcAdminPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncNpcDataPacket.TYPE, SyncNpcDataPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncFeedbackPacket.TYPE, SyncFeedbackPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncQuestionnairesPacket.TYPE, SyncQuestionnairesPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPlayerListPacket.TYPE, SyncPlayerListPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncMailsPacket.TYPE, SyncMailsPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncMailBindingPacket.TYPE, SyncMailBindingPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncCompensationRulesPacket.TYPE, SyncCompensationRulesPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncCerebellumSettingsPacket.TYPE, SyncCerebellumSettingsPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncContributionRewardsPacket.TYPE, SyncContributionRewardsPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncAiConfigPacket.TYPE, SyncAiConfigPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncContributionsPacket.TYPE, SyncContributionsPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ExportResultPacket.TYPE, ExportResultPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ExecuteAiCommandResultPacket.TYPE, ExecuteAiCommandResultPacket.CODEC);
    }

    private void registerServerNetworkHandlers() {
        // 玩家请求同步 NPC 数据（打开传送界面时）
        ServerPlayNetworking.registerGlobalReceiver(RequestNpcDataPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.level().getEntity(payload.entityId()) instanceof CustomerServiceNpcEntity npc) {
                    sendNpcData(player, npc);
                }
            });
        });

        // 玩家请求传送到某传送点
        ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                // 对局进行中禁止传送（NPC管理员豁免）——服务端权威校验，防止绕过客户端
                if (isGameInProgressServer(player)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c游戏中无法使用传送"));
                    return;
                }
                // 全局传送点：所有 NPC 共享互通
                if (player.level().getEntity(payload.entityId()) instanceof CustomerServiceNpcEntity) {
                    for (TeleportPoint p : TeleportPointStorage.getPoints()) {
                        if (p.name().equals(payload.pointName())) {
                            player.teleportTo(p.x(), p.y(), p.z());
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§a已传送到：" + p.name()
                            ));
                            return;
                        }
                    }
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c传送点不存在"));
                }
            });
        });

        // 管理员添加传送点（全局存储，所有 NPC 共享互通）
        ServerPlayNetworking.registerGlobalReceiver(AddTeleportPointPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.level().getEntity(payload.entityId()) instanceof CustomerServiceNpcEntity npc) {
                    // 同名传送点直接替换，避免重复
                    TeleportPointStorage.addPoint(new TeleportPoint(
                        payload.name(), payload.x(), payload.y(), payload.z(), System.currentTimeMillis(),
                        payload.category() != null ? payload.category() : "其他"
                    ));
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a已添加传送点：" + payload.name()
                    ));
                    sendNpcData(player, npc);
                }
            });
        });

        // 管理员删除传送点（全局存储）
        ServerPlayNetworking.registerGlobalReceiver(RemoveTeleportPointPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.level().getEntity(payload.entityId()) instanceof CustomerServiceNpcEntity npc) {
                    TeleportPointStorage.removePoint(payload.name());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a已删除传送点：" + payload.name()
                    ));
                    sendNpcData(player, npc);
                }
            });
        });

        // 管理员保存设置
        ServerPlayNetworking.registerGlobalReceiver(SaveNpcSettingsPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.level().getEntity(payload.entityId()) instanceof CustomerServiceNpcEntity npc) {
                    npc.setNpcName(payload.displayName());
                    npc.setSkinName(payload.skinName());
                    npc.setPos(payload.x(), payload.y(), payload.z());
                    try {
                        npc.setFollowMode(NpcData.FollowMode.values()[payload.followMode()]);
                    } catch (Exception e) {
                        npc.setFollowMode(NpcData.FollowMode.FIXED);
                    }
                    try {
                        npc.setViewMode(NpcData.ViewMode.values()[payload.viewMode()]);
                    } catch (Exception e) {
                        npc.setViewMode(NpcData.ViewMode.RANDOM);
                    }
                    npc.setScale(payload.scale());
                    npc.setHeldItem(payload.heldItem());

                    NpcData data = NpcDataManager.get(npc.getUUID());
                    data.setDisplayName(payload.displayName());
                    data.setSkinName(payload.skinName());
                    data.setFollowMode(npc.getFollowMode());
                    data.setViewMode(npc.getViewMode());
                    data.setScale(npc.getScale());
                    data.setHeldItem(npc.getHeldItemId());
                    data.setPos(payload.x(), payload.y(), payload.z());
                    data.setLevel(player.level().dimension().location().toString());
                    data.setRoamCenter(payload.roamX(), payload.roamY(), payload.roamZ());
                    data.setRoamRadius(payload.roamRadius());
                    NpcDataManager.save(data);
                    // 保存后自动传送到活动中心
                    if (data.hasRoamLimit()) {
                        npc.teleportTo(payload.roamX(), payload.roamY(), payload.roamZ());
                        npc.setDeltaMovement(0, 0, 0);
                    }
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aNPC 设置已保存"));
                }
            });
        });

        // 管理员删除 NPC
        ServerPlayNetworking.registerGlobalReceiver(DeleteNpcPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                CustomerServiceNpcEntity npc = null;
                // 按 entityId 查找
                if (player.level().getEntity(payload.entityId()) instanceof CustomerServiceNpcEntity e) {
                    npc = e;
                }
                if (npc != null) {
                    UUID npcUuid = npc.getUUID();
                    npc.discard();
                    NpcDataManager.delete(npcUuid);
                    player.sendSystemMessage(Component.literal("§aNPC 已删除（数据已保留，可重新创建）"));
                } else {
                    player.sendSystemMessage(Component.literal("§c未找到NPC"));
                }
            });
        });

        // 玩家提交反馈
        ServerPlayNetworking.registerGlobalReceiver(SubmitFeedbackPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                FeedbackStorage.save(player.getName().getString(), payload.anonymous(), payload.content());
                PlayerPendingTracker.clearFeedbackPending(player.getUUID());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a反馈已提交，感谢建议"));
            });
        });

        // 管理员创建问卷
        ServerPlayNetworking.registerGlobalReceiver(CreateQuestionnairePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Questionnaire q = new Questionnaire(payload.id());
                q.setTitle(payload.title());
                q.setQuestions(payload.questions());
                q.setHints(payload.hints());
                q.setStartAt(payload.startAt());
                q.setEndAt(payload.endAt());
                q.setCreatedAt(System.currentTimeMillis());
                QuestionnaireStorage.save(q);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a问卷已发布：" + q.getTitle()));
                syncQuestionnaires(player);
            });
        });

        // 管理员删除问卷
        ServerPlayNetworking.registerGlobalReceiver(DeleteQuestionnairePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                QuestionnaireStorage.delete(payload.questionnaireId());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a问卷已删除"));
                syncQuestionnaires(player);
            });
        });

        // 管理员导出问卷（导出内容发回客户端，由客户端保存到本地游戏文件夹）
        ServerPlayNetworking.registerGlobalReceiver(ExportQuestionnairePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Questionnaire q = QuestionnaireStorage.get(payload.questionnaireId());
                if (q == null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c问卷不存在"));
                    return;
                }
                String[] result = QuestionnaireStorage.exportToMarkdownText(q);
                if (result != null) {
                    ServerPlayNetworking.send(player, new ExportResultPacket("diaocha", result[0], result[1]));
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a问卷已导出：" + result[0]));
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c问卷导出失败"));
                }
            });
        });

        // 玩家提交问卷
        ServerPlayNetworking.registerGlobalReceiver(SubmitQuestionnaireResponsePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Questionnaire q = QuestionnaireStorage.get(payload.questionnaireId());
                if (q == null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c问卷不存在"));
                    return;
                }
                if (q.hasResponded(player.getName().getString())) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你已经填写过这份问卷了"));
                    return;
                }
                QuestionnaireStorage.addResponse(payload.questionnaireId(), player.getName().getString(), payload.answers());
                PlayerPendingTracker.clearQuestionnairePending(player.getUUID(), payload.questionnaireId());

                // 问卷绑定邮箱：绑定后玩家首次提交问卷，自动把绑定模板邮件发送到提交玩家邮箱
                boolean mailSent = false;
                try {
                    MailBindingStorage.Binding binding = MailBindingStorage.get();
                    if (binding.questionnaireId.equals(q.getId().toString())) {
                        String pName = player.getName().getString();
                        if (!binding.mailedPlayers.contains(pName)) {
                            MailBridge.sendMail(player, "系统", binding.title, binding.content,
                                binding.endAt, binding.cards, binding.lotteryCount);
                            binding.mailedPlayers.add(pName);
                            MailBindingStorage.save();
                            mailSent = true;
                        }
                    }
                } catch (Exception e) {
                    NpcAiMod.LOGGER.error("Failed to send bound questionnaire mail", e);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    mailSent ? "§a问卷提交成功，邮件已自动发送到你的邮箱" : "§a问卷提交成功"));
            });
        });

        // 管理员发送邮件
        ServerPlayNetworking.registerGlobalReceiver(SendMailPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                sendMailToPlayers(admin, payload);
            });
        });

        // 管理端绑定/解除问卷→邮件（绑定瞬间快照当前邮件模板：标题/内容/指令/抽奖/有效期）
        ServerPlayNetworking.registerGlobalReceiver(BindMailQuestionnairePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                if (!admin.hasPermissions(2)) return;
                try {
                    MailBindingStorage.Binding binding = MailBindingStorage.get();
                    if (payload.questionnaireId().isEmpty()) {
                        binding.clear();
                        MailBindingStorage.save();
                        admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已解除问卷绑定"));
                    } else {
                        Questionnaire q = QuestionnaireStorage.get(UUID.fromString(payload.questionnaireId()));
                        if (q == null) {
                            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c问卷不存在"));
                            return;
                        }
                        if (!binding.questionnaireId.equals(payload.questionnaireId())) {
                            binding.mailedPlayers.clear(); // 换绑其他问卷：清空已发记录
                        }
                        binding.questionnaireId = payload.questionnaireId();
                        binding.title = payload.title();
                        binding.content = payload.content();
                        binding.cards = payload.cards();
                        binding.lotteryCount = Math.max(0, payload.lotteryCount());
                        binding.endAt = payload.endAt();
                        MailBindingStorage.save();
                        admin.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§a已绑定问卷《" + q.getTitle() + "》：玩家首次提交后自动发送邮件"));
                    }
                    ServerPlayNetworking.send(admin, new SyncMailBindingPacket(
                        binding.questionnaireId, bindingTitle(binding.questionnaireId)));
                } catch (Exception e) {
                    NpcAiMod.LOGGER.error("Failed to bind mail questionnaire", e);
                    admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c问卷绑定失败"));
                }
            });
        });

        // 管理员请求在线玩家列表
        ServerPlayNetworking.registerGlobalReceiver(RequestPlayerListPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                syncPlayerList(admin);
            });
        });

        // 管理员管理玩家
        ServerPlayNetworking.registerGlobalReceiver(ManagePlayerPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                ServerPlayer target = context.server().getPlayerList().getPlayer(payload.playerId());
                if (target == null) {
                    admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c目标玩家不在线"));
                    return;
                }
                switch (payload.action()) {
                    case "op" -> {
                        if (target.hasPermissions(2)) {
                            context.server().getPlayerList().deop(target.getGameProfile());
                            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已取消 " + target.getName().getString() + " 的管理员权限"));
                        } else {
                            context.server().getPlayerList().op(target.getGameProfile());
                            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已设置 " + target.getName().getString() + " 为管理员"));
                        }
                    }
                    case "mapgroup" -> {
                        if (PlayerMapGroupStorage.isMember(target.getUUID())) {
                            PlayerMapGroupStorage.remove(target.getUUID());
                            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已将 " + target.getName().getString() + " 移出地图组"));
                        } else {
                            PlayerMapGroupStorage.add(target.getUUID());
                            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已将 " + target.getName().getString() + " 添加为地图组成员"));
                        }
                    }
                    case "npcadmin" -> {
                        if (NpcAdminStorage.isAdmin(target.getUUID())) {
                            NpcAdminStorage.removeAdmin(target.getUUID());
                            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已取消 " + target.getName().getString() + " 的NPC管理权限"));
                        } else {
                            NpcAdminStorage.addAdmin(target.getUUID());
                            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已给予 " + target.getName().getString() + " NPC管理权限"));
                        }
                    }
                    case "role" -> {
                        String roleName = payload.value().trim();
                        String roleId = RoleRepository.getEnglishId(roleName);
                        executeCommand(admin.getServer(), "changeRole " + target.getName().getString() + " " + roleId);
                        admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已给予身份 " + roleName + "(" + roleId + ")，下回合生效"));
                    }
                    default -> admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c未知操作"));
                }
                syncPlayerList(admin);
            });
        });

        // 管理员请求反馈列表
        ServerPlayNetworking.registerGlobalReceiver(RequestFeedbackPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                List<FeedbackEntry> filtered = new ArrayList<>();
                for (FeedbackEntry e : FeedbackStorage.loadAll()) {
                    if (e.timestamp() >= payload.startAt() && e.timestamp() <= payload.endAt()) {
                        filtered.add(e);
                    }
                }
                ServerPlayNetworking.send(admin, new SyncFeedbackPacket(filtered));
            });
        });

        // 管理员导出选中反馈（导出内容发回客户端，由客户端保存到本地游戏文件夹）
        ServerPlayNetworking.registerGlobalReceiver(ExportFeedbackPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                try {
                    String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    String fileName = "导出_" + date + ".md";
                    StringBuilder sb = new StringBuilder();
                    for (FeedbackEntry e : FeedbackStorage.loadAll()) {
                        if (!payload.fileNames().contains(e.fileName())) continue;
                        String name = e.anonymous() ? "匿名玩家" : e.playerName();
                        sb.append(name).append("\n");
                        sb.append(formatFeedbackTime(e.timestamp())).append("\n");
                        sb.append(e.content()).append("\n\n");
                    }
                    ServerPlayNetworking.send(admin, new ExportResultPacket("fankui", fileName, sb.toString()));
                    admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a反馈已导出：" + fileName));
                } catch (Exception e) {
                    NpcAiMod.LOGGER.error("Failed to export feedback", e);
                    admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c反馈导出失败"));
                }
            });
        });

        // 管理员请求邮件记录列表
        ServerPlayNetworking.registerGlobalReceiver(RequestMailsPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                ServerPlayNetworking.send(admin, new SyncMailsPacket(MailStorage.loadAll()));
            });
        });

        // 玩家请求打开列车邮箱界面（接入 habitrain_lottery 邮箱系统）
        ServerPlayNetworking.registerGlobalReceiver(OpenMailboxPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                MailBridge.openMailbox(context.player());
            });
        });

        // 管理员删除邮件记录
        ServerPlayNetworking.registerGlobalReceiver(DeleteMailPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer admin = context.player();
                MailStorage.delete(payload.mailId());
                ServerPlayNetworking.send(admin, new SyncMailsPacket(MailStorage.loadAll()));
                admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a邮件记录已删除"));
            });
        });

        // 玩家请求问卷列表
        ServerPlayNetworking.registerGlobalReceiver(RequestQuestionnairesPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                List<Questionnaire> all = QuestionnaireStorage.loadAll();
                all = QuestionnaireStorage.filterVisibleForPlayer(all, player.getUUID());
                ServerPlayNetworking.send(player, new SyncQuestionnairesPacket(all));
            });
        });

        // 玩家隐藏已填写问卷
        ServerPlayNetworking.registerGlobalReceiver(HideQuestionnairePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                QuestionnaireStorage.hideForPlayer(player.getUUID(), payload.questionnaireId());
                List<Questionnaire> visible = QuestionnaireStorage.filterVisibleForPlayer(QuestionnaireStorage.loadAll(), player.getUUID());
                ServerPlayNetworking.send(player, new SyncQuestionnairesPacket(visible));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已隐藏该问卷"));
            });
        });

        // 管理员保存补偿规则
        ServerPlayNetworking.registerGlobalReceiver(SaveCompensationRulesPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                CompensationStorage.setAll(payload.rules());
                SyncCompensationRulesPacket sync = new SyncCompensationRulesPacket(CompensationStorage.getAll());
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, sync);
                }
            });
        });

        // 玩家提交投稿
        ServerPlayNetworking.registerGlobalReceiver(SubmitContributionPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                String title = payload.title().trim();
                if (title.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§c投稿标题不能为空"));
                    return;
                }
                if (!Contribution.TYPE_ROLE.equals(payload.contributionType())
                    && !Contribution.TYPE_MODIFIER.equals(payload.contributionType())) {
                    player.sendSystemMessage(Component.literal("§c投稿类型无效"));
                    return;
                }
                // 角色投稿必须选择阵营（修饰符投稿无阵营概念）
                String faction = payload.faction().trim();
                if (Contribution.TYPE_ROLE.equals(payload.contributionType())
                    && !java.util.Arrays.asList(Contribution.FACTIONS).contains(faction)) {
                    player.sendSystemMessage(Component.literal("§c请选择阵营"));
                    return;
                }
                // 每期投稿上限：角色+修饰符两个分区合计最多5个
                if (ContributionStorage.countSubmissions(player.getUUID(), Contribution.getCurrentPeriod())
                    >= ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD) {
                    player.sendSystemMessage(Component.literal("§c本期两个分区合计最多投稿" + ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD + "个内容"));
                    return;
                }
                Contribution c = new Contribution(UUID.randomUUID());
                c.setType(payload.contributionType());
                c.setTitle(title);
                c.setShortDesc(payload.shortDesc().trim());
                c.setDescription(payload.description().trim());
                c.setShop(payload.shop().trim());
                c.setBackground(payload.background().trim());
                c.setFaction(faction);
                c.setAuthorName(player.getName().getString());
                c.setAuthorId(player.getUUID());
                c.setCreatedAt(System.currentTimeMillis());
                c.setPeriod(Contribution.getCurrentPeriod());
                ContributionStorage.save(c);
                // 投稿奖励改为审核通过后发放（管理端审核时发送奖励邮件），此处只提示等待审核
                player.sendSystemMessage(Component.literal("§a请等待审核通过！奖励会以邮箱形式发送！"));
                LOGGER.info("New contribution '{}' by {}", c.getTitle(), c.getAuthorName());
            });
        });

        // AI对话指令执行（玩家输入yes确认后触发；服务端按身份二次校验权限，防绕过）
        ServerPlayNetworking.registerGlobalReceiver(ExecuteAiCommandPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                String command = payload.command();
                if (player == null || command == null || command.trim().isEmpty()) {
                    return;
                }
                String cleaned = command.trim();
                while (cleaned.startsWith("/")) {
                    cleaned = cleaned.substring(1).trim();
                }
                String reject = validateAiCommand(player, cleaned);
                if (reject != null) {
                    ServerPlayNetworking.send(player, new ExecuteAiCommandResultPacket("§c" + reject));
                    return;
                }
                executeAiCommand(context.server(), player, cleaned);
                ServerPlayNetworking.send(player, new ExecuteAiCommandResultPacket("§a指令已执行：" + cleaned));
                LOGGER.info("AI对话指令执行 by {}: {}", player.getName().getString(), cleaned);
            });
        });

        // 请求投稿列表
        ServerPlayNetworking.registerGlobalReceiver(RequestContributionsPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                sendContributionsTo(player);
            });
        });

        // 点赞/取消点赞
        ServerPlayNetworking.registerGlobalReceiver(LikeContributionPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                // 不能给自己的投稿点赞
                Contribution likeTarget = ContributionStorage.get(payload.contributionId());
                if (likeTarget != null
                    && likeTarget.getAuthorId() != null
                    && likeTarget.getAuthorId().equals(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§c不能给自己的投稿点赞"));
                    return;
                }
                Boolean result = ContributionStorage.toggleLike(player.getUUID(), payload.contributionId());
                if (result == null) {
                    player.sendSystemMessage(Component.literal("§c投稿不存在或今日点赞次数已用完"));
                    return;
                }
                if (result) {
                    player.sendSystemMessage(Component.literal("§a点赞成功！今日剩余 " + ContributionStorage.getRemainingLikes(player.getUUID()) + " 次"));
                } else {
                    player.sendSystemMessage(Component.literal("§7已取消点赞"));
                }
                sendContributionsTo(player);
            });
        });

        // 管理端审核投稿（通过=发投稿奖励邮件+作品归属期改为通过当期；不通过=删除作品+发驳回邮件）
        ServerPlayNetworking.registerGlobalReceiver(ApproveContributionPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                try {
                    // 仅管理端可审核
                    if (player == null || !NpcAdminStorage.isAdmin(player.getUUID())) {
                        return;
                    }
                    Contribution c = ContributionStorage.get(payload.contributionId());
                    if (c == null) {
                        return;
                    }
                    // 防重复审核：已审核通过的作品忽略重复点击（审字与详细页审核按钮同时消失），不会重复发放奖励
                    if (c.isApproved()) {
                        return;
                    }
                    if (payload.approved()) {
                        // 审核通过：作品归属期数 = 审核通过时间所在期
                        c.setApproved(true);
                        c.setPeriod(Contribution.getCurrentPeriod());
                        ContributionStorage.save(c);
                        // 发送投稿奖励邮件（设置中的每次投稿奖励，永久有效邮箱）
                        ContributionRewardSettings settings = ContributionRewardStorage.getSettings();
                        String rewardSummary = buildRewardSummary(settings.getPerSubmitCards(), settings.getPerSubmitLottery());
                        sendRewardMail(context.server(), c.getAuthorName(), "投稿奖励！",
                            "恭喜您的投稿通过审核，请点击下方领取投稿奖励！"
                                + (rewardSummary.isEmpty() ? "" : "\n奖励：" + rewardSummary));
                        if (c.getAuthorId() != null) {
                            ServerPlayer author = context.server().getPlayerList().getPlayer(c.getAuthorId());
                            if (author != null) {
                                grantReward(author, settings.getPerSubmitCards(), settings.getPerSubmitLottery());
                                author.sendSystemMessage(Component.literal(
                                    "§a您的投稿《" + c.getTitle() + "》已通过审核，奖励已发送至邮箱！"));
                            } else {
                                // 离线：奖励计入待领，上线时补发
                                ContributionRewardStorage.addPending(c.getAuthorId(), settings.getPerSubmitCards(), settings.getPerSubmitLottery());
                            }
                        }
                        player.sendSystemMessage(Component.literal("§a已审核通过：" + c.getTitle()));
                        LOGGER.info("Contribution approved by {}: {}", player.getName().getString(), c.getTitle());
                    } else {
                        // 审核不通过：直接删除作品 + 发送驳回邮件（无奖励）
                        sendRewardMail(context.server(), c.getAuthorName(), "投稿驳回！",
                            "您的投稿由于特殊原因没有通过审核，无法领取投稿奖励。");
                        String title = c.getTitle();
                        ContributionStorage.delete(c.getId());
                        player.sendSystemMessage(Component.literal("§a已驳回并删除投稿：" + title));
                        LOGGER.info("Contribution rejected by {}: {}", player.getName().getString(), title);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to process contribution approval {}", payload.contributionId(), e);
                } finally {
                    // 无论成功失败都刷新管理端列表，保证审核结果立即可见（审字/审核按钮随之消失）
                    if (player != null) {
                        sendContributionsTo(player);
                    }
                }
            });
        });

        // 管理端保存投稿奖励设置（NPC管理员或OP均可修改）
        ServerPlayNetworking.registerGlobalReceiver(SaveContributionRewardsPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer editor = context.player();
                if (!(NpcAdminStorage.isAdmin(editor.getUUID()) || editor.hasPermissions(2))) {
                    editor.sendSystemMessage(Component.literal("§c无权限修改投稿奖励设置"));
                    return;
                }
                ContributionRewardStorage.setSettings(payload.settings());
                LOGGER.info("投稿奖励设置已保存：由 {} 修改", context.player().getName().getString());
                SyncContributionRewardsPacket sync = new SyncContributionRewardsPacket(ContributionRewardStorage.getSettings());
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, sync);
                }
            });
        });

        // 投稿奖励结算已改为自动：每期到期后由服务器自动结算（见 autoSettleContributionPeriods）

        // 同步装备：把玩家当前主副手+全部装备复制到NPC（空手/空槽=清空）
        ServerPlayNetworking.registerGlobalReceiver(SyncEquipmentPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                CustomerServiceNpcEntity npc = null;
                if (player.level().getEntity(player.getId()) instanceof CustomerServiceNpcEntity e) {
                    npc = e;
                } else if (player.getServer() != null) {
                    // 优先找任意一个已存在的NPC（支持多个NPC）
                    UUID anyNpcUuid = NpcDataManager.findAnyNpcUuid();
                    if (anyNpcUuid != null) {
                        for (ServerLevel level : player.getServer().getAllLevels()) {
                            if (level.getEntity(anyNpcUuid) instanceof CustomerServiceNpcEntity e) {
                                npc = e;
                                break;
                            }
                        }
                    }
                }
                if (npc == null) {
                    player.sendSystemMessage(Component.literal("§c未找到NPC客服"));
                    return;
                }
                NpcData data = NpcDataManager.get(npc.getUUID());
                data.setEquipment(
                    itemIdOf(player.getMainHandItem()),
                    itemIdOf(player.getOffhandItem()),
                    itemIdOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)),
                    itemIdOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)),
                    itemIdOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)),
                    itemIdOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET))
                );
                NpcDataManager.save(data);
                // 应用到NPC实体
                npc.refreshEquipmentFromData();
                player.sendSystemMessage(Component.literal("§a已同步装备到NPC（含主副手和全部装备）"));
            });
        });

        // 地图组成员按X键请求打开传送面板：服务端确认身份 + 查找NPC
        ServerPlayNetworking.registerGlobalReceiver(RequestMapTeleportPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                // 对局进行中禁止打开传送面板（NPC管理员豁免）——服务端权威校验
                if (isGameInProgressServer(player)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c游戏中无法使用传送"));
                    return;
                }
                // 地图组成员检查（NPC管理员豁免：服主/管理员不受成员限制，与对局拦截逻辑一致）
                boolean isMember = PlayerMapGroupStorage.isMember(player.getUUID())
                    || NpcAdminStorage.isAdmin(player.getUUID());
                if (!isMember) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c你不是地图组成员，无法使用传送面板（请管理员在管理界面添加）"));
                    return;
                }
                int entityId = -1;
                String npcName = "";
                // 支持多个NPC：优先找任意一个已存在的NPC
                UUID anyNpcUuid = NpcDataManager.findAnyNpcUuid();
                if (anyNpcUuid != null) {
                    for (ServerLevel level : context.server().getAllLevels()) {
                        if (level.getEntity(anyNpcUuid) instanceof CustomerServiceNpcEntity npc) {
                            entityId = npc.getId();
                            npcName = npc.getNpcName();
                            break;
                        }
                    }
                }
                if (entityId < 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c服务器还没有配置NPC，请先在管理界面添加NPC"));
                    return;
                }
                ServerPlayNetworking.send(player, new OpenMapTeleportPacket(true, entityId, npcName));
            });
        });

        // 玩家打开称号页：回发当前称号状态（预填编辑框 + 展示区）
        ServerPlayNetworking.registerGlobalReceiver(RequestTitlePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> TitleManager.handleRequest(context.player()));
        });

        // 玩家点击称号页确认：校验 → 落盘 → 立即生效 → 回包
        ServerPlayNetworking.registerGlobalReceiver(SaveTitlePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> TitleManager.handleSave(context.player(), payload));
        });

        // 导出小脑榜为md文档
        ServerPlayNetworking.registerGlobalReceiver(ExportCerebellumPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                try {
                    Map<UUID, Integer> deathCounts = CerebellumStorage.getAllCounts();
                    Map<UUID, Integer> punishmentCounts = CerebellumStorage.getAllPunishmentCounts();
                    Map<UUID, Integer> pendingCounts = CerebellumStorage.getAllPendingCounts();
                    Set<UUID> allUuids = new HashSet<>();
                    allUuids.addAll(deathCounts.keySet());
                    allUuids.addAll(punishmentCounts.keySet());
                    allUuids.addAll(pendingCounts.keySet());

                    // 玩家名
                    Map<UUID, String> nameMap = new HashMap<>();
                    for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                        nameMap.put(p.getUUID(), p.getName().getString());
                    }
                    try {
                        for (var holder : context.server().getScoreboard().getTrackedPlayers()) {
                            String hn = holder.getScoreboardName();
                            if (hn == null || hn.isEmpty()) continue;
                            var profileOpt = context.server().getProfileCache().get(hn);
                            if (profileOpt.isPresent()) {
                                nameMap.putIfAbsent(profileOpt.get().getId(), hn);
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    // 排序：惩罚降序 → 当前降序
                    List<UUID> sorted = new ArrayList<>(allUuids);
                    sorted.sort((a, b) -> {
                        int cmp = Integer.compare(punishmentCounts.getOrDefault(b, 0), punishmentCounts.getOrDefault(a, 0));
                        if (cmp != 0) return cmp;
                        return Integer.compare(deathCounts.getOrDefault(b, 0), deathCounts.getOrDefault(a, 0));
                    });

                    int requiredDeaths = CerebellumStorage.getSettings().getRequiredDeaths();
                    StringBuilder sb = new StringBuilder();
                    sb.append("# 小脑榜\n\n");
                    sb.append("| 序号 | 玩家 | 小脑次数(").append(requiredDeaths).append("次) | 惩罚次数 | 待执行 |\n");
                    sb.append("|---|---|---|---|---|\n");
                    int idx = 1;
                    for (UUID uuid : sorted) {
                        String name = nameMap.getOrDefault(uuid, uuid.toString().substring(0, 8));
                        sb.append("| ").append(idx++).append(" | ").append(name)
                            .append(" | ").append(deathCounts.getOrDefault(uuid, 0))
                            .append(" | ").append(punishmentCounts.getOrDefault(uuid, 0))
                            .append(" | ").append(pendingCounts.getOrDefault(uuid, 0)).append(" |\n");
                    }

                    String fileName = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".md";
                    // 导出内容发回客户端，由客户端保存到本地游戏文件夹
                    ServerPlayNetworking.send(player, new ExportResultPacket("xiaonao", fileName, sb.toString()));
                    player.sendSystemMessage(Component.literal("§a小脑榜已导出：" + fileName));
                } catch (Exception e) {
                    LOGGER.error("Failed to export cerebellum", e);
                    player.sendSystemMessage(Component.literal("§c小脑榜导出失败"));
                }
            });
        });

        // 管理员保存小脑设置
        ServerPlayNetworking.registerGlobalReceiver(SaveCerebellumSettingsPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                CerebellumStorage.setSettings(payload.settings());
                LOGGER.info("小脑设置已保存：由 {} 修改 [诅咒={}, 高大={}, 晕血症={}, 纳税={}, 偏执={}, 沙哑={}, 次数阈值={}]",
                    context.player().getName().getString(),
                    payload.settings().isCursedEnabled(), payload.settings().isTallEnabled(),
                    payload.settings().isHemophobiaEnabled(), payload.settings().isTaxedEnabled(),
                    payload.settings().isParanoidEnabled(), payload.settings().isHoarseEnabled(),
                    payload.settings().getRequiredDeaths());
                SyncCerebellumSettingsPacket sync = new SyncCerebellumSettingsPacket(
                    CerebellumStorage.getSettings(), buildCerebellumLeaderboard(context.server()));
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, sync);
                }
            });
        });
    }

    private static String formatFeedbackTime(long millis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static int lastReminderMinute = -1;

    private static void sendPendingReminders(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int mailCount = 0;
            try {
                mailCount = MailBridge.getUnreadCount(player);
            } catch (Exception ignored) {
            }
            int questionnaireCount = 0;
            try {
                String playerName = player.getName().getString();
                for (com.kghua.npcai.data.Questionnaire q : com.kghua.npcai.data.QuestionnaireStorage.loadAll()) {
                    if (q.isActive() && !q.hasResponded(playerName)) {
                        questionnaireCount++;
                    }
                }
            } catch (Exception ignored) {
            }
            if (mailCount > 0) {
                player.sendSystemMessage(Component.literal("§e[提醒] 您有" + mailCount + "封未读邮件！"));
            }
            if (questionnaireCount > 0) {
                player.sendSystemMessage(Component.literal("§e[提醒] 您有" + questionnaireCount + "个未填问卷！"));
            }
        }
    }

    private void registerNpcChunkLoadEvent() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world.isClientSide) return;
            // 支持多个NPC：遍历所有未删除的NPC数据，命中本区块则重生
            for (NpcData data : NpcDataManager.loadAll()) {
                if (!data.hasPosition() || data.isDeleted()) continue;
                if (!data.getLevel().equals(world.dimension().location().toString())) continue;
                // 已存在则跳过
                if (world.getEntity(data.getNpcUuid()) != null) continue;

                ChunkPos npcChunk = new ChunkPos(
                    Mth.floor(data.getX()) >> 4,
                    Mth.floor(data.getZ()) >> 4
                );
                if (!chunk.getPos().equals(npcChunk)) continue;

                spawnNpcFromData(world, data);
            }
        });
    }

    private static void spawnNpcFromData(ServerLevel world, NpcData data) {
        if (!data.hasPosition()) return;
        CustomerServiceNpcEntity npc = new CustomerServiceNpcEntity(
            data.getNpcUuid(), world, data.getX(), data.getY(), data.getZ(), data.getSkinName(), data.getDisplayName()
        );
        npc.setFollowMode(data.getFollowMode());
        npc.setViewMode(data.getViewMode());
        npc.setScale(data.getScale());
        npc.setHeldItem(data.getHeldItem());
        world.addFreshEntity(npc);
        LOGGER.info("Respawned NPC from data: {} at [{}, {}, {}] in {}",
            data.getNpcUuid(), data.getX(), data.getY(), data.getZ(), data.getLevel());
    }

    private static void executeCommand(net.minecraft.server.MinecraftServer server, String command) {
        if (server == null) return;
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        server.getCommands().performPrefixedCommand(source, command);
    }

    /** AI对话可用的消息类指令白名单（无权限玩家仅此6种） */
    private static final java.util.Set<String> AI_MESSAGE_COMMANDS =
        java.util.Set.of("say", "tell", "me", "teammsg", "tellraw", "title");

    /**
     * AI对话指令权限校验（服务端权威，防客户端绕过）：
     * 管理员 = OP玩家（hasPermissions(2)）或 NPC管理员，全部指令；地图组 = 消息类指令 + 模式修改指令 playnpc ditu creative/adventure；
     * 普通玩家 = 仅消息类指令（say/tell/me/teammsg/tellraw/title）。
     * 允许 execute as <目标> run <指令> 前缀形式。返回 null 表示允许，否则返回拒绝原因。
     */
    private static String validateAiCommand(ServerPlayer player, String rawCommand) {
        if (NpcAdminStorage.isAdmin(player.getUUID()) || player.hasPermissions(2)) {
            return null; // OP / NPC管理员：全部指令
        }
        String cmd = rawCommand.trim();
        while (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        String inner = cmd;
        String[] tokens = cmd.split("\\s+");
        // 允许 execute as <目标> run <指令> 形式（如 execute as 11 run say 123）
        if (tokens.length >= 5 && tokens[0].equals("execute") && tokens[1].equals("as")
            && tokens[3].equals("run")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 4; i < tokens.length; i++) {
                if (i > 4) sb.append(' ');
                sb.append(tokens[i]);
            }
            inner = sb.toString();
            if (inner.isEmpty()) {
                return "指令格式错误，无法执行";
            }
        }
        String first = inner.split("\\s+", 2)[0].toLowerCase();
        if (AI_MESSAGE_COMMANDS.contains(first)) {
            return null;
        }
        if (PlayerMapGroupStorage.isMember(player.getUUID())) {
            // 地图组额外允许模式修改指令
            String[] it = inner.split("\\s+");
            if (it.length == 3 && it[0].equals("playnpc") && it[1].equals("ditu")
                && (it[2].equals("creative") || it[2].equals("adventure"))) {
                return null;
            }
        }
        return "该指令不在您的权限范围内，无法执行";
    }

    /** 以玩家身份（提升至4级权限）执行AI对话指令，保证 playnpc 等命令内的玩家身份校验能通过 */
    private static void executeAiCommand(net.minecraft.server.MinecraftServer server, ServerPlayer player, String command) {
        if (server == null || player == null) return;
        CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        server.getCommands().performPrefixedCommand(source, command);
    }

    /** 服务端判断当前是否对局进行中（NPC管理员豁免传送限制） */
    private static boolean isGameInProgressServer(ServerPlayer player) {
        try {
            if (NpcAdminStorage.isAdmin(player.getUUID())) return false;
            // 死者休息区（habitrain_core：对局中死亡后返回大厅休息的玩家）同样禁止传送
            if (isPlayerResting(player)) return true;
            return io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level()).isRunning();
        } catch (Exception e) {
            // 异常时保守拦截
            return true;
        }
    }

    /**
     * 反射检测 habitrain_core 的"死者休息区"状态（对局中死亡后返回大厅休息）。
     * 未安装该mod或版本不兼容时返回 false，不影响其他功能。
     */
    private static boolean isPlayerResting(ServerPlayer player) {
        try {
            Class<?> clazz = Class.forName("com.habitrain.core.game.sre.EliminatedRestAreaService");
            return (boolean) clazz.getMethod("isResting", ServerPlayer.class).invoke(null, player);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void syncQuestionnaires(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncQuestionnairesPacket(QuestionnaireStorage.loadAll()));
    }

    private static void syncPlayerList(ServerPlayer admin) {
        List<SyncPlayerListPacket.PlayerInfo> list = new ArrayList<>();
        Scoreboard scoreboard = admin.getServer().getScoreboard();
        for (ServerPlayer p : admin.getServer().getPlayerList().getPlayers()) {
            PlayerTeam team = scoreboard.getPlayersTeam(p.getScoreboardName());
            String teamName = "";
            String teamColor = "";
            String playerColor = "";
            if (team != null) {
                teamName = team.getDisplayName().getString();
                TextColor tc = team.getDisplayName().getStyle().getColor();
                if (tc != null) {
                    teamColor = String.format("#%06X", tc.getValue() & 0xFFFFFF);
                }
                if (team.getColor() != ChatFormatting.RESET && team.getColor().getColor() != null) {
                    playerColor = formatTeamColor(team.getColor());
                }
            }
            boolean mapGroup = PlayerMapGroupStorage.isMember(p.getUUID());
            boolean npcAdmin = NpcAdminStorage.isAdmin(p.getUUID());
            list.add(new SyncPlayerListPacket.PlayerInfo(p.getUUID(), p.getName().getString(), p.hasPermissions(2), teamName, teamColor, playerColor, mapGroup, npcAdmin));
        }
        ServerPlayNetworking.send(admin, new SyncPlayerListPacket(list));
    }

    private static String formatTeamColor(ChatFormatting color) {
        Integer rgb = color.getColor();
        if (rgb == null || rgb == -1) return "";
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static ChatFormatting parseChatColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        String c = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            int rgb = Integer.parseInt(c, 16);
            for (ChatFormatting fmt : ChatFormatting.values()) {
                if (fmt.isColor() && fmt.getColor() != null && fmt.getColor() == rgb) {
                    return fmt;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static TextColor parseTextColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        String c = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return TextColor.fromRgb(Integer.parseInt(c, 16));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ChatFormatting nearestChatColor(String hex) {
        TextColor tc = parseTextColor(hex);
        if (tc == null) return null;
        int r = (tc.getValue() >> 16) & 0xFF;
        int g = (tc.getValue() >> 8) & 0xFF;
        int b = tc.getValue() & 0xFF;
        ChatFormatting best = null;
        double bestDist = Double.MAX_VALUE;
        for (ChatFormatting fmt : ChatFormatting.values()) {
            if (!fmt.isColor() || fmt.getColor() == null) continue;
            int rgb = fmt.getColor();
            int fr = (rgb >> 16) & 0xFF;
            int fg = (rgb >> 8) & 0xFF;
            int fb = rgb & 0xFF;
            double dist = Math.pow(r - fr, 2) + Math.pow(g - fg, 2) + Math.pow(b - fb, 2);
            if (dist < bestDist) {
                bestDist = dist;
                best = fmt;
            }
        }
        return best;
    }

    /** 问卷绑定对应的问卷标题（用于同步给客户端显示按钮文本） */
    private static String bindingTitle(String questionnaireId) {
        if (questionnaireId == null || questionnaireId.isEmpty()) return "";
        try {
            Questionnaire q = QuestionnaireStorage.get(UUID.fromString(questionnaireId));
            return q != null ? q.getTitle() : "（问卷已删除）";
        } catch (Exception e) {
            return "";
        }
    }

    private static void sendMailToPlayers(ServerPlayer admin, SendMailPacket payload) {
        // 绑定问卷模式（3）：不直接发送，由玩家提交问卷时自动发送
        if (payload.sendMode() == 3) {
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c绑定问卷模式不会直接发送邮件"));
            return;
        }
        try {
            long sentAt = System.currentTimeMillis();
            long expiresAt = payload.expiresAt();
            int[] cards = payload.cards();
            int lotteryCount = payload.lotteryCount();

            List<ServerPlayer> targets = new ArrayList<>();
            switch (payload.sendMode()) {
                case 0 -> targets.addAll(admin.getServer().getPlayerList().getPlayers());
                case 1 -> {
                    for (String name : payload.playerNames()) {
                        ServerPlayer p = admin.getServer().getPlayerList().getPlayerByName(name);
                        if (p != null) targets.add(p);
                    }
                }
                case 2 -> {
                    Set<String> blacklist = new HashSet<>(payload.playerNames());
                    for (ServerPlayer p : admin.getServer().getPlayerList().getPlayers()) {
                        if (!blacklist.contains(p.getName().getString())) {
                            targets.add(p);
                        }
                    }
                }
            }
            int count = 0;
            // 保存发布记录，供管理端列表展示及后续上线玩家投递
            MailRecord record = new MailRecord(UUID.randomUUID());
            record.setTitle(payload.title());
            record.setContent(payload.content());
            record.setCards(cards);
            record.setLotteryCount(lotteryCount);
            record.setSendMode(payload.sendMode());
            record.setPlayerNames(payload.playerNames());
            record.setStartAt(payload.startAt());
            record.setEndAt(expiresAt);
            record.setSentAt(sentAt);

            for (ServerPlayer target : targets) {
                MailBridge.sendMail(target, admin.getName().getString(), payload.title(), payload.content(),
                    expiresAt, cards, lotteryCount);
                record.addDeliveredPlayer(target.getName().getString());
                count++;
            }
            MailStorage.save(record);

            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a邮件已发送给 " + count + " 位玩家"));
        } catch (Exception e) {
            NpcAiMod.LOGGER.error("Failed to send mail", e);
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c邮件发送失败，请检查列车 MOD 邮箱系统是否可用"));
        }
    }

    private static void deliverPendingMailToPlayer(ServerPlayer player) {
        String playerName = player.getName().getString();
        for (MailRecord record : MailStorage.loadAll()) {
            if (!record.isActive()) continue;
            if (!record.shouldSendTo(playerName)) continue;
            if (record.hasDeliveredPlayer(playerName)) continue;

            // 投递邮件（接入 habitrain_lottery 邮箱；身份卡+抽奖奖励在投递时立即发放）
            MailBridge.sendMail(player, "系统", record.getTitle(), record.getContent(),
                record.getEndAt(), record.getCards(), record.getLotteryCount());
            record.addDeliveredPlayer(playerName);
            MailStorage.save(record);
            LOGGER.info("Delivered pending mail '{}' to player {}", record.getTitle(), playerName);
        }
    }

    /** 确保小脑榜计分板存在（服务器启动时创建，无需等死亡事件） */
    private static void ensureCerebellumObjectives(MinecraftServer server) {
        try {
            var scoreboard = server.getScoreboard();
            if (scoreboard.getObjective("kgxnbang") == null) {
                scoreboard.addObjective("kgxnbang", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("小脑榜"),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
            }
            if (scoreboard.getObjective("kgxnbang_punish") == null) {
                scoreboard.addObjective("kgxnbang_punish", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("小脑惩罚次数"),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to ensure cerebellum objectives", e);
        }
    }

    /** 物品栈 → 注册ID字符串（空栈/空气 → ""） */
    private static String itemIdOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : "";
    }

    private static void sendContributionsTo(ServerPlayer player) {
        try {
            List<Contribution> all = ContributionStorage.loadAll();
            int remaining = ContributionStorage.getRemainingLikes(player.getUUID());
            List<String> likedIds = new ArrayList<>();
            for (Contribution c : all) {
                if (ContributionStorage.hasLiked(player.getUUID(), c.getId())) {
                    likedIds.add(c.getId().toString());
                }
            }
            ServerPlayNetworking.send(player, new SyncContributionsPacket(all, remaining, likedIds));
        } catch (Exception e) {
            LOGGER.error("Failed to sync contributions", e);
        }
    }

    /** 4种身份卡类型（顺序与基座mod进度背包展示一致：杀手/平民/独赢中立/杀手中立） */
    private static final ProgressionState.FactionCardType[] REWARD_CARD_TYPES = {
        ProgressionState.FactionCardType.KILLER,
        ProgressionState.FactionCardType.CIVILIAN,
        ProgressionState.FactionCardType.NEUTRAL,
        ProgressionState.FactionCardType.NEUTRAL_FOR_KILLER
    };

    /** 构建奖励摘要文本（全部为0返回空字符串） */
    public static String buildRewardSummary(int[] cards, int lottery) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4 && i < cards.length; i++) {
            if (cards[i] > 0) {
                sb.append(ContributionRewardSettings.CARD_LABELS[i]).append("×").append(cards[i]).append(" ");
            }
        }
        // 抽奖系统已断开（2026-08-16），不再发放抽奖次数，摘要也不写该文案
        return sb.toString().trim();
    }

    /** 发放奖励：身份卡（基座mod进度背包）。lottery 参数保留仅为兼容调用方，抽奖系统已断开，忽略。 */
    public static void grantReward(ServerPlayer player, int[] cards, int lottery) {
        try {
            for (int i = 0; i < 4 && i < cards.length; i++) {
                if (cards[i] > 0) {
                    ProgressionDataManager.addFactionCard(player, REWARD_CARD_TYPES[i], cards[i]);
                }
            }
            // 抽奖系统已断开（2026-08-16），不再发放抽奖次数
        } catch (Exception e) {
            LOGGER.error("Failed to grant contribution reward to {}", player.getName().getString(), e);
        }
    }

    /**
     * 自动结算所有已结束且未结算的投稿期（服务器启动时与每60秒检测调用）。
     * 每期时间一到自动结算：前三名获得奖励。
     */
    private static void autoSettleContributionPeriods(MinecraftServer server) {
        try {
            int current = Contribution.getCurrentPeriod();
            for (int p = 1; p < current; p++) {
                if (!ContributionRewardStorage.isSettled(p)) {
                    settleContributionPeriod(server, p);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Auto-settle contribution periods failed", e);
        }
    }

    /**
     * 结算某期投稿前三名奖励（自动触发，幂等）。
     * 按点赞数降序（同赞取投稿更早者在前）；
     * 奖励以永久有效邮箱（无结束时间）发送到玩家邮箱；在线直接发放，离线计入待领（上线补发）。
     */
    private static void settleContributionPeriod(MinecraftServer server, int period) {
        if (period >= Contribution.getCurrentPeriod()) return;
        if (!ContributionRewardStorage.markSettled(period)) return;
        List<Contribution> list = new ArrayList<>();
        for (Contribution c : ContributionStorage.loadAll()) {
            // 仅已审核通过的作品参与排名（未审核/已驳回的不参与）
            if (c.getPeriod() == period && c.isApproved()) list.add(c);
        }
        list.sort(java.util.Comparator.comparingInt(Contribution::getLikes).reversed()
            .thenComparingLong(Contribution::getCreatedAt));

        ContributionRewardSettings settings = ContributionRewardStorage.getSettings();
        String[] placeNames = {"一", "二", "三"};
        int granted = 0;
        for (int i = 0; i < 3 && i < list.size(); i++) {
            Contribution c = list.get(i);
            int[] cards = settings.getPlaceCards(i);
            int lottery = settings.getPlaceLottery(i);
            if (ContributionRewardSettings.isAllZero(cards, lottery)) continue;
            String rewardSummary = buildRewardSummary(cards, lottery);
            // 永久有效邮箱（endAt=0 无结束时间）：奖励通知直达玩家邮箱
            sendRewardMail(server, c.getAuthorName(), "投稿高点赞奖励！",
                "恭喜您投稿的作品成为本期点赞热度第" + placeNames[i] + "名，请点击下方领取专属奖励！"
                    + (rewardSummary.isEmpty() ? "" : "\n奖励：" + rewardSummary));
            ServerPlayer target = server.getPlayerList().getPlayer(c.getAuthorId());
            if (target != null) {
                grantReward(target, cards, lottery);
                target.sendSystemMessage(Component.literal(
                    "§6恭喜！你在第" + period + "期投稿中获第" + placeNames[i] + "名，奖励已发送至邮箱"));
            } else {
                ContributionRewardStorage.addPending(c.getAuthorId(), cards, lottery);
            }
            granted++;
        }
        LOGGER.info("第{}期投稿前三名已自动结算，共发放{}名", period, granted);
    }

    /**
     * 发送永久奖励邮件（endAt=0 = 无结束时间，长期有效）。
     * 白名单单人投递；在线玩家立即投递，离线玩家下次上线时由 deliverPendingMailToPlayer 自动投递。
     */
    public static void sendRewardMail(MinecraftServer server, String playerName, String title, String content) {
        try {
            long now = System.currentTimeMillis();
            MailRecord record = new MailRecord(UUID.randomUUID());
            record.setTitle(title);
            record.setContent(content);
            record.setSendMode(1); // 白名单：仅该玩家
            record.setPlayerNames(java.util.Collections.singletonList(playerName));
            record.setStartAt(now);
            record.setEndAt(0); // 0 = 永久有效（无结束时间）
            record.setSentAt(now);
            MailStorage.save(record);
            ServerPlayer online = server.getPlayerList().getPlayerByName(playerName);
            if (online != null) {
                deliverPendingMailToPlayer(online);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to send reward mail to {}", playerName, e);
        }
    }

    private static void sendNpcData(ServerPlayer player, CustomerServiceNpcEntity npc) {
        NpcData data = NpcDataManager.get(npc.getUUID());
        ServerPlayNetworking.send(player, new SyncNpcDataPacket(
            npc.getId(),
            data.getDisplayName(),
            data.getSkinName(),
            // 全局传送点：所有 NPC 共享互通
            TeleportPointStorage.getPoints()
        ));
    }


    /**
     * 构建小脑榜：以计分板为权威数据源（kgxnbang=当前次数, kgxnbang_punish=已受惩罚次数）。
     * 管理员可用 /scoreboard players set 指令实时修改，此处读取即实时反映。
     */
    private static List<SyncCerebellumSettingsPacket.CerebellumEntry> buildCerebellumLeaderboard(MinecraftServer server) {
        try {
            var scoreboard = server.getScoreboard();
            var currentObj = scoreboard.getObjective("kgxnbang");
            var punishObj = scoreboard.getObjective("kgxnbang_punish");

            // 玩家名 → UUID（在线优先，其次档案缓存）
            Map<String, UUID> uuidByName = new HashMap<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                uuidByName.put(player.getName().getString(), player.getUUID());
            }

            // 从计分板读取所有分数条目（holder 名即玩家名）
            Map<String, Integer> currentScores = new HashMap<>();
            Map<String, Integer> punishScores = new HashMap<>();
            for (var holder : scoreboard.getTrackedPlayers()) {
                String holderName = holder.getScoreboardName();
                if (holderName == null || holderName.isEmpty()) continue;
                try {
                    if (currentObj != null) {
                        int s = scoreboard.getOrCreatePlayerScore(holder, currentObj).get();
                        if (s != 0) currentScores.put(holderName, s);
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (punishObj != null) {
                        int s = scoreboard.getOrCreatePlayerScore(holder, punishObj).get();
                        if (s != 0) punishScores.put(holderName, s);
                    }
                } catch (Exception ignored) {
                }
            }

            // 合并所有玩家名
            Set<String> allNames = new HashSet<>();
            allNames.addAll(currentScores.keySet());
            allNames.addAll(punishScores.keySet());

            List<SyncCerebellumSettingsPacket.CerebellumEntry> entries = new ArrayList<>();
            for (String name : allNames) {
                UUID uuid = uuidByName.get(name);
                if (uuid == null) {
                    try {
                        var profileOpt = server.getProfileCache().get(name);
                        if (profileOpt.isPresent()) {
                            uuid = profileOpt.get().getId();
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (uuid == null) {
                    uuid = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                entries.add(new SyncCerebellumSettingsPacket.CerebellumEntry(
                    uuid, name,
                    currentScores.getOrDefault(name, 0),
                    punishScores.getOrDefault(name, 0),
                    CerebellumStorage.getPendingCount(uuid)));
            }
            entries.sort((a, b) -> {
                int cmp = Integer.compare(b.punishmentCount(), a.punishmentCount());
                if (cmp != 0) return cmp;
                return Integer.compare(b.currentCount(), a.currentCount());
            });
            return entries;
        } catch (Exception e) {
            LOGGER.error("Failed to build cerebellum leaderboard", e);
            return new ArrayList<>();
        }
    }

    public static void openAdminScreen(ServerPlayer player, CustomerServiceNpcEntity npc) {
        NpcData data = NpcDataManager.get(npc.getUUID());
        ServerPlayNetworking.send(player, new OpenNpcAdminPacket(
            npc.getId(),
            data.getDisplayName(),
            data.getSkinName(),
            npc.getX(), npc.getY(), npc.getZ(),
            data.getFollowMode().ordinal(),
            data.getViewMode().ordinal(),
            data.getScale(),
            data.getHeldItem(),
            // 全局传送点：所有 NPC 共享互通
            TeleportPointStorage.getPoints(),
            data.getRoamX(), data.getRoamY(), data.getRoamZ(), data.getRoamRadius()
        ));
        ServerPlayNetworking.send(player, new SyncCompensationRulesPacket(CompensationStorage.getAll()));
        ServerPlayNetworking.send(player, new SyncCerebellumSettingsPacket(
            CerebellumStorage.getSettings(), buildCerebellumLeaderboard(player.getServer())));
    }
}
