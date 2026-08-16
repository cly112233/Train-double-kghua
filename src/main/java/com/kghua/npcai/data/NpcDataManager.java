package com.kghua.npcai.data;

import com.kghua.npcai.NpcAiMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcDataManager {
    public static final UUID SINGLETON_NPC_UUID = new UUID(0, 1);
    private static final Map<UUID, NpcData> CACHE = new ConcurrentHashMap<>();
    private static final Path DATA_DIR = FabricLoader.getInstance()
        .getGameDir().resolve("npctalltome").resolve("npc_data");
    private static boolean loadedAll = false;

    public static NpcData get(UUID npcUuid) {
        return CACHE.computeIfAbsent(npcUuid, NpcDataManager::load);
    }

    public static void saveAll() {
        for (NpcData data : CACHE.values()) {
            if (data.isDirty()) {
                save(data);
            }
        }
    }

    public static void save(NpcData data) {
        try {
            Files.createDirectories(DATA_DIR);
            Path file = DATA_DIR.resolve(data.getNpcUuid().toString() + ".nbt");
            NbtIo.write(data.toNbt(), file);
            data.clearDirty();
            NpcAiMod.LOGGER.info("Saved NPC data: {}", file);
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to save NPC data for {}", data.getNpcUuid(), e);
        }
    }

    private static NpcData load(UUID uuid) {
        try {
            Path file = DATA_DIR.resolve(uuid.toString() + ".nbt");
            if (Files.exists(file)) {
                CompoundTag tag = NbtIo.read(file);
                if (tag != null) {
                    return NpcData.fromNbt(uuid, tag);
                }
            }
        } catch (IOException e) {
            NpcAiMod.LOGGER.error("Failed to load NPC data for {}", uuid, e);
        }
        return new NpcData(uuid);
    }

    public static Collection<NpcData> loadAll() {
        if (!loadedAll) {
            loadedAll = true;
            try {
                if (Files.exists(DATA_DIR)) {
                    try (var stream = Files.list(DATA_DIR)) {
                        stream.filter(p -> p.getFileName().toString().endsWith(".nbt"))
                            .forEach(p -> {
                                try {
                                    String name = p.getFileName().toString();
                                    UUID uuid = UUID.fromString(name.substring(0, name.length() - 4));
                                    if (!CACHE.containsKey(uuid)) {
                                        NpcData data = load(uuid);
                                        if (data != null && !data.isDeleted()) {
                                            CACHE.put(uuid, data);
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            });
                    }
                }
            } catch (IOException e) {
                NpcAiMod.LOGGER.error("Failed to load all NPC data", e);
            }
        }
        return List.copyOf(CACHE.values());
    }

    /**
     * 查找任意一个未删除的 NPC 数据 UUID（优先第一个/单例，其次其他）。
     * 用于"找任意 NPC"的场景（地图组传送面板、装备同步等）。
     */
    public static UUID findAnyNpcUuid() {
        if (CACHE.containsKey(SINGLETON_NPC_UUID)) {
            NpcData first = CACHE.get(SINGLETON_NPC_UUID);
            if (first != null && !first.isDeleted()) return SINGLETON_NPC_UUID;
        }
        for (var entry : CACHE.entrySet()) {
            if (!entry.getValue().isDeleted()) return entry.getKey();
        }
        return null;
    }

    public static void delete(UUID npcUuid) {
        NpcData data = CACHE.remove(npcUuid);
        Path file = DATA_DIR.resolve(npcUuid.toString() + ".nbt");
        if (Files.exists(file)) {
            if (data == null) {
                data = load(npcUuid);
            }
            data.setDeleted(true);
            save(data);
            NpcAiMod.LOGGER.info("Soft-deleted NPC data (kept on disk): {}", npcUuid);
        }
    }
}
