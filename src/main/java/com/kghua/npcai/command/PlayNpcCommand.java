package com.kghua.npcai.command;

import com.kghua.npcai.data.Contribution;
import com.kghua.npcai.data.ContributionStorage;
import com.kghua.npcai.data.PlayerMapGroupStorage;
import com.kghua.npcai.data.TeleportPoint;
import com.kghua.npcai.data.TeleportPointStorage;
import com.kghua.npcai.entity.CustomerServiceNpcEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * /playnpc <坐标> <玩家名> <npc显示名称>
 * 早期设计指令，功能与 /tallai 创建 NPC 保持一致。
 * /playnpc ditu <creative/adventure>
 * 仅地图组成员可用，用于切换创造/冒险模式。
 */
public class PlayNpcCommand {
    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("creative");
        builder.suggest("adventure");
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("playnpc")
            .then(Commands.literal("exportcontribution")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("contributionId", StringArgumentType.string())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        try {
                            UUID uuid = UUID.fromString(StringArgumentType.getString(ctx, "contributionId"));
                            Contribution c = ContributionStorage.get(uuid);
                            if (c == null) {
                                source.sendFailure(Component.literal("§c投稿不存在"));
                                return 0;
                            }
                            String[] result = ContributionStorage.exportToMarkdownText(c);
                            if (result != null) {
                                // 导出内容发回执行者客户端，由客户端保存到本地游戏文件夹
                                if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                                        new com.kghua.npcai.network.ExportResultPacket("juesetougao", result[0], result[1]));
                                    source.sendSuccess(() -> Component.literal("§a投稿已导出：" + result[0]), true);
                                } else {
                                    source.sendFailure(Component.literal("§c导出只能在游戏内执行"));
                                }
                            } else {
                                source.sendFailure(Component.literal("§c投稿导出失败"));
                            }
                            return 1;
                        } catch (IllegalArgumentException e) {
                            source.sendFailure(Component.literal("§c投稿ID格式错误"));
                            return 0;
                        }
                    })))
            .then(Commands.literal("tp")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("category", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                for (String c : TeleportPoint.CATEGORIES) {
                                    builder.suggest("\"" + c + "\""); // 中文类型用引号包裹
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "name");
                                Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                                String category = StringArgumentType.getString(ctx, "category");
                                CommandSourceStack source = ctx.getSource();

                                if (!(source.getEntity() instanceof ServerPlayer player)) {
                                    source.sendFailure(Component.literal("该命令只能由玩家执行"));
                                    return 0;
                                }

                                if (CustomerServiceNpcEntity.countAnywhere(player.getServer()) <= 0) {
                                    source.sendFailure(Component.literal("§c尚未创建NPC客服，请先用 /tallai 创建一个"));
                                    return 0;
                                }

                                // 校验分类合法，非法归入"其他"
                                String cat = "其他";
                                for (String c : TeleportPoint.CATEGORIES) {
                                    if (c.equals(category)) {
                                        cat = c;
                                        break;
                                    }
                                }
                                final String finalCat = cat;

                                // 全局传送点存储：所有 NPC 共享互通（同名替换）
                                TeleportPointStorage.addPoint(new TeleportPoint(
                                    name, pos.x, pos.y, pos.z, System.currentTimeMillis(), finalCat
                                ));

                                source.sendSuccess(
                                    () -> Component.literal("§a已添加传送点：" + name + "（" + formatCoord(pos.x) + ", " + formatCoord(pos.y) + ", " + formatCoord(pos.z) + "，分类：" + finalCat + "）"),
                                    true
                                );
                                return 1;
                            })))))
            .then(Commands.literal("list")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    java.util.List<CustomerServiceNpcEntity> npcs = findNpcs(source.getServer());
                    if (npcs.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§7当前没有已加载的NPC客服"), false);
                        return 1;
                    }
                    source.sendSuccess(() -> Component.literal("§e当前存在 " + npcs.size() + " 个NPC客服："), false);
                    for (CustomerServiceNpcEntity npc : npcs) {
                        String dim = npc.level().dimension().location().toString();
                        source.sendSuccess(() -> Component.literal("§a" + npc.getNpcName()
                            + "§f @ " + dim + " (" + formatCoord(npc.getX()) + ", " + formatCoord(npc.getY()) + ", " + formatCoord(npc.getZ()) + ")"), false);
                    }
                    return 1;
                }))
            .then(Commands.literal("ditu")
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests(MODE_SUGGESTIONS)
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                            ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行"));
                            return 0;
                        }
                        if (!PlayerMapGroupStorage.isMember(player.getUUID())) {
                            ctx.getSource().sendFailure(Component.literal("只有地图组成员可以使用该指令"));
                            return 0;
                        }
                        String mode = StringArgumentType.getString(ctx, "mode");
                        if ("creative".equalsIgnoreCase(mode)) {
                            player.setGameMode(GameType.CREATIVE);
                            ctx.getSource().sendSuccess(() -> Component.literal("已切换为创造模式"), false);
                            return 1;
                        } else if ("adventure".equalsIgnoreCase(mode)) {
                            player.setGameMode(GameType.ADVENTURE);
                            ctx.getSource().sendSuccess(() -> Component.literal("已切换为冒险模式"), false);
                            return 1;
                        } else {
                            ctx.getSource().sendFailure(Component.literal("模式只能是 creative 或 adventure"));
                            return 0;
                        }
                    })))
            .then(Commands.argument("pos", Vec3Argument.vec3())
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("skinName", StringArgumentType.word())
                    .then(Commands.argument("displayName", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                            String skinName = StringArgumentType.getString(ctx, "skinName");
                            String displayName = StringArgumentType.getString(ctx, "displayName");
                            CommandSourceStack source = ctx.getSource();

                            if (source.getEntity() instanceof ServerPlayer player) {
                                int count = CustomerServiceNpcEntity.countAnywhere(player.getServer());
                                if (count >= 2) {
                                    source.sendFailure(Component.literal("§c最多只能创建两个NPC客服"));
                                    return 0;
                                }
                                // 第一个NPC复用单例UUID（兼容旧数据），第二个生成新UUID
                                java.util.UUID npcUuid = count == 0 ? com.kghua.npcai.data.NpcDataManager.SINGLETON_NPC_UUID : java.util.UUID.randomUUID();
                                CustomerServiceNpcEntity npc = new CustomerServiceNpcEntity(
                                    npcUuid, player.serverLevel(), pos.x, pos.y, pos.z, skinName, displayName
                                );
                                player.serverLevel().addFreshEntity(npc);
                                source.sendSuccess(
                                    () -> Component.literal("已创建 NPC 客服：" + displayName + "（皮肤：" + skinName + "）"),
                                    true
                                );
                                return 1;
                            }

                            source.sendFailure(Component.literal("该命令只能由玩家执行"));
                            return 0;
                        })))));

        // /tpnpc <NPC名称>：把执行者传送到指定NPC所在位置（名称自动补全）
        dispatcher.register(Commands.literal("tpnpc")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("npcName", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    for (CustomerServiceNpcEntity npc : findNpcs(ctx.getSource().getServer())) {
                        builder.suggest(npc.getNpcName());
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    String npcName = StringArgumentType.getString(ctx, "npcName");
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal("该命令只能由玩家执行"));
                        return 0;
                    }
                    for (CustomerServiceNpcEntity npc : findNpcs(player.getServer())) {
                        if (npcName.equals(npc.getNpcName())) {
                            player.teleportTo((net.minecraft.server.level.ServerLevel) npc.level(),
                                npc.getX(), npc.getY(), npc.getZ(), player.getYRot(), player.getXRot());
                            source.sendSuccess(() -> Component.literal("§a已传送到NPC：" + npc.getNpcName()
                                + "（" + formatCoord(npc.getX()) + ", " + formatCoord(npc.getY()) + ", " + formatCoord(npc.getZ()) + "）"), true);
                            return 1;
                        }
                    }
                    source.sendFailure(Component.literal("§c未找到名为 " + npcName + " 的NPC"));
                    return 0;
                })));
    }

    /** 查找服务器所有维度中已加载的NPC实体 */
    private static java.util.List<CustomerServiceNpcEntity> findNpcs(net.minecraft.server.MinecraftServer server) {
        java.util.List<CustomerServiceNpcEntity> list = new java.util.ArrayList<>();
        if (server == null) return list;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            list.addAll(level.getEntities(com.kghua.npcai.NpcAiMod.CUSTOMER_SERVICE_NPC, e -> true));
        }
        return list;
    }

    private static String formatCoord(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }
}
