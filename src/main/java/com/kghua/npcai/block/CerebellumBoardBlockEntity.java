package com.kghua.npcai.block;

import com.kghua.npcai.NpcAiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CerebellumBoardBlockEntity extends BlockEntity {
    public CerebellumBoardBlockEntity(BlockPos pos, BlockState state) {
        super(NpcAiMod.CEREBELLUM_BOARD_BE, pos, state);
    }
}
