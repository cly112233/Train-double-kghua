package com.kghua.npcai.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * 小脑榜方块。
 * 固定显示尺寸（不拼接）：
 * - 3×4 / 5×7 / 7×9 / 9×9，显示行数由方块高度决定（标题+表头+数据行占满整块高度）
 * 小脑榜从方块（顶部中间）开始，标题在最顶端往下顺延。
 * 方块本体为屏障方块样式（世界内不可见，只显示文字屏幕），仍可挖掘拆除。
 */
public class CerebellumBoardBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public final int width;
    public final int height;

    public CerebellumBoardBlock(Properties properties, int width, int height) {
        super(properties);
        this.width = width;
        this.height = height;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new CerebellumBoardBlock(p, 3, 4));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CerebellumBoardBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // 屏障方块样式：方块本体不可见，只显示文字屏幕
    }
}
