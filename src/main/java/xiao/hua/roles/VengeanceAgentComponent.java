package xiao.hua.roles;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import xiao.hua.Huarolemods;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VengeanceAgentComponent implements RoleComponent, ServerTickingComponent {
    private final Player player;

    public static ComponentKey<VengeanceAgentComponent> getKey() {
        return Huarolemods.VENGEANCE_AGENT_COMPONENT;
    }

    private final List<UUID> contractTargets = new ArrayList<>();
    private UUID currentLensTarget = null;
    private boolean lensEnabled = false;
    private boolean inspecting = false;
    private long lensCooldownEnd = 0L;
    private long protectedUntil = 0L;

    public VengeanceAgentComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {
        this.contractTargets.clear();
        this.currentLensTarget = null;
        this.lensEnabled = false;
        this.inspecting = false;
        this.lensCooldownEnd = 0L;
        this.protectedUntil = 0L;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public List<UUID> getContractTargets() {
        return this.contractTargets;
    }

    public void addContractTarget(UUID playerId) {
        if (!this.contractTargets.contains(playerId)) {
            this.contractTargets.add(playerId);
            sync();
        }
    }

    public void removeContractTarget(UUID playerId) {
        this.contractTargets.remove(playerId);
        if (this.currentLensTarget != null && this.currentLensTarget.equals(playerId))
            this.currentLensTarget = null;
        sync();
    }

    public void clearContractTargets() {
        this.contractTargets.clear();
        this.currentLensTarget = null;
        sync();
    }

    public boolean isContractTarget(UUID playerId) {
        return this.contractTargets.contains(playerId);
    }

    public UUID getCurrentLensTarget() {
        return this.currentLensTarget;
    }

    public void setCurrentLensTarget(UUID target) {
        this.currentLensTarget = target;
        sync();
    }

    public boolean isLensEnabled() {
        return this.lensEnabled;
    }

    public void enableLens() {
        this.lensEnabled = true;
        sync();
    }

    public boolean isLensOnCooldown() {
        return System.currentTimeMillis() < this.lensCooldownEnd;
    }

    public void setLensCooldown() {
        this.lensCooldownEnd = System.currentTimeMillis() + 60000L;
        sync();
    }

    public boolean isInspecting() {
        return this.inspecting;
    }

    public void startInspecting() {
        this.inspecting = true;
        sync();
    }

    public void stopInspecting() {
        this.inspecting = false;
        sync();
    }

    public long getLensCooldownRemaining() {
        long remaining = this.lensCooldownEnd - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public boolean isProtected() {
        return System.currentTimeMillis() < this.protectedUntil;
    }

    public void setProtectedUntil(long time) {
        this.protectedUntil = time;
        sync();
    }

    public void sync() {
        ComponentKey<VengeanceAgentComponent> key = getKey();
        if (key != null)
            key.sync(this.player);
    }

    @Override
    public void serverTick() {}

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {}

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        ListTag targetsTag = new ListTag();
        for (UUID target : this.contractTargets)
            targetsTag.add(StringTag.valueOf(target.toString()));
        tag.put("contractTargets", targetsTag);
        if (this.currentLensTarget != null)
            tag.putString("currentLensTarget", this.currentLensTarget.toString());
        tag.putBoolean("lensEnabled", this.lensEnabled);
        tag.putBoolean("inspecting", this.inspecting);
        tag.putLong("lensCooldownEnd", this.lensCooldownEnd);
        tag.putLong("protectedUntil", this.protectedUntil);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.contractTargets.clear();
        ListTag targetsTag = tag.getList("contractTargets", Tag.TAG_STRING);
        for (int i = 0; i < targetsTag.size(); i++)
            this.contractTargets.add(UUID.fromString(targetsTag.getString(i)));
        if (tag.contains("currentLensTarget")) {
            this.currentLensTarget = UUID.fromString(tag.getString("currentLensTarget"));
        } else {
            this.currentLensTarget = null;
        }
        this.lensEnabled = tag.getBoolean("lensEnabled");
        this.inspecting = tag.getBoolean("inspecting");
        this.lensCooldownEnd = tag.getLong("lensCooldownEnd");
        this.protectedUntil = tag.getLong("protectedUntil");
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }
}