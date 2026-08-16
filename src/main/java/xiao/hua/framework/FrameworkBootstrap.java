package xiao.hua.framework;

import xiao.hua.Huarolemods;

public class FrameworkBootstrap {
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized)
            return;
        Huarolemods.LOGGER.info("Initializing HuaRole framework...");
        SkillManager.initialize();
        ResourceLoader.initialize();
        CompatibilityManager.initialize();
        initialized = true;
        Huarolemods.LOGGER.info("HuaRole framework initialized successfully!");
    }

    public static boolean isInitialized() {
        return initialized;
    }
}