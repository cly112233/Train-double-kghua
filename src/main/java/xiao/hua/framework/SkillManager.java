package xiao.hua.framework;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import xiao.hua.Huarolemods;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class SkillManager {
    private static final Map<ResourceLocation, Skill> SKILL_REGISTRY = new HashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Long>> COOLDOWN_MAP = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized)
            return;
        initialized = true;
        Huarolemods.LOGGER.info("Skill manager initialized");
    }

    public static void registerSkill(Skill skill) {
        SKILL_REGISTRY.put(skill.getId(), skill);
        Huarolemods.LOGGER.info("Registered skill: {}", skill.getId());
    }

    public static Skill getSkill(ResourceLocation id) {
        return SKILL_REGISTRY.get(id);
    }

    public static boolean useSkill(Player player, ResourceLocation skillId) {
        Skill skill = SKILL_REGISTRY.get(skillId);
        if (skill == null)
            return false;
        if (!canUseSkill(player, skillId))
            return false;
        boolean success = skill.execute(player);
        if (success && skill.getCooldown() > 0)
            setCooldown(player, skillId, skill.getCooldown());
        return success;
    }

    public static boolean canUseSkill(Player player, ResourceLocation skillId) {
        Skill skill = SKILL_REGISTRY.get(skillId);
        if (skill == null)
            return false;
        Map<ResourceLocation, Long> cooldowns = COOLDOWN_MAP.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Long endTime = cooldowns.get(skillId);
        if (endTime != null && System.currentTimeMillis() < endTime)
            return false;
        return skill.canExecute(player);
    }

    public static void setCooldown(Player player, ResourceLocation skillId, int cooldownMs) {
        Map<ResourceLocation, Long> cooldowns = COOLDOWN_MAP.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        cooldowns.put(skillId, System.currentTimeMillis() + cooldownMs);
    }

    public static long getRemainingCooldown(Player player, ResourceLocation skillId) {
        Map<ResourceLocation, Long> cooldowns = COOLDOWN_MAP.get(player.getUUID());
        if (cooldowns == null)
            return 0L;
        Long endTime = cooldowns.get(skillId);
        if (endTime == null)
            return 0L;
        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public static void resetCooldowns(Player player) {
        COOLDOWN_MAP.remove(player.getUUID());
    }

    public static void copyCooldowns(Player source, Player target) {
        Map<ResourceLocation, Long> sourceCooldowns = COOLDOWN_MAP.get(source.getUUID());
        if (sourceCooldowns != null)
            COOLDOWN_MAP.put(target.getUUID(), new HashMap<>(sourceCooldowns));
    }

    public static SkillBuilder builder(String id) {
        return new SkillBuilder(id);
    }

    public static class Skill {
        private final ResourceLocation id;
        private final int cooldown;
        private final Function<Player, Boolean> executor;
        private final Function<Player, Boolean> canExecutor;

        public Skill(ResourceLocation id, int cooldown, Function<Player, Boolean> executor, Function<Player, Boolean> canExecutor) {
            this.id = id;
            this.cooldown = cooldown;
            this.executor = executor;
            this.canExecutor = canExecutor;
        }

        public ResourceLocation getId() {
            return id;
        }

        public int getCooldown() {
            return cooldown;
        }

        public boolean execute(Player player) {
            return executor.apply(player);
        }

        public boolean canExecute(Player player) {
            return canExecutor != null ? canExecutor.apply(player) : true;
        }
    }

    public static class SkillBuilder {
        private final ResourceLocation id;
        private int cooldown = 0;
        private Function<Player, Boolean> executor;
        private Function<Player, Boolean> canExecutor;

        public SkillBuilder(String id) {
            this.id = ResourceLocation.fromNamespaceAndPath("huarolemods", id);
        }

        public SkillBuilder cooldown(int cooldownMs) {
            this.cooldown = cooldownMs;
            return this;
        }

        public SkillBuilder executor(Function<Player, Boolean> executor) {
            this.executor = executor;
            return this;
        }

        public SkillBuilder canExecute(Function<Player, Boolean> canExecutor) {
            this.canExecutor = canExecutor;
            return this;
        }

        public Skill build() {
            Skill skill = new Skill(id, cooldown, executor, canExecutor);
            registerSkill(skill);
            return skill;
        }
    }
}