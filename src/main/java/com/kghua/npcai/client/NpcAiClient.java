package com.kghua.npcai.client;

import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.client.config.NpcAiClientConfig;
import com.kghua.npcai.client.renderer.CerebellumBoardRenderer;
import com.kghua.npcai.client.screen.*;
import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.data.MailRecord;
import com.kghua.npcai.data.NpcData;
import com.kghua.npcai.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class NpcAiClient implements ClientModInitializer {
    // 地图组成员按键：默认X键，打开传送点分类弹窗
    private static final net.minecraft.client.KeyMapping OPEN_TELEPORT_KEY = new net.minecraft.client.KeyMapping(
        "key.western_cowboy.open_teleport",
        org.lwjgl.glfw.GLFW.GLFW_KEY_X,
        "key.categories.western_cowboy");

    @Override
    public void onInitializeClient() {
        NpcAiMod.LOGGER.info("NPC AI client initializing...");

        EntityRendererRegistry.register(NpcAiMod.CUSTOMER_SERVICE_NPC, CustomerServiceNpcRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(NpcAiMod.CEREBELLUM_BOARD_BE, CerebellumBoardRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(OpenNpcChatPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientCache.setNpcAdmin(payload.isNpcAdmin());
                ClientCache.setOp(payload.isOp());
                context.client().setScreen(new CustomerChatScreen(payload.npcName(), payload.entityId(),
                    payload.playerUuid(), payload.mapGroupMember(),
                    payload.unreadMailCount(), payload.unfilledQuestionnaireCount()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenNpcAdminPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                NpcAdminScreen screen = new NpcAdminScreen(payload.entityId());
                screen.setData(payload.displayName(), payload.skinName(), payload.teleportPoints());
                screen.setCoords(payload.x(), payload.y(), payload.z());
                NpcData.FollowMode mode;
                try {
                    mode = NpcData.FollowMode.values()[payload.followMode()];
                } catch (Exception e) {
                    mode = NpcData.FollowMode.FIXED;
                }
                screen.setFollowMode(mode);
                NpcData.ViewMode viewMode;
                try {
                    viewMode = NpcData.ViewMode.values()[payload.viewMode()];
                } catch (Exception e) {
                    viewMode = NpcData.ViewMode.RANDOM;
                }
                screen.setViewMode(viewMode);
                screen.setScale(payload.scale());
                screen.setHeldItem(payload.heldItem());
                screen.setRoam(payload.roamX(), payload.roamY(), payload.roamZ(), payload.roamRadius());
                context.client().setScreen(screen);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncNpcDataPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof TeleportScreen screen && screen.getEntityId() == payload.entityId()) {
                    screen.setTeleportPoints(payload.teleportPoints());
                } else if (context.client().screen instanceof MapTeleportEditScreen editScreen) {
                    editScreen.setTeleportPoints(payload.teleportPoints());
                } else if (context.client().screen instanceof NpcAdminScreen adminScreen && adminScreen.getEntityId() == payload.entityId()) {
                    adminScreen.setData(payload.displayName(), payload.skinName(), payload.teleportPoints());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncQuestionnairesPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setQuestionnaires(payload.questionnaires());
                } else if (context.client().screen instanceof MailBindScreen screen) {
                    screen.setQuestionnaires(payload.questionnaires());
                } else if (context.client().screen instanceof PlayerQuestionnaireScreen screen) {
                    screen.setQuestionnaires(payload.questionnaires());
                } else if (context.client().screen instanceof QuestionnaireDetailScreen screen) {
                    screen.updateQuestionnaire(payload.questionnaires());
                } else if (context.client().screen instanceof QuestionnaireResultScreen screen) {
                    screen.updateQuestionnaire(payload.questionnaires());
                }
            });
        });

        // 问卷绑定状态同步（管理端邮箱设置按钮显示当前绑定的问卷）
        ClientPlayNetworking.registerGlobalReceiver(SyncMailBindingPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientCache.setMailBinding(payload.questionnaireId(), payload.questionnaireTitle());
                if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setMailBinding(payload.questionnaireId(), payload.questionnaireTitle());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPlayerListPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setPlayerList(payload.players());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncFeedbackPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setFeedback(payload.entries());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncMailsPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setMails(payload.mails());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncCompensationRulesPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientCache.setCompensationRules(payload.rules());
                if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setCompensationRules(payload.rules());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncCerebellumSettingsPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientCache.setCerebellumSettings(payload.settings());
                ClientCache.setCerebellumLeaderboard(payload.leaderboard());
                if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setCerebellumSettings(payload.settings());
                    screen.setCerebellumLeaderboard(payload.leaderboard());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncContributionsPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof ContributionBrowseScreen screen) {
                    screen.setContributions(payload.contributions());
                    screen.setRemainingLikes(payload.remainingLikes());
                    screen.setLikedIds(payload.likedIds());
                } else if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setContributions(payload.contributions());
                } else if (context.client().screen instanceof ContributionCategoryScreen screen) {
                    screen.setContributions(payload.contributions());
                } else if (context.client().screen instanceof ContributionSubmitScreen screen) {
                    screen.setContributions(payload.contributions());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncContributionRewardsPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientCache.setContributionRewards(payload.settings());
                if (context.client().screen instanceof NpcAdminScreen screen) {
                    screen.setContributionRewards(payload.settings());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncAiConfigPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientCache.setServerDefaultAiApiUrl(payload.defaultAiApiUrl());
                ClientCache.setMapGroupMember(payload.mapGroupMember());
                ClientCache.setNpcAdmin(payload.npcAdmin());
                ClientCache.setOp(payload.isOp());
            });
        });

        // 导出结果：保存到客户端本地的游戏文件夹（npctalltome/<子目录>/）
        // AI对话指令执行结果：对话界面打开时显示在界面内，否则落到游戏聊天
        ClientPlayNetworking.registerGlobalReceiver(ExecuteAiCommandResultPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (CustomerChatScreen.showCommandResult(payload.message())) {
                    return;
                }
                net.minecraft.client.player.LocalPlayer player = context.client().player;
                if (player != null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(payload.message()));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ExportResultPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getGameDir().resolve("npctalltome").resolve(payload.subDir());
                    java.nio.file.Files.createDirectories(dir);
                    java.nio.file.Path file = dir.resolve(payload.fileName());
                    java.nio.file.Files.writeString(file, payload.content(), java.nio.charset.StandardCharsets.UTF_8);
                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("§a已保存到客户端：" + file));
                    }
                } catch (Exception e) {
                    NpcAiMod.LOGGER.error("Failed to save export locally", e);
                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("§c导出保存失败"));
                    }
                }
            });
        });

        // 地图组成员按键（默认X）：请求服务端确认后打开传送点分类弹窗
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(OPEN_TELEPORT_KEY);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_TELEPORT_KEY.consumeClick()) {
                if (client.player == null) continue;
                // 对局进行中禁止传送（NPC管理员豁免）
                if (ClientCache.isGameInProgress() && !ClientCache.isNpcAdmin()) {
                    client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c游戏中无法使用传送"));
                    continue;
                }
                // 向服务端请求：确认地图组成员身份 + 查找NPC（不依赖本地缓存，管理员在线设置成员后也立即生效）
                ClientPlayNetworking.send(new com.kghua.npcai.network.RequestMapTeleportPacket());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(com.kghua.npcai.network.OpenMapTeleportPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (!payload.isMember()) {
                    // 旧服务端或异常路径：明确提示而不是静默失败
                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c你不是地图组成员，无法使用传送面板（请管理员在管理界面添加）"));
                    }
                    return;
                }
                if (payload.npcEntityId() < 0) {
                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c服务器还没有配置NPC，请先在管理界面添加NPC"));
                    }
                    return;
                }
                context.client().setScreen(new com.kghua.npcai.client.screen.TeleportCategoryScreen(
                    payload.npcEntityId(), payload.npcName()));
            });
        });

        // 预加载客户端配置，确保缓存默认值并创建默认配置文件
        NpcAiClientConfig.getAiApiUrl();

        NpcAiMod.LOGGER.info("NPC AI client ready!");
    }
}
