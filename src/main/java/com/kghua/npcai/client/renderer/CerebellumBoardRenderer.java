package com.kghua.npcai.client.renderer;

import com.kghua.npcai.block.CerebellumBoardBlock;
import com.kghua.npcai.block.CerebellumBoardBlockEntity;
import com.kghua.npcai.client.ClientCache;
import com.kghua.npcai.network.SyncCerebellumSettingsPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * 小脑榜方块渲染器（固定尺寸版）。
 * - 方块自带尺寸：3×4 / 5×7 / 7×9 / 9×9，无需拼接检测
 * - 字号由方块宽度约束决定（保持可读大小），数据行数按此字号从顶部向下排满整块方块高度
 * - 显示区域整体平移：向方块方向移动一格 + 向下移动一格（标题位于最顶格方块，整块区域一起移动，行数不变不截断）
 * - 标题在区域最顶端，往下表头+数据行，水平居中左右对称
 * - 文字面到方块面的距离、显示高度均保持原设定不变
 */
public class CerebellumBoardRenderer implements BlockEntityRenderer<CerebellumBoardBlockEntity> {

    private static final float LINE_H = 10f;
    private static final float TITLE_H = 10f;
    // 文字面到方块面的间距常量（原设定不变）：0.5（格中心）+ 0.0575（格间隙）
    private static final float OUT_OFFSET = -0.5575f;

    public CerebellumBoardRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CerebellumBoardBlockEntity be, float tickDelta, PoseStack poseStack,
                       MultiBufferSource bufferSource, int light, int overlay) {
        List<SyncCerebellumSettingsPacket.CerebellumEntry> data = ClientCache.getCerebellumLeaderboard();
        if (data.isEmpty()) return;

        BlockState state = be.getBlockState();
        Block block = state.getBlock();
        if (!(block instanceof CerebellumBoardBlock boardBlock)) return;
        Direction facing = state.getValue(CerebellumBoardBlock.FACING);

        int width = boardBlock.width;
        int height = boardBlock.height;

        Font font = Minecraft.getInstance().font;
        int requiredDeaths = ClientCache.getCerebellumSettings().getRequiredDeaths();

        // 列宽估算：取前 30 行数据（避免大榜每帧计算全部行）
        List<SyncCerebellumSettingsPacket.CerebellumEntry> colSample = data.size() > 30
            ? data.subList(0, 30) : data;
        int[] colW = new int[4];
        colW[0] = font.width("序号");
        colW[1] = font.width("玩家");
        colW[2] = font.width("小脑次数(" + requiredDeaths + "次)");
        colW[3] = font.width("惩罚次数");
        for (SyncCerebellumSettingsPacket.CerebellumEntry e : colSample) {
            int w1 = font.width(String.valueOf(e.currentCount()));
            if (w1 > colW[0]) colW[0] = w1;
            int w2 = font.width(e.playerName());
            if (w2 > colW[1]) colW[1] = w2;
            int w3 = font.width(String.valueOf(e.punishmentCount()));
            if (w3 > colW[2]) colW[2] = w3;
            int w4 = font.width(String.valueOf(e.pendingCount()));
            if (w4 > colW[3]) colW[3] = w4;
        }
        int pad = 8;
        float totalW = colW[0] + colW[1] + colW[2] + colW[3] + pad * 5;

        // 字号由宽度约束决定（保持当前大小，不随高度压缩）
        float scale = width / totalW;

        // 数据行数：按此字号（行高 = scale×LINE_H）从顶部向下排满整块方块高度
        // 总像素高度 = 标题上移5 + 标题10 + 间隔2 + 行数×10 → 世界格 = scale × (17 + totalLines×10) ≤ height
        int totalLines = Math.max(3, (int) Math.floor((height / scale - 17) / LINE_H));
        int dataRows = totalLines - 2;

        List<SyncCerebellumSettingsPacket.CerebellumEntry> top = data.size() > dataRows
            ? data.subList(0, dataRows) : data;

        // 表格数据
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"序号", "玩家", "小脑次数(" + requiredDeaths + "次)", "惩罚次数"});
        for (int i = 0; i < top.size(); i++) {
            SyncCerebellumSettingsPacket.CerebellumEntry e = top.get(i);
            rows.add(new String[]{String.valueOf(i + 1), e.playerName(), String.valueOf(e.currentCount()), String.valueOf(e.punishmentCount())});
        }

        poseStack.pushPose();
        // 主方块中心
        poseStack.translate(0.5, 0.5, 0.5);
        // 方向保持当前设置（不动）
        poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(facing.toYRot())));
        // 显示区域整体平移：向方块方向移动一格（-1.0 - OUT_OFFSET = -0.4425，
        // 文字面位于方块背面外一格，距离设定不变），同时向下移动一格（-1.0），
        // 标题位于最顶格方块顶部，整块区域一起移动、行数不变不截断
        poseStack.translate(0, -1.0, -1.0 - OUT_OFFSET);
        // 等比缩放
        poseStack.scale(scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();

        // 区域顶部 = 主方块顶部（世界 +0.5 格）→ 屏幕像素 = -0.5/scale
        float topPx = -0.5f / scale;

        // 标题：区域最顶端下移3像素，水平居中（金色，大一号）
        String title = "小脑榜";
        float titleY = topPx + 5;
        float titleScale = 1.15f; // 标题调大一号
        Matrix4f titleMatrix = new Matrix4f(matrix)
            .translate(0, titleY + TITLE_H / 2f, 0)
            .scale(titleScale)
            .translate(0, -(titleY + TITLE_H / 2f), 0);
        font.drawInBatch(Component.literal(title), -font.width(title) / 2.0f, titleY, 0x21000000, false, titleMatrix,
            bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(Component.literal(title), -font.width(title) / 2.0f, titleY, 0xFFFFCC00, false, titleMatrix,
            bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

        // 表头 + 数据行（每列居中，左右最边边对称）
        float startY = titleY + TITLE_H + 2;
        // 表头行字体小一号（序号/玩家/小脑次数/惩罚次数行）
        float headerScale = 0.85f;
        float headerCy = startY + LINE_H / 2f; // y 缩放中心
        Matrix4f headerMatrix = new Matrix4f(matrix)
            .translate(0, headerCy, 0)
            .scale(headerScale)
            .translate(0, -headerCy, 0);
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            int color = r == 0 ? 0xFFFFFF00 : 0xFFFFFFFF;
            boolean isHeader = r == 0;
            float x = -totalW / 2.0f;
            float y = startY + r * LINE_H;
            for (int c = 0; c < 4; c++) {
                String cell = row[c];
                int cellW = font.width(cell);
                float cellX = x + (colW[c] - cellW) / 2.0f + pad;
                if (isHeader) {
                    // 表头在缩放矩阵空间绘制：反向补偿缩放，保证列中心与数据行完全对齐
                    float compX = cellX / headerScale;                       // x 缩放中心为 0
                    float compY = (y - headerCy) / headerScale + headerCy;   // y 缩放中心为 headerCy
                    font.drawInBatch(Component.literal(cell), compX, compY, 0x21000000, false, headerMatrix,
                        bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
                    font.drawInBatch(Component.literal(cell), compX, compY, color, false, headerMatrix,
                        bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                } else {
                    font.drawInBatch(Component.literal(cell), cellX, y, 0x21000000, false, matrix, bufferSource,
                        Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
                    font.drawInBatch(Component.literal(cell), cellX, y, color, false, matrix, bufferSource,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                }
                x += colW[c] + pad;
            }
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CerebellumBoardBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
