package xiao.hua.framework;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xiao.hua.Huarolemods;

public class ResourceLoader {
    public static void initialize() {
        Huarolemods.LOGGER.info("Initializing ResourceLoader...");
    }

    public static class ResourceReloadListener extends SimplePreparableReloadListener<Void> implements IdentifiableResourceReloadListener {
        private final ResourceLocation id;

        public ResourceReloadListener(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation getFabricId() {
            return this.id;
        }

        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void prepared, ResourceManager manager, ProfilerFiller profiler) {
            Huarolemods.LOGGER.info("Reloading HuaRoleMods resources...");
        }
    }

    public static class ResourceLoadException extends RuntimeException {
        public ResourceLoadException(String message) {
            super(message);
        }

        public ResourceLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class LoadContext {
        private final ResourceManager resourceManager;
        private final String namespace;

        public LoadContext(ResourceManager resourceManager, String namespace) {
            this.resourceManager = resourceManager;
            this.namespace = namespace;
        }

        public ResourceManager getResourceManager() {
            return resourceManager;
        }

        public String getNamespace() {
            return namespace;
        }
    }
}