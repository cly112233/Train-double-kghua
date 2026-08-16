package com.kghua.npcai.command;

import com.kghua.npcai.entity.CustomerServiceNpcEntity;
import com.kghua.npcai.player.PlayerPendingTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class TallaiCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tallai")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("pos", Vec3Argument.vec3())
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
                        }))))
            .then(Commands.literal("requirefeedback")
                .then(Commands.argument("playerName", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "playerName");
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
                        if (target == null) {
                            source.sendFailure(Component.literal("找不到玩家：" + name));
                            return 0;
                        }
                        PlayerPendingTracker.markFeedbackPending(target.getUUID());
                        source.sendSuccess(() -> Component.literal("已标记 " + name + " 需要填写反馈/问卷"), true);
                        return 1;
                    })))
            .then(Commands.literal("clearfeedback")
                .then(Commands.argument("playerName", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "playerName");
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
                        if (target == null) {
                            source.sendFailure(Component.literal("找不到玩家：" + name));
                            return 0;
                        }
                        PlayerPendingTracker.clearFeedbackPending(target.getUUID());
                        source.sendSuccess(() -> Component.literal("已清除 " + name + " 的反馈/问卷标记"), true);
                        return 1;
                    })))
        );
    }
}
