package com.cowboymod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CowboyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir().resolve("western_cowboy.json");

    // 配置文件版本号，mod 更新后递增可强制覆盖用户旧配置
    public static final int CONFIG_VERSION = 1;
    public static int configVersion = CONFIG_VERSION;

    // ===== Duel Arena Configuration =====
    // 决斗场地中心 — 类似塔罗会议，位于高空远离主世界的位置。
    // 注意：SRE 在 Z >= 19000 时会判定为“掉出列车”并立即击杀，所以必须放在 Z < 19000。
    // X=10000 远离主建筑区域，避免与列车路线或玩家建筑冲突。
    public static int arenaCenterX = 10000, arenaCenterY = 200, arenaCenterZ = 10000;
    public static int arenaSizeX = 31, arenaSizeZ = 47;          // 与原区域比例接近（74 : 116 ≈ 31 : 47）
    public static int arenaSpawnOffset = 13;                     // 出生点距中心距离（背对背，每人再外移 3 格）
    public static int arenaWallHeight = 4;                       // 边界墙高度

    // 决斗场地出生点A — 牛仔必定在此位置（由 DuelArenaBuilder 生成后写入）
    // 基于原区域 12527,-4,3823 ~ 12600,22,3938 中 A(12563,3,3894) 的相对偏移 (36,7,74)
    public static double spawnAX = 10036, spawnAY = 207, spawnAZ = 10074;
    public static float spawnAYaw = 0;     // 朝向南侧（+Z），背对 B

    // 决斗场地出生点B — 被使用技能的玩家必定在此位置
    // 基于原区域中 B(12563,3,3875) 的相对偏移 (36,7,49)
    public static double spawnBX = 10036, spawnBY = 207, spawnBZ = 10049;
    public static float spawnBYaw = 180;   // 朝向北侧（-Z），背对 A

    // 决斗场地边界 (对角: minX minY minZ maxX maxY maxZ)
    // 结构尺寸 74*27*116，中心 (10000,200,10000)，margin=2
    public static double arenaMinX = 9998, arenaMinY = 199, arenaMinZ = 9998;
    public static double arenaMaxX = 10076, arenaMaxY = 229, arenaMaxZ = 10118;

    // ===== Custom Structure Template =====
    // 是否使用自定义结构模板（把你造的决斗场地导出为 NBT 放进 mod 资源）
    // 默认 true，强制使用你搭建的地图，不再自动生成。
    public static boolean useStructureTemplate = true;
    public static String structureTemplateId = "western_cowboy:duel_arena";
    // 结构模板内两个出生点相对于拼接原点的偏移。
    // 你的 6 块结构以最小角为原点保存，原区域 12527,-4,3823 ~ 12600,22,3938：
    //   原 A (12563, 3, 3894) -> 相对偏移 (36, 7, 74)（再往外移 3 格）
    //   原 B (12563, 3, 3875) -> 相对偏移 (36, 7, 49)（再往外移 3 格）
    public static double structureSpawnAX = 36, structureSpawnAY = 7, structureSpawnAZ = 74;
    public static float structureSpawnAYaw = 0;      // 朝向南侧（+Z），背对 B
    public static double structureSpawnBX = 36, structureSpawnBY = 7, structureSpawnBZ = 49;
    public static float structureSpawnBYaw = 180;    // 朝向北侧（-Z），背对 A
    // 使用结构模板时，边界在结构尺寸基础上额外扩展的格数
    public static int structureBoundsMargin = 2;

    // ===== Multi-Part Structure Template =====
    // 如果场地太大超过结构方块 48x48x48 上限，可切成多个结构模板，按顺序拼接。
    // 每个元素格式："模板ID相对base的偏移X,Y,Z"，实际模板ID = structureTemplateId + "_" + 序号
    // 下面默认配置对应区域 12527 -4 3823 ~ 12600 22 3938 切成 6 块（2x3 网格）：
    //   X: 48+26, Y: 27, Z: 48+48+20
    public static List<String> structureTemplateParts = new ArrayList<>(Arrays.asList(
            "0,0,0",
            "48,0,0",
            "0,0,48",
            "48,0,48",
            "0,0,96",
            "48,0,96"
    ));

    // ===== Duel Timings =====
    public static int duelCooldownSeconds = 300;    // 5 minutes
    public static int duelCostGold = 250;
    public static int countdownSeconds = 3;          // 3-2-1 countdown
    public static int duelDurationSeconds = 35;      // max duel time
    public static double maxTargetDistance = 18.0;   // blocks

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                ConfigData data = GSON.fromJson(Files.readString(CONFIG_PATH), ConfigData.class);
                if (data != null && data.configVersion == CONFIG_VERSION) {
                    arenaCenterX = data.arenaCenterX;
                    arenaCenterY = data.arenaCenterY;
                    arenaCenterZ = data.arenaCenterZ;
                    arenaSizeX = data.arenaSizeX;
                    arenaSizeZ = data.arenaSizeZ;
                    arenaSpawnOffset = data.arenaSpawnOffset;
                    arenaWallHeight = data.arenaWallHeight;
                    spawnAX = data.spawnAX;
                    spawnAY = data.spawnAY;
                    spawnAZ = data.spawnAZ;
                    spawnAYaw = data.spawnAYaw;
                    spawnBX = data.spawnBX;
                    spawnBY = data.spawnBY;
                    spawnBZ = data.spawnBZ;
                    spawnBYaw = data.spawnBYaw;
                    arenaMinX = data.arenaMinX;
                    arenaMinY = data.arenaMinY;
                    arenaMinZ = data.arenaMinZ;
                    arenaMaxX = data.arenaMaxX;
                    arenaMaxY = data.arenaMaxY;
                    arenaMaxZ = data.arenaMaxZ;
                    useStructureTemplate = data.useStructureTemplate;
                    structureTemplateId = data.structureTemplateId;
                    structureSpawnAX = data.structureSpawnAX;
                    structureSpawnAY = data.structureSpawnAY;
                    structureSpawnAZ = data.structureSpawnAZ;
                    structureSpawnAYaw = data.structureSpawnAYaw;
                    structureSpawnBX = data.structureSpawnBX;
                    structureSpawnBY = data.structureSpawnBY;
                    structureSpawnBZ = data.structureSpawnBZ;
                    structureSpawnBYaw = data.structureSpawnBYaw;
                    structureBoundsMargin = data.structureBoundsMargin;
                    if (data.structureTemplateParts != null) {
                        structureTemplateParts = new ArrayList<>(data.structureTemplateParts);
                    }

                    // 自动修正旧版配置中面对面朝向/间距的问题
                    migrateToBackToBack();
                    save();
                    return;
                }
                if (data != null && data.configVersion != CONFIG_VERSION) {
                    CowboyMod.LOGGER.info("Config version mismatch ({} != {}), resetting western_cowboy.json to defaults",
                            data.configVersion, CONFIG_VERSION);
                }
            } catch (IOException e) {
                CowboyMod.LOGGER.warn("Failed to load config, using defaults", e);
            }
        }

        // 没有配置或版本不匹配：重置为默认并保存
        resetToDefaults();
        save();
        CowboyMod.LOGGER.info("Created/reset default config at {}", CONFIG_PATH);
    }

    /** 将所有字段恢复为源码中的最新默认值 */
    private static void resetToDefaults() {
        ConfigData d = new ConfigData();
        arenaCenterX = d.arenaCenterX; arenaCenterY = d.arenaCenterY; arenaCenterZ = d.arenaCenterZ;
        arenaSizeX = d.arenaSizeX; arenaSizeZ = d.arenaSizeZ;
        arenaSpawnOffset = d.arenaSpawnOffset;
        arenaWallHeight = d.arenaWallHeight;
        spawnAX = d.spawnAX; spawnAY = d.spawnAY; spawnAZ = d.spawnAZ;
        spawnAYaw = d.spawnAYaw;
        spawnBX = d.spawnBX; spawnBY = d.spawnBY; spawnBZ = d.spawnBZ;
        spawnBYaw = d.spawnBYaw;
        arenaMinX = d.arenaMinX; arenaMinY = d.arenaMinY; arenaMinZ = d.arenaMinZ;
        arenaMaxX = d.arenaMaxX; arenaMaxY = d.arenaMaxY; arenaMaxZ = d.arenaMaxZ;
        useStructureTemplate = d.useStructureTemplate;
        structureTemplateId = d.structureTemplateId;
        structureSpawnAX = d.structureSpawnAX; structureSpawnAY = d.structureSpawnAY; structureSpawnAZ = d.structureSpawnAZ;
        structureSpawnAYaw = d.structureSpawnAYaw;
        structureSpawnBX = d.structureSpawnBX; structureSpawnBY = d.structureSpawnBY; structureSpawnBZ = d.structureSpawnBZ;
        structureSpawnBYaw = d.structureSpawnBYaw;
        structureBoundsMargin = d.structureBoundsMargin;
        structureTemplateParts = new ArrayList<>(d.structureTemplateParts);
        configVersion = d.configVersion;
    }

    /**
     * 检测并修正旧配置中的面对面出生点：改为背对背，并将两人沿直线各自外移 3 格。
     */
    private static void migrateToBackToBack() {
        // 1. 自动生成场地的出生点
        if (flipIfFaceToFace(spawnAX, spawnAZ, spawnBX, spawnBZ, spawnAYaw, spawnBYaw)) {
            double dx = spawnBX - spawnAX;
            double dz = spawnBZ - spawnAZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001) {
                double ux = dx / len, uz = dz / len;
                spawnAX -= ux * 3.0; spawnAZ -= uz * 3.0;
                spawnBX += ux * 3.0; spawnBZ += uz * 3.0;
            }
            if (arenaSpawnOffset < 13) arenaSpawnOffset = 13;
            spawnAYaw = normalizeYaw(spawnAYaw + 180f);
            spawnBYaw = normalizeYaw(spawnBYaw + 180f);
        }

        // 2. 自定义结构模板的出生点偏移
        if (flipIfFaceToFace(structureSpawnAX, structureSpawnAZ,
                structureSpawnBX, structureSpawnBZ,
                structureSpawnAYaw, structureSpawnBYaw)) {
            double dx = structureSpawnBX - structureSpawnAX;
            double dz = structureSpawnBZ - structureSpawnAZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001) {
                double ux = dx / len, uz = dz / len;
                structureSpawnAX -= ux * 3.0; structureSpawnAZ -= uz * 3.0;
                structureSpawnBX += ux * 3.0; structureSpawnBZ += uz * 3.0;
            }
            structureSpawnAYaw = normalizeYaw(structureSpawnAYaw + 180f);
            structureSpawnBYaw = normalizeYaw(structureSpawnBYaw + 180f);
        }
    }

    private static boolean flipIfFaceToFace(double ax, double az, double bx, double bz,
                                            float yawA, float yawB) {
        double dx = bx - ax;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= 0.001) return false;
        float towardB = normalizeYaw((float) Math.toDegrees(Math.atan2(dx, dz)));
        float towardA = normalizeYaw(towardB + 180f);
        // 旧版面对面：A 朝向 B，B 朝向 A
        return yawClose(yawA, towardB) && yawClose(yawB, towardA);
    }

    private static boolean yawClose(float a, float b) {
        float diff = normalizeYaw(a - b);
        return diff < 5f || diff > 355f;
    }

    private static float normalizeYaw(float yaw) {
        while (yaw < 0f) yaw += 360f;
        while (yaw >= 360f) yaw -= 360f;
        return yaw;
    }

    public static void save() {
        try {
            ConfigData data = new ConfigData();
            data.configVersion = CONFIG_VERSION;
            data.arenaCenterX = arenaCenterX; data.arenaCenterY = arenaCenterY; data.arenaCenterZ = arenaCenterZ;
            data.arenaSizeX = arenaSizeX; data.arenaSizeZ = arenaSizeZ;
            data.arenaSpawnOffset = arenaSpawnOffset;
            data.arenaWallHeight = arenaWallHeight;
            data.spawnAX = spawnAX; data.spawnAY = spawnAY; data.spawnAZ = spawnAZ;
            data.spawnAYaw = spawnAYaw;
            data.spawnBX = spawnBX; data.spawnBY = spawnBY; data.spawnBZ = spawnBZ;
            data.spawnBYaw = spawnBYaw;
            data.arenaMinX = arenaMinX; data.arenaMinY = arenaMinY; data.arenaMinZ = arenaMinZ;
            data.arenaMaxX = arenaMaxX; data.arenaMaxY = arenaMaxY; data.arenaMaxZ = arenaMaxZ;
            data.useStructureTemplate = useStructureTemplate;
            data.structureTemplateId = structureTemplateId;
            data.structureSpawnAX = structureSpawnAX; data.structureSpawnAY = structureSpawnAY; data.structureSpawnAZ = structureSpawnAZ;
            data.structureSpawnAYaw = structureSpawnAYaw;
            data.structureSpawnBX = structureSpawnBX; data.structureSpawnBY = structureSpawnBY; data.structureSpawnBZ = structureSpawnBZ;
            data.structureSpawnBYaw = structureSpawnBYaw;
            data.structureBoundsMargin = structureBoundsMargin;
            data.structureTemplateParts = new ArrayList<>(structureTemplateParts);
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            CowboyMod.LOGGER.error("Failed to save config", e);
        }
    }

    public static boolean isInArenaBounds(double x, double y, double z) {
        return x >= arenaMinX && x <= arenaMaxX
            && y >= arenaMinY && y <= arenaMaxY
            && z >= arenaMinZ && z <= arenaMaxZ;
    }

    private static class ConfigData {
        int configVersion = CONFIG_VERSION;
        int arenaCenterX = 10000, arenaCenterY = 200, arenaCenterZ = 10000;
        int arenaSizeX = 31, arenaSizeZ = 47;
        int arenaSpawnOffset = 13;
        int arenaWallHeight = 4;
        double spawnAX = 10036, spawnAY = 207, spawnAZ = 10074;
        float spawnAYaw = 0;
        double spawnBX = 10036, spawnBY = 207, spawnBZ = 10049;
        float spawnBYaw = 180;
        double arenaMinX = 9998, arenaMinY = 199, arenaMinZ = 9998;
        double arenaMaxX = 10076, arenaMaxY = 229, arenaMaxZ = 10118;
        boolean useStructureTemplate = true;
        String structureTemplateId = "western_cowboy:duel_arena";
        double structureSpawnAX = 36, structureSpawnAY = 7, structureSpawnAZ = 74;
        float structureSpawnAYaw = 0;
        double structureSpawnBX = 36, structureSpawnBY = 7, structureSpawnBZ = 49;
        float structureSpawnBYaw = 180;
        int structureBoundsMargin = 2;
        List<String> structureTemplateParts = new ArrayList<>(Arrays.asList(
                "0,0,0",
                "48,0,0",
                "0,0,48",
                "48,0,48",
                "0,0,96",
                "48,0,96"
        ));
    }
}
