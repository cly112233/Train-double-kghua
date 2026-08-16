package com.cowboymod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 西部牛仔决斗场地生成器。
 * 每次决斗开始前在专属高空坐标重建场地，避免依赖地图原有建筑或危险区。
 * 默认中心 (0, 200, 20000)，与塔罗会议类似，位于远离主世界的位置。
 *
 * 支持两种模式：
 * 1. 自动生成（默认）：铺设平滑石头地板 + Barrier 边界墙 + 光源。
 * 2. 自定义结构模板：读取资源文件 data/western_cowboy/structures/duel_arena.nbt 并放置，
 *    适合把你在游戏里亲手造的决斗场地搬进 mod。
 */
public class DuelArenaBuilder {

    private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState WALL = Blocks.BARRIER.defaultBlockState();
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final int FLAGS = 3; // block update + send to clients

    /** 已生成的当前场地边界与出生点 */
    public static class ArenaInfo {
        public final Vec3 spawnA;
        public final Vec3 spawnB;
        public final float spawnAYaw;
        public final float spawnBYaw;
        public final AABB bounds;

        public ArenaInfo(Vec3 spawnA, Vec3 spawnB, float spawnAYaw, float spawnBYaw, AABB bounds) {
            this.spawnA = spawnA;
            this.spawnB = spawnB;
            this.spawnAYaw = spawnAYaw;
            this.spawnBYaw = spawnBYaw;
            this.bounds = bounds;
        }
    }

    /**
     * 在指定世界重建决斗场地，并返回场地信息。
     * 优先使用自定义结构模板；只要模板存在且能加载，就一定使用你的建筑。
     */
    public static ArenaInfo build(ServerLevel level) {
        Optional<ArenaInfo> structure = buildFromStructure(level);
        if (structure.isPresent()) return structure.get();

        if (CowboyConfig.useStructureTemplate) {
            CowboyMod.LOGGER.warn("Structure template '{}' not found, falling back to generated arena",
                    CowboyConfig.structureTemplateId);
        } else {
            CowboyMod.LOGGER.info("Structure template not found and useStructureTemplate=false, using generated arena");
        }
        return buildGenerated(level);
    }

    /**
     * 直接从 mod jar 的 classpath 读取 NBT 结构文件，绕过 StructureTemplateManager
     * 在部分环境下无法加载 mod 资源的问题。
     */
    private static Optional<StructureTemplate> loadTemplateFromClasspath(ServerLevel level, ResourceLocation id) {
        String path = "/data/" + id.getNamespace() + "/structures/" + id.getPath() + ".nbt";
        try (InputStream in = DuelArenaBuilder.class.getResourceAsStream(path)) {
            if (in == null) {
                return Optional.empty();
            }
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            StructureTemplate template = new StructureTemplate();
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
            return Optional.of(template);
        } catch (Exception e) {
            CowboyMod.LOGGER.warn("Failed to load structure {} from classpath: {}", id, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 模式一：读取并放置自定义结构模板。支持单块与多块拼接。
     */
    private static Optional<ArenaInfo> buildFromStructure(ServerLevel level) {
        int cx = CowboyConfig.arenaCenterX;
        int cy = CowboyConfig.arenaCenterY;
        int cz = CowboyConfig.arenaCenterZ;
        BlockPos origin = new BlockPos(cx, cy, cz);

        if (!CowboyConfig.structureTemplateParts.isEmpty()) {
            return buildFromMultiPartStructure(level, origin);
        }

        ResourceLocation id;
        try {
            id = ResourceLocation.parse(CowboyConfig.structureTemplateId);
        } catch (Exception e) {
            CowboyMod.LOGGER.warn("Invalid structure template id: {}", CowboyConfig.structureTemplateId);
            return Optional.empty();
        }

        Optional<StructureTemplate> templateOpt = loadTemplateFromClasspath(level, id);
        if (templateOpt.isEmpty()) {
            return Optional.empty();
        }
        StructureTemplate template = templateOpt.get();
        Vec3i size = template.getSize();

        clearVolume(level, cx - 5, cy - 5, cz - 5,
                cx + size.getX() + 5, cy + size.getY() + 20, cz + size.getZ() + 5);

        StructurePlaceSettings settings = new StructurePlaceSettings();
        template.placeInWorld(level, origin, origin, settings, RandomSource.create(), FLAGS);

        return Optional.of(finalizeStructureArena(level, origin, size, cx, cy, cz));
    }

    /**
     * 多块结构模板拼接：适合超过 48x48x48 上限的大型场地。
     */
    private static Optional<ArenaInfo> buildFromMultiPartStructure(ServerLevel level, BlockPos origin) {
        int cx = origin.getX();
        int cy = origin.getY();
        int cz = origin.getZ();

        List<PartOffset> parts = new ArrayList<>();
        for (String entry : CowboyConfig.structureTemplateParts) {
            PartOffset part = parsePartOffset(entry);
            if (part == null) {
                CowboyMod.LOGGER.warn("Invalid structure template part entry: {}", entry);
                return Optional.empty();
            }
            ResourceLocation id = ResourceLocation.parse(CowboyConfig.structureTemplateId + "_" + (parts.size() + 1));
            Optional<StructureTemplate> opt = loadTemplateFromClasspath(level, id);
            if (opt.isEmpty()) {
                CowboyMod.LOGGER.warn("Structure template part '{}' not found", id);
                return Optional.empty();
            }
            part.template = opt.get();
            part.id = id;
            parts.add(part);
        }

        // 计算整体占用范围
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (PartOffset part : parts) {
            Vec3i size = part.template.getSize();
            minX = Math.min(minX, part.x);
            minY = Math.min(minY, part.y);
            minZ = Math.min(minZ, part.z);
            maxX = Math.max(maxX, part.x + size.getX());
            maxY = Math.max(maxY, part.y + size.getY());
            maxZ = Math.max(maxZ, part.z + size.getZ());
        }

        // 清空整体区域（参考 TarotAssemblySceneBuilder.clear，留足边距防止残留列车方块）
        clearVolume(level, cx + minX - 5, cy + minY - 5, cz + minZ - 5,
                cx + maxX + 5, cy + maxY + 20, cz + maxZ + 5);

        // 逐个放置
        StructurePlaceSettings settings = new StructurePlaceSettings();
        for (PartOffset part : parts) {
            BlockPos pos = origin.offset(part.x, part.y, part.z);
            part.template.placeInWorld(level, pos, pos, settings, RandomSource.create(), FLAGS);
            CowboyMod.LOGGER.debug("Placed structure part '{}' at {}", part.id, pos);
        }

        Vec3i totalSize = new Vec3i(maxX - minX, maxY - minY, maxZ - minZ);
        return Optional.of(finalizeStructureArena(level, origin, totalSize, cx, cy, cz));
    }

    private static PartOffset parsePartOffset(String entry) {
        String[] split = entry.split(",");
        if (split.length != 3) return null;
        try {
            PartOffset part = new PartOffset();
            part.x = Integer.parseInt(split[0].trim());
            part.y = Integer.parseInt(split[1].trim());
            part.z = Integer.parseInt(split[2].trim());
            return part;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class PartOffset {
        int x, y, z;
        StructureTemplate template;
        ResourceLocation id;
    }

    private static ArenaInfo finalizeStructureArena(ServerLevel level, BlockPos origin, Vec3i size,
                                                     int cx, int cy, int cz) {
        // 出生点 = 结构原点 + 配置偏移
        Vec3 spawnA = new Vec3(cx + CowboyConfig.structureSpawnAX,
                cy + CowboyConfig.structureSpawnAY,
                cz + CowboyConfig.structureSpawnAZ);
        Vec3 spawnB = new Vec3(cx + CowboyConfig.structureSpawnBX,
                cy + CowboyConfig.structureSpawnBY,
                cz + CowboyConfig.structureSpawnBZ);

        int margin = CowboyConfig.structureBoundsMargin;
        AABB bounds = new AABB(
                cx - margin, cy - 1, cz - margin,
                cx + size.getX() + margin, cy + size.getY() + margin, cz + size.getZ() + margin
        );

        applyToConfig(spawnA, spawnB, CowboyConfig.structureSpawnAYaw,
                CowboyConfig.structureSpawnBYaw, bounds);
        CowboyMod.LOGGER.info("Western Cowboy duel arena placed from structure(s) at {}", origin);
        return new ArenaInfo(spawnA, spawnB,
                CowboyConfig.structureSpawnAYaw, CowboyConfig.structureSpawnBYaw, bounds);
    }

    /**
     * 模式二：自动生成平整场地（默认）。
     */
    private static ArenaInfo buildGenerated(ServerLevel level) {
        int cx = CowboyConfig.arenaCenterX;
        int cy = CowboyConfig.arenaCenterY;
        int cz = CowboyConfig.arenaCenterZ;
        int sx = CowboyConfig.arenaSizeX;
        int sz = CowboyConfig.arenaSizeZ;
        int halfX = sx / 2;
        int halfZ = sz / 2;
        int minX = cx - halfX;
        int maxX = cx + halfX;
        int minZ = cz - halfZ;
        int maxZ = cz + halfZ;
        int floorY = cy;
        int wallTop = floorY + CowboyConfig.arenaWallHeight;
        int clearTop = floorY + 12;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1. 清空场地内部及上方空间
        for (int x = minX - 2; x <= maxX + 2; x++) {
            for (int z = minZ - 2; z <= maxZ + 2; z++) {
                for (int y = floorY - 2; y <= clearTop; y++) {
                    pos.set(x, y, z);
                    level.setBlock(pos, AIR, FLAGS);
                }
            }
        }

        // 2. 铺设地板
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, floorY, z);
                level.setBlock(pos, FLOOR, FLAGS);
            }
        }

        // 3. 建造边界墙（Barrier，防止走出场地且不影响视线）
        for (int y = floorY + 1; y <= wallTop; y++) {
            for (int x = minX - 1; x <= maxX + 1; x++) {
                pos.set(x, y, minZ - 1);
                level.setBlock(pos, WALL, FLAGS);
                pos.set(x, y, maxZ + 1);
                level.setBlock(pos, WALL, FLAGS);
            }
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(minX - 1, y, z);
                level.setBlock(pos, WALL, FLAGS);
                pos.set(maxX + 1, y, z);
                level.setBlock(pos, WALL, FLAGS);
            }
        }

        // 4. 四角与中心放置光源，防止刷怪
        placeLight(level, minX, floorY + 1, minZ);
        placeLight(level, maxX, floorY + 1, minZ);
        placeLight(level, minX, floorY + 1, maxZ);
        placeLight(level, maxX, floorY + 1, maxZ);
        placeLight(level, cx, floorY + 1, cz);

        // 5. 计算出生点：沿 Z 轴背对背，A 在南 (z+)、B 在北 (z-)
        int offset = CowboyConfig.arenaSpawnOffset;
        Vec3 spawnA = new Vec3(cx, floorY + 1, cz + offset);
        Vec3 spawnB = new Vec3(cx, floorY + 1, cz - offset);

        // 边界比地板大 1 格，用于 isInArenaBounds 检测
        AABB bounds = new AABB(minX - 1, floorY - 1, minZ - 1, maxX + 1, wallTop, maxZ + 1);

        // A 在南側 (z+) 朝南 (+Z, yaw=0)，B 在北側 (z-) 朝北 (-Z, yaw=180)，背对背
        applyToConfig(spawnA, spawnB, 0.0f, 180.0f, bounds);
        CowboyMod.LOGGER.info("Western Cowboy duel arena rebuilt at center ({}, {}, {})", cx, cy, cz);
        return new ArenaInfo(spawnA, spawnB, 0.0f, 180.0f, bounds);
    }

    private static void clearVolume(ServerLevel level, int minX, int minY, int minZ,
                                    int maxX, int maxY, int maxZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    level.setBlock(pos, AIR, FLAGS);
                }
            }
        }
    }

    private static void applyToConfig(Vec3 spawnA, Vec3 spawnB, float yawA, float yawB, AABB bounds) {
        CowboyConfig.spawnAX = spawnA.x;
        CowboyConfig.spawnAY = spawnA.y;
        CowboyConfig.spawnAZ = spawnA.z;
        CowboyConfig.spawnAYaw = yawA;
        CowboyConfig.spawnBX = spawnB.x;
        CowboyConfig.spawnBY = spawnB.y;
        CowboyConfig.spawnBZ = spawnB.z;
        CowboyConfig.spawnBYaw = yawB;
        CowboyConfig.arenaMinX = bounds.minX;
        CowboyConfig.arenaMinY = bounds.minY;
        CowboyConfig.arenaMinZ = bounds.minZ;
        CowboyConfig.arenaMaxX = bounds.maxX;
        CowboyConfig.arenaMaxY = bounds.maxY;
        CowboyConfig.arenaMaxZ = bounds.maxZ;
    }

    private static void placeLight(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, LIGHT, 3);
        }
    }
}
