package com.kghua.npcai.entity;

import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.data.NpcData;
import com.kghua.npcai.data.NpcDataManager;
import com.kghua.npcai.data.PlayerMapGroupStorage;
import com.kghua.npcai.item.NpcAdminToolItem;
import com.kghua.npcai.network.OpenNpcChatPacket;
import com.kghua.npcai.player.PlayerPendingTracker;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class CustomerServiceNpcEntity extends LivingEntity {
    private static final EntityDataAccessor<String> SKIN_NAME =
        SynchedEntityData.defineId(CustomerServiceNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NPC_NAME =
        SynchedEntityData.defineId(CustomerServiceNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> FOLLOW_MODE =
        SynchedEntityData.defineId(CustomerServiceNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VIEW_MODE =
        SynchedEntityData.defineId(CustomerServiceNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE =
        SynchedEntityData.defineId(CustomerServiceNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> HELD_ITEM =
        SynchedEntityData.defineId(CustomerServiceNpcEntity.class, EntityDataSerializers.STRING);

    private static final double FOLLOW_STOP_DISTANCE = 1.5;
    private static final double FOLLOW_SPEED = 0.15;
    private static final double RANDOM_WALK_SPEED = 0.12;
    private static final float HEAD_TURN_SPEED = 5.0f;
    private static final float BODY_TURN_SPEED = 5.0f;
    private static final float PITCH_TURN_SPEED = 5.0f;
    private static final float MAX_HEAD_YAW = 90.0f;
    private static final float MOVE_ANGLE_THRESHOLD = 15.0f;
    private static final int RANDOM_WALK_INTERVAL = 40;
    private static final int POSITION_SYNC_INTERVAL = 20;

    private GameProfile skinProfile;
    private ItemStack heldItemStack = ItemStack.EMPTY;
    private BlockPos randomWalkTarget;
    private int randomWalkCooldown;
    private int positionSyncCounter;

    public CustomerServiceNpcEntity(EntityType<? extends LivingEntity> type, Level world) {
        super(type, world);
        this.setUUID(NpcDataManager.SINGLETON_NPC_UUID);
        this.setInvulnerable(true);
        this.setNoGravity(false);
        this.setCustomNameVisible(true);
    }

    /** 指定 UUID 创建（支持多个 NPC），第一个 NPC 默认使用单例 UUID */
    public CustomerServiceNpcEntity(java.util.UUID npcUuid, Level world, double x, double y, double z,
                                    String skinName, String npcName) {
        super(NpcAiMod.CUSTOMER_SERVICE_NPC, world);
        this.setUUID(npcUuid != null ? npcUuid : NpcDataManager.SINGLETON_NPC_UUID);
        this.setInvulnerable(true);
        this.setNoGravity(false);
        this.setCustomNameVisible(true);
        this.setPos(x, y, z);
        setSkinName(skinName);
        setNpcName(npcName);
        if (!world.isClientSide) {
            NpcData data = NpcDataManager.get(getUUID());
            data.setDeleted(false); // 重新激活数据
            data.setPos(x, y, z);
            data.setLevel(world.dimension().location().toString());
        }
    }

    public CustomerServiceNpcEntity(Level world, double x, double y, double z, String skinName, String npcName) {
        this(NpcDataManager.SINGLETON_NPC_UUID, world, x, y, z, skinName, npcName);
    }

    /**
     * 统计服务器所有维度中已存在的客服NPC实体数量。
     */
    public static int countAnywhere(net.minecraft.server.MinecraftServer server) {
        if (server == null) return 0;
        int count = 0;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(NpcAiMod.CUSTOMER_SERVICE_NPC, e -> true).size();
        }
        return count;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_NAME, "");
        builder.define(NPC_NAME, "");
        builder.define(FOLLOW_MODE, 0);
        builder.define(VIEW_MODE, 0);
        builder.define(SCALE, 1.0f);
        builder.define(HELD_ITEM, "");
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            tickMovement();
            tickLook();
            if (++positionSyncCounter >= POSITION_SYNC_INTERVAL) {
                positionSyncCounter = 0;
                updateDataPosition();
            }
        }
    }

    private void updateDataPosition() {
        NpcData data = NpcDataManager.get(getUUID());
        data.setPos(getX(), getY(), getZ());
        data.setLevel(level().dimension().location().toString());
    }

    private void tickMovement() {
        NpcData.FollowMode mode = getFollowMode();
        switch (mode) {
            case FIXED -> setDeltaMovement(new Vec3(0, getDeltaMovement().y, 0));
            case RANDOM_WALK -> tickRandomWalk();
            case FOLLOW_RED_DOT -> tickFollowRedDot();
        }
        // 活动范围限制必须在移动逻辑之后：超出半径则强制朝中心走（覆盖所有移动模式）
        enforceRoamLimit();
    }

    /** 活动范围：超出中心点水平半径则拉回中心（不限制Y值） */
    private void enforceRoamLimit() {
        NpcData data = NpcDataManager.get(getUUID());
        if (!data.hasRoamLimit()) return;

        double dx = getX() - data.getRoamX();
        double dz = getZ() - data.getRoamZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > data.getRoamRadius()) {
            // 朝中心移动（水平方向，不改变Y）
            double speed = 0.5;
            Vec3 current = getDeltaMovement();
            setDeltaMovement(-dx / dist * speed, current.y, -dz / dist * speed);
            if (getFollowMode() != NpcData.FollowMode.RANDOM_WALK) {
                // 强制面向中心方向
                this.yBodyRot = (float) (Mth.atan2(-dx, -dz) * Mth.RAD_TO_DEG);
            }
        }
    }

    private void tickLook() {
        if (getViewMode() != NpcData.ViewMode.FOLLOW_NEAREST_PLAYER) return;

        Player target = findNearestPlayer(false);
        if (target == null) return;

        lookAt(target.getX(), target.getEyeY(), target.getZ(), true);
    }

    private void tickFollowRedDot() {
        ServerPlayer target = findPendingPlayer();
        if (target == null) return;

        double dx = target.getX() - getX();
        double dy = target.getY() - getY();
        double dz = target.getZ() - getZ();
        double distHoriz = Math.sqrt(dx * dx + dz * dz);

        // 先转向目标；未对准时不前进
        boolean aligned = rotateBodyTowards(target.getX(), target.getZ(), BODY_TURN_SPEED);
        lookAt(target.getX(), target.getEyeY(), target.getZ(), false);

        Vec3 motion = Vec3.ZERO;
        if (aligned && distHoriz > FOLLOW_STOP_DISTANCE) {
            double speedY = Mth.clamp(dy * 0.05, -FOLLOW_SPEED * 0.5, FOLLOW_SPEED * 0.5);
            motion = moveForward(FOLLOW_SPEED).add(0, speedY, 0);
        }
        setDeltaMovement(motion);
    }

    private void tickRandomWalk() {
        if (randomWalkCooldown-- <= 0 || randomWalkTarget == null || blockPosition().equals(randomWalkTarget)) {
            randomWalkTarget = findRandomWalkTarget();
            randomWalkCooldown = RANDOM_WALK_INTERVAL + random.nextInt(20);
        }

        if (randomWalkTarget == null) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 targetCenter = Vec3.atBottomCenterOf(randomWalkTarget);
        boolean aligned = rotateBodyTowards(targetCenter.x, targetCenter.z, BODY_TURN_SPEED);
        if (aligned) {
            setDeltaMovement(moveForward(RANDOM_WALK_SPEED));
        } else {
            setDeltaMovement(new Vec3(0, getDeltaMovement().y, 0));
        }
    }

    private void lookAt(double targetX, double targetY, double targetZ, boolean rotateBody) {
        double dx = targetX - getX();
        double dy = targetY - getEyeY();
        double dz = targetZ - getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return;

        float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float targetPitch = (float) (-(Mth.atan2(dy, dist) * Mth.RAD_TO_DEG));

        // 如需转身，先让身体转向目标
        if (rotateBody) {
            this.yBodyRot = rotateTowards(this.yBodyRot, targetYaw, BODY_TURN_SPEED);
            setYRot(this.yBodyRot);
        }

        // 头部平滑转向目标，但严格限制在身体朝向 ±90° 以内
        float headYaw = rotateTowards(getYHeadRot(), targetYaw, HEAD_TURN_SPEED);
        headYaw = clampHeadYaw(headYaw);

        float pitch = Mth.clamp(rotateTowards(getXRot(), targetPitch, PITCH_TURN_SPEED), -90.0f, 90.0f);

        setYHeadRot(headYaw);
        setXRot(pitch);
    }

    private float clampHeadYaw(float headYaw) {
        float delta = Mth.wrapDegrees(headYaw - this.yBodyRot);
        if (delta > MAX_HEAD_YAW) {
            // 头部已到右极限，身体必须立即跟上，使头部保持在正前方 ±90°
            this.yBodyRot = Mth.wrapDegrees(headYaw - MAX_HEAD_YAW);
            setYRot(this.yBodyRot);
            return headYaw;
        }
        if (delta < -MAX_HEAD_YAW) {
            // 头部已到左极限，身体必须立即跟上
            this.yBodyRot = Mth.wrapDegrees(headYaw + MAX_HEAD_YAW);
            setYRot(this.yBodyRot);
            return headYaw;
        }
        return headYaw;
    }

    private boolean rotateBodyTowards(double targetX, double targetZ, float speed) {
        double dx = targetX - getX();
        double dz = targetZ - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.01) return true;

        float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        this.yBodyRot = rotateTowards(this.yBodyRot, targetYaw, speed);
        setYRot(this.yBodyRot);

        // 移动时头部必须跟随身体，保持 ±90° 以内
        setYHeadRot(clampHeadYaw(getYHeadRot()));

        float diff = Mth.wrapDegrees(targetYaw - this.yBodyRot);
        return Math.abs(diff) < MOVE_ANGLE_THRESHOLD;
    }

    private Vec3 moveForward(double speed) {
        float yawRad = this.yBodyRot * Mth.DEG_TO_RAD;
        double mx = -Mth.sin(yawRad) * speed;
        double mz = Mth.cos(yawRad) * speed;
        return new Vec3(mx, getDeltaMovement().y, mz);
    }

    private ServerPlayer findPendingPlayer() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;

        ServerPlayer target = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (!PlayerPendingTracker.hasPending(player)) continue;
            double dist = distanceToSqr(player);
            if (dist < bestDist) {
                bestDist = dist;
                target = player;
            }
        }
        return target;
    }

    private Player findNearestPlayer(boolean includeSpectators) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;

        Player target = null;
        double bestDist = Double.MAX_VALUE;
        for (Player player : serverLevel.players()) {
            if (!player.isAlive()) continue;
            if (!includeSpectators && player.isSpectator()) continue;
            double dist = distanceToSqr(player);
            if (dist < bestDist) {
                bestDist = dist;
                target = player;
            }
        }
        return target;
    }

    private BlockPos findRandomWalkTarget() {
        BlockPos pos = blockPosition();
        NpcData data = NpcDataManager.get(getUUID());
        for (int i = 0; i < 10; i++) {
            int dx = random.nextInt(10) - 5;
            int dz = random.nextInt(10) - 5;
            BlockPos candidate = pos.offset(dx, 0, dz);
            // 活动范围限制
            if (data.hasRoamLimit()) {
                double cdx = candidate.getX() - data.getRoamX();
                double cdz = candidate.getZ() - data.getRoamZ();
                if (Math.sqrt(cdx * cdx + cdz * cdz) > data.getRoamRadius()) continue;
            }
            // 寻找可站立位置
            if (level().getBlockState(candidate).isAir() && level().getBlockState(candidate.below()).isSolidRender(level(), candidate.below())) {
                return candidate;
            }
            // 尝试向上找一格
            BlockPos up = candidate.above();
            if (level().getBlockState(up).isAir() && level().getBlockState(candidate).isSolidRender(level(), candidate)) {
                return up;
            }
        }
        return pos;
    }

    private float rotateTowards(float current, float target, float maxDelta) {
        float delta = Mth.wrapDegrees(target - current);
        return Mth.wrapDegrees(current + Mth.clamp(delta, -maxDelta, maxDelta));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide) {
            if (key.equals(SKIN_NAME)) {
                updateSkinProfile();
            }
            if (key.equals(NPC_NAME)) {
                this.setCustomName(Component.literal(getNpcName()));
            }
            if (key.equals(HELD_ITEM)) {
                this.heldItemStack = parseHeldItem(getHeldItemId());
            }
        }
    }

    private void updateSkinProfile() {
        String skin = getSkinName();
        if (!skin.isEmpty()) {
            this.skinProfile = new GameProfile(UUID.nameUUIDFromBytes(skin.getBytes()), skin);
        }
    }

    public String getSkinName() {
        return this.entityData.get(SKIN_NAME);
    }

    public String getNpcName() {
        return this.entityData.get(NPC_NAME);
    }

    public NpcData.FollowMode getFollowMode() {
        return NpcData.FollowMode.values()[this.entityData.get(FOLLOW_MODE)];
    }

    public void setFollowMode(NpcData.FollowMode mode) {
        this.entityData.set(FOLLOW_MODE, mode.ordinal());
        if (!level().isClientSide) {
            NpcDataManager.get(getUUID()).setFollowMode(mode);
        }
    }

    public NpcData.ViewMode getViewMode() {
        return NpcData.ViewMode.values()[this.entityData.get(VIEW_MODE)];
    }

    public void setViewMode(NpcData.ViewMode mode) {
        this.entityData.set(VIEW_MODE, mode.ordinal());
        if (!level().isClientSide) {
            NpcDataManager.get(getUUID()).setViewMode(mode);
        }
    }

    @Override
    public float getScale() {
        return this.entityData.get(SCALE);
    }

    public void setScale(float scale) {
        float clamped = Math.max(0.2f, Math.min(2.0f, scale));
        this.entityData.set(SCALE, clamped);
        if (!level().isClientSide) {
            NpcDataManager.get(getUUID()).setScale(clamped);
        }
    }

    public String getHeldItemId() {
        return this.entityData.get(HELD_ITEM);
    }

    public void setHeldItem(String itemId) {
        this.entityData.set(HELD_ITEM, itemId != null ? itemId : "");
        this.heldItemStack = parseHeldItem(itemId);
        if (!level().isClientSide) {
            NpcDataManager.get(getUUID()).setHeldItem(itemId != null ? itemId : "");
        }
    }

    private ItemStack parseHeldItem(String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) return ItemStack.EMPTY;
        try {
            ResourceLocation id = ResourceLocation.parse(itemId.trim());
            var item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    public GameProfile getSkinProfile() {
        return skinProfile;
    }

    public void setSkinName(String skinName) {
        this.entityData.set(SKIN_NAME, skinName);
        if (level().isClientSide) {
            updateSkinProfile();
        }
        if (!level().isClientSide) {
            NpcDataManager.get(getUUID()).setSkinName(skinName);
        }
    }

    public void setNpcName(String npcName) {
        this.entityData.set(NPC_NAME, npcName);
        this.setCustomName(Component.literal(npcName));
        if (!level().isClientSide) {
            NpcDataManager.get(getUUID()).setDisplayName(npcName);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() instanceof NpcAdminToolItem) {
            if (!com.kghua.npcai.data.NpcAdminStorage.isAdmin(serverPlayer.getUUID())) {
                serverPlayer.sendSystemMessage(Component.literal("§c您不是NPC管理员，无法打开NPC管理端"));
                return InteractionResult.FAIL;
            }
            // 管理员手持管理物品，打开管理界面
            NpcAiMod.openAdminScreen(serverPlayer, this);
        } else {
            // 普通玩家打开聊天界面，携带未读通知计数
            int mailCount = 0;
            try {
                mailCount = com.kghua.npcai.mailbridge.MailBridge.getUnreadCount(serverPlayer);
            } catch (Exception ignored) {
            }
            int questionnaireCount = 0;
            try {
                String playerName = serverPlayer.getName().getString();
                for (com.kghua.npcai.data.Questionnaire q : com.kghua.npcai.data.QuestionnaireStorage.loadAll()) {
                    if (q.isActive() && !q.hasResponded(playerName)) {
                        questionnaireCount++;
                    }
                }
            } catch (Exception ignored) {
            }
            ServerPlayNetworking.send(serverPlayer, new OpenNpcChatPacket(
                getNpcName(), getId(), serverPlayer.getUUID().toString(),
                PlayerMapGroupStorage.isMember(serverPlayer.getUUID()),
                mailCount, questionnaireCount,
                com.kghua.npcai.data.NpcAdminStorage.isAdmin(serverPlayer.getUUID()),
                serverPlayer.hasPermissions(2)
            ));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("SkinName", getSkinName());
        nbt.putString("NpcName", getNpcName());
    }

    @Override
    public boolean shouldBeSaved() {
        // 不写入区块 NBT，随区块卸载自然消失；由 NpcDataManager 在区块加载时重新生成
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        setSkinName(nbt.getString("SkinName"));
        setNpcName(nbt.getString("NpcName"));
        NpcData data = NpcDataManager.get(getUUID());
        setFollowMode(data.getFollowMode());
        setViewMode(data.getViewMode());
        setScale(data.getScale());
        setHeldItem(data.getHeldItem());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        // 从 NpcData 装备槽读取
        NpcData data = NpcDataManager.get(getUUID());
        String itemId = data.getSlotItem(slot);
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        NpcData data = NpcDataManager.get(getUUID());
        String id = "";
        if (stack != null && !stack.isEmpty()) {
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            id = key != null ? key.toString() : "";
        }
        switch (slot) {
            case MAINHAND -> data.setEquipment(id, data.getOffHand(), data.getHeadSlot(), data.getChestSlot(), data.getLegsSlot(), data.getFeetSlot());
            case OFFHAND -> data.setEquipment(data.getMainHand(), id, data.getHeadSlot(), data.getChestSlot(), data.getLegsSlot(), data.getFeetSlot());
            case HEAD -> data.setEquipment(data.getMainHand(), data.getOffHand(), id, data.getChestSlot(), data.getLegsSlot(), data.getFeetSlot());
            case CHEST -> data.setEquipment(data.getMainHand(), data.getOffHand(), data.getHeadSlot(), id, data.getLegsSlot(), data.getFeetSlot());
            case LEGS -> data.setEquipment(data.getMainHand(), data.getOffHand(), data.getHeadSlot(), data.getChestSlot(), id, data.getFeetSlot());
            case FEET -> data.setEquipment(data.getMainHand(), data.getOffHand(), data.getHeadSlot(), data.getChestSlot(), data.getLegsSlot(), id);
            default -> {}
        }
        if (slot == EquipmentSlot.MAINHAND) {
            this.heldItemStack = getItemBySlot(slot);
        }
    }

    /** 同步装备后刷新实体的手持渲染 */
    public void refreshEquipmentFromData() {
        this.heldItemStack = getItemBySlot(EquipmentSlot.MAINHAND);
    }

    @Override
    public EquipmentSlot getEquipmentSlotForItem(ItemStack stack) {
        return EquipmentSlot.MAINHAND;
    }
}
