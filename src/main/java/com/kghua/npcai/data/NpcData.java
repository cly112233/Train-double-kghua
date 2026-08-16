package com.kghua.npcai.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class NpcData {
    public enum FollowMode {
        FIXED, RANDOM_WALK, FOLLOW_RED_DOT
    }

    public enum ViewMode {
        RANDOM, FOLLOW_NEAREST_PLAYER
    }

    private final UUID npcUuid;
    private String displayName = "";
    private String skinName = "";
    private FollowMode followMode = FollowMode.FIXED;
    private ViewMode viewMode = ViewMode.RANDOM;
    private float scale = 1.0f;
    private String heldItem = "";
    // 装备槽位（物品ID，空串=空）：主手/副手/头/胸/腿/脚
    private String mainHand = "";
    private String offHand = "";
    private String headSlot = "";
    private String chestSlot = "";
    private String legsSlot = "";
    private String feetSlot = "";
    private double x;
    private double y;
    private double z;
    private boolean hasPosition = false;
    private String level = "minecraft:overworld";
    // 活动范围（非固定模式）：中心点 + 半径（格数），radius<=0 表示不限
    private double roamX;
    private double roamY;
    private double roamZ;
    private double roamRadius = -1;
    private final List<TeleportPoint> teleportPoints = new ArrayList<>();
    private boolean deleted = false;
    private boolean dirty = false;

    public NpcData(UUID npcUuid) {
        this.npcUuid = npcUuid;
    }

    public UUID getNpcUuid() {
        return npcUuid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        markDirty();
    }

    public String getSkinName() {
        return skinName;
    }

    public void setSkinName(String skinName) {
        this.skinName = skinName;
        markDirty();
    }

    public FollowMode getFollowMode() {
        return followMode;
    }

    public void setFollowMode(FollowMode followMode) {
        this.followMode = followMode;
        markDirty();
    }

    public ViewMode getViewMode() {
        return viewMode;
    }

    public void setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode;
        markDirty();
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.2f, Math.min(2.0f, scale));
        markDirty();
    }

    public String getHeldItem() {
        return heldItem;
    }

    public void setHeldItem(String heldItem) {
        this.heldItem = heldItem;
        markDirty();
    }

    public String getMainHand() { return mainHand; }
    public String getOffHand() { return offHand; }
    public String getHeadSlot() { return headSlot; }
    public String getChestSlot() { return chestSlot; }
    public String getLegsSlot() { return legsSlot; }
    public String getFeetSlot() { return feetSlot; }

    public void setEquipment(String mainHand, String offHand, String head, String chest, String legs, String feet) {
        this.mainHand = mainHand != null ? mainHand : "";
        this.offHand = offHand != null ? offHand : "";
        this.headSlot = head != null ? head : "";
        this.chestSlot = chest != null ? chest : "";
        this.legsSlot = legs != null ? legs : "";
        this.feetSlot = feet != null ? feet : "";
        markDirty();
    }

    /** 按槽位获取物品ID */
    public String getSlotItem(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> mainHand;
            case OFFHAND -> offHand;
            case HEAD -> headSlot;
            case CHEST -> chestSlot;
            case LEGS -> legsSlot;
            case FEET -> feetSlot;
            default -> "";
        };
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasPosition = true;
        markDirty();
    }

    public boolean hasPosition() {
        return hasPosition;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
        markDirty();
    }

    public double getRoamX() { return roamX; }
    public double getRoamY() { return roamY; }
    public double getRoamZ() { return roamZ; }
    public double getRoamRadius() { return roamRadius; }

    public void setRoamCenter(double x, double y, double z) {
        this.roamX = x;
        this.roamY = y;
        this.roamZ = z;
        markDirty();
    }

    public void setRoamRadius(double radius) {
        this.roamRadius = radius;
        markDirty();
    }

    /** 是否启用了活动范围限制 */
    public boolean hasRoamLimit() {
        return roamRadius > 0;
    }

    public List<TeleportPoint> getTeleportPoints() {
        // 按修改时间倒序，最新的在最前面
        return teleportPoints.stream()
            .sorted(Comparator.comparingLong(TeleportPoint::updatedAt).reversed())
            .toList();
    }

    public void addTeleportPoint(TeleportPoint point) {
        teleportPoints.add(point);
        markDirty();
    }

    public void removeTeleportPoint(String name) {
        teleportPoints.removeIf(p -> p.name().equals(name));
        markDirty();
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        markDirty();
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("DisplayName", displayName);
        tag.putString("SkinName", skinName);
        tag.putString("FollowMode", followMode.name());
        tag.putString("ViewMode", viewMode.name());
        tag.putFloat("Scale", scale);
        tag.putString("HeldItem", heldItem);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putBoolean("HasPosition", hasPosition);
        tag.putString("Level", level);

        ListTag list = new ListTag();
        for (TeleportPoint point : teleportPoints) {
            list.add(point.toNbt());
        }
        tag.putBoolean("Deleted", deleted);
        tag.putString("MainHand", mainHand);
        tag.putString("OffHand", offHand);
        tag.putString("HeadSlot", headSlot);
        tag.putString("ChestSlot", chestSlot);
        tag.putString("LegsSlot", legsSlot);
        tag.putString("FeetSlot", feetSlot);
        tag.putDouble("RoamX", roamX);
        tag.putDouble("RoamY", roamY);
        tag.putDouble("RoamZ", roamZ);
        tag.putDouble("RoamRadius", roamRadius);
        tag.put("TeleportPoints", list);
        return tag;
    }

    public static NpcData fromNbt(UUID uuid, CompoundTag tag) {
        NpcData data = new NpcData(uuid);
        data.displayName = tag.getString("DisplayName");
        data.skinName = tag.getString("SkinName");
        try {
            data.followMode = FollowMode.valueOf(tag.getString("FollowMode"));
        } catch (IllegalArgumentException ignored) {
            data.followMode = FollowMode.FIXED;
        }
        try {
            data.viewMode = ViewMode.valueOf(tag.getString("ViewMode"));
        } catch (IllegalArgumentException ignored) {
            data.viewMode = ViewMode.RANDOM;
        }
        data.scale = tag.getFloat("Scale");
        if (data.scale <= 0) data.scale = 1.0f;
        data.heldItem = tag.getString("HeldItem");
        data.x = tag.contains("X") ? tag.getDouble("X") : 0.0;
        data.y = tag.contains("Y") ? tag.getDouble("Y") : 0.0;
        data.z = tag.contains("Z") ? tag.getDouble("Z") : 0.0;
        data.hasPosition = tag.contains("HasPosition") && tag.getBoolean("HasPosition");
        data.level = tag.contains("Level") ? tag.getString("Level") : "minecraft:overworld";
        data.deleted = tag.contains("Deleted") && tag.getBoolean("Deleted");
        data.mainHand = tag.contains("MainHand") ? tag.getString("MainHand") : "";
        data.offHand = tag.contains("OffHand") ? tag.getString("OffHand") : "";
        data.headSlot = tag.contains("HeadSlot") ? tag.getString("HeadSlot") : "";
        data.chestSlot = tag.contains("ChestSlot") ? tag.getString("ChestSlot") : "";
        data.legsSlot = tag.contains("LegsSlot") ? tag.getString("LegsSlot") : "";
        data.feetSlot = tag.contains("FeetSlot") ? tag.getString("FeetSlot") : "";
        data.roamX = tag.contains("RoamX") ? tag.getDouble("RoamX") : 0;
        data.roamY = tag.contains("RoamY") ? tag.getDouble("RoamY") : 0;
        data.roamZ = tag.contains("RoamZ") ? tag.getDouble("RoamZ") : 0;
        data.roamRadius = tag.contains("RoamRadius") ? tag.getDouble("RoamRadius") : -1;

        ListTag list = tag.getList("TeleportPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.teleportPoints.add(TeleportPoint.fromNbt(list.getCompound(i)));
        }
        return data;
    }
}
