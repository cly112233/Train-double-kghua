package xiao.hua.framework;

import net.fabricmc.loader.api.FabricLoader;
import xiao.hua.Huarolemods;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CompatibilityManager {
    private static final Set<String> REQUIRED_MODS = Set.of("starrailexpress", "fabric-api", "cardinal-components-base", "cardinal-components-entity");
    private static final Map<String, CompatibilityInfo> COMPATIBILITY_INFO = new HashMap<>();
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized)
            return;
        Huarolemods.LOGGER.info("Initializing compatibility manager...");
        checkRequiredMods();
        registerCompatibilityInfo();
        logCompatibilityStatus();
        initialized = true;
        Huarolemods.LOGGER.info("Compatibility manager initialized");
    }

    private static void checkRequiredMods() {
        for (String modId : REQUIRED_MODS) {
            boolean loaded = FabricLoader.getInstance().isModLoaded(modId);
            if (!loaded)
                Huarolemods.LOGGER.error("Required mod not found: {} - some features may not work!", modId);
        }
    }

    private static void registerCompatibilityInfo() {
        registerModCompatibility("starrailexpress", "4.0.0", "4.0.0", true);
        registerModCompatibility("fabric-api", "0.116.0", "0.116.7", true);
        registerModCompatibility("cardinal-components-base", "6.0.0", "6.1.1", true);
        registerModCompatibility("cardinal-components-entity", "6.0.0", "6.1.1", true);
        registerModCompatibility("voicechat", "1.21.1", "1.21.1", false);
        registerModCompatibility("exposure-polaroid", "2.0.0", "2.0.0", false);
    }

    public static void registerModCompatibility(String modId, String minVersion, String testedVersion, boolean required) {
        boolean loaded = FabricLoader.getInstance().isModLoaded(modId);
        String currentVersion = loaded ? FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown") : "not loaded";
        COMPATIBILITY_INFO.put(modId, new CompatibilityInfo(modId, minVersion, testedVersion, currentVersion, loaded, required, isCompatibleVersion(currentVersion, minVersion)));
    }

    private static boolean isCompatibleVersion(String currentVersion, String minVersion) {
        if ("not loaded".equals(currentVersion) || "unknown".equals(currentVersion))
            return false;
        try {
            String[] currentParts = currentVersion.split("\\.");
            String[] minParts = minVersion.split("\\.");
            for (int i = 0; i < Math.min(currentParts.length, minParts.length); i++) {
                int current = Integer.parseInt(currentParts[i].replaceAll("[^0-9]", ""));
                int min = Integer.parseInt(minParts[i].replaceAll("[^0-9]", ""));
                if (current > min)
                    return true;
                if (current < min)
                    return false;
            }
            return currentParts.length >= minParts.length;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isModCompatible(String modId) {
        CompatibilityInfo info = COMPATIBILITY_INFO.get(modId);
        return info != null && info.isLoaded() && info.isCompatible();
    }

    public static CompatibilityInfo getCompatibilityInfo(String modId) {
        return COMPATIBILITY_INFO.get(modId);
    }

    private static void logCompatibilityStatus() {
        Huarolemods.LOGGER.info("=== Compatibility Status ===");
        for (CompatibilityInfo info : COMPATIBILITY_INFO.values()) {
            String status = info.isLoaded() ? (info.isCompatible() ? "COMPATIBLE" : "INCOMPATIBLE VERSION") : (info.isRequired() ? "MISSING (REQUIRED)" : "NOT LOADED (OPTIONAL)");
            Huarolemods.LOGGER.info("{}: {} (required: {}, tested: {}, current: {})", info.getModId(), status, info.isRequired(), info.getTestedVersion(), info.getCurrentVersion());
        }
        Huarolemods.LOGGER.info("============================");
    }

    public static Map<String, CompatibilityInfo> getAllCompatibilityInfo() {
        return Map.copyOf(COMPATIBILITY_INFO);
    }

    public static class CompatibilityInfo {
        private final String modId;
        private final String minVersion;
        private final String testedVersion;
        private final String currentVersion;
        private final boolean loaded;
        private final boolean required;
        private final boolean compatible;

        public CompatibilityInfo(String modId, String minVersion, String testedVersion, String currentVersion, boolean loaded, boolean required, boolean compatible) {
            this.modId = modId;
            this.minVersion = minVersion;
            this.testedVersion = testedVersion;
            this.currentVersion = currentVersion;
            this.loaded = loaded;
            this.required = required;
            this.compatible = compatible;
        }

        public String getModId() {
            return modId;
        }

        public String getMinVersion() {
            return minVersion;
        }

        public String getTestedVersion() {
            return testedVersion;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public boolean isRequired() {
            return required;
        }

        public boolean isCompatible() {
            return compatible;
        }
    }
}