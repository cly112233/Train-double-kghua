package xiao.hua.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

public class BedSleepingPlayerHelper {
    public static Optional<Player> findSleepingPlayerOnBed(Level world, BlockHitResult blockHitResult) {
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState state = world.getBlockState(blockPos);

        if (!(state.getBlock() instanceof BedBlock)) {
            return Optional.empty();
        }

        BedPart part = state.getValue(BedBlock.PART);
        Direction facing = state.getValue(BedBlock.FACING);
        BlockPos headPos = (part == BedPart.HEAD) ? blockPos : blockPos.relative(facing);

        for (Player player : world.players()) {
            if (!player.isSleeping()) {
                continue;
            }
            Optional<BlockPos> sleepingPosOpt = player.getSleepingPos();
            if (sleepingPosOpt.isEmpty()) {
                continue;
            }
            BlockPos sleepingPos = sleepingPosOpt.get();
            if (sleepingPos.equals(headPos) || sleepingPos.equals(blockPos)) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }
}