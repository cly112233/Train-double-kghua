package com.cowboymod.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CowboyPuppetEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
        SynchedEntityData.defineId(CowboyPuppetEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final int MAX_LIFETIME = 12000;

    private int lifetime = 0;
    private GameProfile skinProfile;

    public CowboyPuppetEntity(EntityType<? extends LivingEntity> type, Level world) {
        super(type, world);
        this.setNoGravity(false);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createLivingAttributes() {
        return LivingEntity.createLivingAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    public void setOwner(Player owner) {
        this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        this.skinProfile = owner.getGameProfile();
        this.setCustomName(owner.getName());
        this.setCustomNameVisible(false);
        this.setPose(owner.getPose());
        this.yHeadRot = owner.yHeadRot;
        this.yBodyRot = owner.yBodyRot;
    }

    public Optional<UUID> getOwnerUuid() { return this.entityData.get(OWNER_UUID); }
    public GameProfile getSkinProfile() { return skinProfile; }

    @Override
    public void tick() {
        super.tick();
        lifetime++;
        if (lifetime > MAX_LIFETIME) { this.discard(); return; }
        if (level() instanceof ServerLevel sw) {
            getOwnerUuid().ifPresent(uid -> {
                if (sw.getServer().getPlayerList().getPlayer(uid) == null)
                    this.discard();
            });
        }
        this.setDeltaMovement(0, 0, 0);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }

    @Override public Iterable<ItemStack> getArmorSlots() { return List.of(); }
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}
    @Override public EquipmentSlot getEquipmentSlotForItem(ItemStack stack) { return EquipmentSlot.MAINHAND; }

    @Override public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        getOwnerUuid().ifPresent(uuid -> nbt.putUUID("OwnerUUID", uuid));
        nbt.putInt("Lifetime", lifetime);
    }

    @Override public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.hasUUID("OwnerUUID"))
            this.entityData.set(OWNER_UUID, Optional.of(nbt.getUUID("OwnerUUID")));
        this.lifetime = nbt.getInt("Lifetime");
    }
}
