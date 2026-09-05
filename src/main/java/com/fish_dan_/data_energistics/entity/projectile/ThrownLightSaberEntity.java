package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.registry.DEEntities;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.upgrades.UpgradeInventories;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import org.jspecify.annotations.Nullable;

public class ThrownLightSaberEntity extends AbstractArrow implements ItemSupplier {

    private static final int DESPAWN_TICKS = 20 * 60 * 5;
    private static final float BASE_DATA_DUST_DAMAGE_RATIO = 0.05F;
    private static final float DATA_DUST_DAMAGE_RATIO_PER_CARD = 0.05F;
    private static final double HOMING_RANGE = 24.0D;
    private static final double HOMING_STRENGTH = 0.35D;
    private static final double HOMING_MAX_STRENGTH = 0.85D;
    private static final double HOMING_CLOSE_RANGE = 8.0D;
    private static final double HOMING_HIT_MARGIN = 0.75D;
    private static final String TAG_DEALT_DAMAGE = "DealtDamage";
    private static final String TAG_DATA_DUST_DAMAGE_RATIO = "DataDustDamageRatio";
    private static final EntityDataAccessor<ItemStack> DATA_SABER_STACK = SynchedEntityData.defineId(ThrownLightSaberEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(ThrownLightSaberEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ID_FOIL = SynchedEntityData.defineId(ThrownLightSaberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ID_HOMING = SynchedEntityData.defineId(ThrownLightSaberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ID_SABER_ENERGY_CARD_COUNT = SynchedEntityData.defineId(ThrownLightSaberEntity.class, EntityDataSerializers.INT);

    private boolean dealtDamage;
    public int clientSideReturnTridentTickCount;
    private ItemStack saberStack = ItemStack.EMPTY;
    private ItemStack weaponStack = ItemStack.EMPTY;
    private float dataDustDamageRatio = BASE_DATA_DUST_DAMAGE_RATIO;

    public ThrownLightSaberEntity(EntityType<? extends ThrownLightSaberEntity> entityType, Level level) {
        super(entityType, level);
        this.pickup = Pickup.ALLOWED;
    }

    public ThrownLightSaberEntity(Level level, LivingEntity shooter, ItemStack saberStack) {
        super(DEEntities.THROWN_LIGHT_SABER.get(), shooter, level, saberStack.copyWithCount(1), saberStack.copyWithCount(1));
        this.setSaberStack(saberStack);
        this.pickup = Pickup.ALLOWED;
        this.entityData.set(ID_LOYALTY, this.getLoyaltyFromItem(saberStack));
        this.entityData.set(ID_FOIL, saberStack.hasFoil());
    }

    public ThrownLightSaberEntity(Level level, double x, double y, double z, ItemStack saberStack) {
        super(DEEntities.THROWN_LIGHT_SABER.get(), x, y, z, level, saberStack.copyWithCount(1), saberStack.copyWithCount(1));
        this.setSaberStack(saberStack);
        this.pickup = Pickup.ALLOWED;
        this.entityData.set(ID_LOYALTY, this.getLoyaltyFromItem(saberStack));
        this.entityData.set(ID_FOIL, saberStack.hasFoil());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SABER_STACK, ItemStack.EMPTY);
        builder.define(ID_LOYALTY, (byte) 0);
        builder.define(ID_FOIL, false);
        builder.define(ID_HOMING, false);
        builder.define(ID_SABER_ENERGY_CARD_COUNT, 0);
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        if (!this.level().isClientSide && this.isHoming() && !this.dealtDamage && !this.isNoPhysics()) {
            LivingEntity homingTarget = this.findNearestHomingTarget();
            if (homingTarget != null) {
                this.applyHoming(homingTarget);
                if (this.tryForceHomingHit(homingTarget)) {
                    return;
                }
            }
        }

        Entity owner = this.getOwner();
        int loyalty = this.entityData.get(ID_LOYALTY);
        if (loyalty > 0 && (this.dealtDamage || this.isNoPhysics()) && owner != null) {
            if (!this.isAcceptibleReturnOwner()) {
                if (!this.level().isClientSide && this.pickup == Pickup.ALLOWED) {
                    this.spawnAtLocation(this.getPickupItem(), 0.1F);
                }
                this.discard();
                return;
            }

            this.setNoPhysics(true);
            Vec3 toOwner = owner.getEyePosition().subtract(this.position());
            this.setPosRaw(this.getX(), this.getY() + toOwner.y * 0.015D * loyalty, this.getZ());
            if (this.level().isClientSide) {
                this.yOld = this.getY();
            }

            double acceleration = 0.05D * loyalty;
            this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(toOwner.normalize().scale(acceleration)));
            if (this.clientSideReturnTridentTickCount == 0) {
                this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
            }

            ++this.clientSideReturnTridentTickCount;
        }

        super.tick();
    }

    @Override
    protected EntityHitResult findHitEntity(Vec3 startVec, Vec3 endVec) {
        return this.dealtDamage ? null : super.findHitEntity(startVec, endVec);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        LivingEntity livingTarget = this.resolveLivingTarget(target);
        float damage = this.getBaseThrownDamage() * this.getSpeedDamageMultiplier();
        Entity owner = this.getOwner();
        DamageSource damageSource = owner instanceof LivingEntity livingOwner ? this.damageSources().mobProjectile(this, livingOwner) : this.damageSources().thrown(this, owner);
        if (this.level() instanceof ServerLevel serverLevel) {
            damage = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(),
                    livingTarget != null ? livingTarget : target, damageSource, damage);
        }

        this.dealtDamage = true;
        this.resetTargetInvulnerability(target);
        if (livingTarget != null && livingTarget != target) {
            this.resetTargetInvulnerability(livingTarget);
        }

        boolean damaged = target.hurt(damageSource, damage);
        if (!damaged && livingTarget != null && livingTarget != target) {
            damaged = livingTarget.hurt(damageSource, damage);
            target = livingTarget;
        }

        LivingEntity effectedTarget = this.resolveLivingTarget(target);
        if (effectedTarget == null && livingTarget != null) {
            effectedTarget = livingTarget;
        }

        if (target.getType() == EntityType.ENDERMAN) {
            return;
        }

        if (damaged && this.level() instanceof ServerLevel serverLevel) {
            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, damageSource, this.getWeaponItem());
        }

        if (effectedTarget != null) {
            if (damaged) {
                this.doKnockback(effectedTarget, damageSource);
                this.doPostHurtEffects(effectedTarget);
            }
            this.resetTargetInvulnerability(effectedTarget);
            this.applyDataDustDamage(effectedTarget, owner);
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected void hitBlockEnchantmentEffects(ServerLevel serverLevel, BlockHitResult blockHitResult, ItemStack stack) {
        Vec3 impact = blockHitResult.getBlockPos().clampLocationWithin(blockHitResult.getLocation());
        LivingEntity attacker = this.getOwner() instanceof LivingEntity livingOwner ? livingOwner : null;
        EnchantmentHelper.onHitBlock(
                serverLevel,
                stack,
                attacker,
                this,
                (EquipmentSlot) null,
                impact,
                serverLevel.getBlockState(blockHitResult.getBlockPos()),
                ignored -> {});
    }

    @Override
    public ItemStack getItem() {
        ItemStack stack = this.getEntityData().get(DATA_SABER_STACK);
        if (stack.isEmpty()) {
            stack = this.getWeaponItem();
        }
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public ItemStack getWeaponItem() {
        ItemStack stack = this.getPickupItemStackOrigin();
        if (stack == null || stack.isEmpty()) {
            return this.saberStack;
        }
        return stack;
    }

    public boolean isFoil() {
        return this.entityData.get(ID_FOIL);
    }

    public void setHoming(boolean homing) {
        this.entityData.set(ID_HOMING, homing);
    }

    public boolean isHoming() {
        return this.entityData.get(ID_HOMING);
    }

    public void setWeaponStack(ItemStack stack) {
        boolean dimensionsChanged = this.shouldUseExpandedDimensions() != shouldUseExpandedDimensions(stack, this.getSaberEnergyCardCount(stack));
        this.weaponStack = stack == null ? ItemStack.EMPTY : stack.copy();
        int saberEnergyCardCount = this.getSaberEnergyCardCount(this.weaponStack);
        this.entityData.set(ID_SABER_ENERGY_CARD_COUNT, saberEnergyCardCount);
        if (dimensionsChanged) {
            this.refreshDimensions();
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        ItemStack stack = this.getPickupItemStackOrigin();
        if (stack == null || stack.isEmpty()) {
            stack = this.saberStack;
        }
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return stack.copy();
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    public boolean isEmbedded() {
        return this.inGround || this.inGroundTime > 0;
    }

    public int getEmbeddedTime() {
        return this.inGroundTime;
    }

    @Override
    public void playerTouch(Player player) {
        if (this.ownedBy(player) || this.getOwner() == null) {
            super.playerTouch(player);
        }
    }

    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player) || this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem());
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_DEALT_DAMAGE, this.dealtDamage);
        tag.putBoolean("Homing", this.isHoming());
        tag.putInt("SaberEnergyCardCount", this.getSaberEnergyCardCount());
        tag.putFloat(TAG_DATA_DUST_DAMAGE_RATIO, this.dataDustDamageRatio);
        if (!this.weaponStack.isEmpty()) {
            tag.put("WeaponStack", this.weaponStack.save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.dealtDamage = tag.getBoolean(TAG_DEALT_DAMAGE);
        ItemStack origin = this.getPickupItemStackOrigin();
        this.setSaberStack(origin == null ? ItemStack.EMPTY : origin);
        this.entityData.set(ID_LOYALTY, this.getLoyaltyFromItem(this.saberStack));
        this.entityData.set(ID_FOIL, !this.saberStack.isEmpty() && this.saberStack.hasFoil());
        this.entityData.set(ID_HOMING, tag.getBoolean("Homing"));
        this.entityData.set(ID_SABER_ENERGY_CARD_COUNT, Math.max(0, tag.getInt("SaberEnergyCardCount")));
        this.dataDustDamageRatio = Math.max(0.0F, tag.getFloat(TAG_DATA_DUST_DAMAGE_RATIO));
        if (tag.contains("WeaponStack", 10)) {
            this.weaponStack = ItemStack.parse(this.registryAccess(), tag.getCompound("WeaponStack"))
                    .orElse(ItemStack.EMPTY);
        } else {
            this.weaponStack = ItemStack.EMPTY;
        }
        if (!this.weaponStack.isEmpty()) {
            this.entityData.set(ID_SABER_ENERGY_CARD_COUNT, this.getSaberEnergyCardCount(this.weaponStack));
        }
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDimensions(pose);
        return this.shouldUseExpandedDimensions() ? dimensions.scale(2.0F) : dimensions;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ID_SABER_ENERGY_CARD_COUNT.equals(key) || DATA_SABER_STACK.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    public void tickDespawn() {
        if (this.inGroundTime >= DESPAWN_TICKS) {
            this.discard();
        }
    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }

    private byte getLoyaltyFromItem(ItemStack stack) {
        if (this.level() instanceof ServerLevel serverLevel) {
            return (byte) Mth.clamp(EnchantmentHelper.getTridentReturnToOwnerAcceleration(serverLevel, stack, this), 0, 127);
        }
        return 0;
    }

    private void setSaberStack(ItemStack stack) {
        this.saberStack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        this.getEntityData().set(DATA_SABER_STACK, this.saberStack.copy());
        this.setPickupItemStack(this.saberStack.copy());
    }

    private float getBaseThrownDamage() {
        ItemStack weapon = this.getWeaponItem();
        if (weapon.isEmpty()) {
            return 1.0F;
        }

        final double playerBaseDamage = 1.0D;
        final double[] addValue = { 0.0D };
        final double[] addMultipliedBase = { 0.0D };
        final double[] addMultipliedTotal = { 0.0D };

        weapon.forEachModifier(EquipmentSlot.MAINHAND, (Holder<Attribute> attribute, AttributeModifier modifier) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
                return;
            }

            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                addValue[0] += modifier.amount();
            } else if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                addMultipliedBase[0] += modifier.amount();
            } else if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                addMultipliedTotal[0] += modifier.amount();
            }
        });

        double damage = playerBaseDamage + addValue[0];
        damage += playerBaseDamage * addMultipliedBase[0];
        damage *= 1.0D + addMultipliedTotal[0];
        return Math.max(0.0F, ((float) damage + 1.0F) * this.getSaberEnergyDamageMultiplier());
    }

    private boolean isAcceptibleReturnOwner() {
        Entity owner = this.getOwner();
        return owner != null && owner.isAlive() && (!(owner instanceof ServerPlayer serverPlayer) || !serverPlayer.isSpectator());
    }

    private int getSaberEnergyCardCount() {
        return Math.max(0, this.entityData.get(ID_SABER_ENERGY_CARD_COUNT));
    }

    private int getSaberEnergyCardCount(ItemStack stack) {
        return stack.isEmpty() ? 0 : Math.max(0, UpgradeInventories.forItem(stack, 6)
                .getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()));
    }

    private boolean shouldUseExpandedDimensions() {
        return shouldUseExpandedDimensions(this.getExpansionSourceStack(), this.getSaberEnergyCardCount());
    }

    private static boolean shouldUseExpandedDimensions(ItemStack stack, int saberEnergyCardCount) {
        return !stack.isEmpty() && stack.is(DEItems.DATA_SANCTIFIER.get()) && saberEnergyCardCount > 0;
    }

    private ItemStack getExpansionSourceStack() {
        if (!this.weaponStack.isEmpty()) {
            return this.weaponStack;
        }
        ItemStack stack = this.getEntityData().get(DATA_SABER_STACK);
        if (!stack.isEmpty()) {
            return stack;
        }
        ItemStack pickupStack = this.getPickupItemStackOrigin();
        return pickupStack == null ? ItemStack.EMPTY : pickupStack;
    }

    private float getSaberEnergyDamageMultiplier() {
        int cardCount = this.getSaberEnergyCardCount();
        return Math.max(1.0F, cardCount * 2.0F);
    }

    private float getSpeedDamageMultiplier() {
        return Math.max(0.0F, (float) this.getDeltaMovement().length());
    }

    private void applyDataDustDamage(LivingEntity target, @Nullable Entity owner) {
        float damage = target.getMaxHealth() * this.getDataDustDamageRatio();
        if (damage <= 0.0F) {
            return;
        }

        DamageSource damageSource = owner instanceof Player player ? this.damageSources().playerAttack(player) : owner instanceof LivingEntity livingOwner ? this.damageSources().mobAttack(livingOwner) : this.damageSources().magic();
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        target.hurtDuration = 0;
        target.lastHurt = 0.0F;
        target.setHealth(Math.max(0.0F, target.getHealth() - damage));
        target.hurt(damageSource, 0.0F);
        if (target.getHealth() <= 0.0F) {
            target.die(damageSource);
        }
    }

    private void resetTargetInvulnerability(Entity target) {
        target.invulnerableTime = 0;
        if (target instanceof LivingEntity livingTarget) {
            livingTarget.hurtTime = 0;
            livingTarget.hurtDuration = 0;
            livingTarget.lastHurt = 0.0F;
        }
    }

    private float getDataDustDamageRatio() {
        return BASE_DATA_DUST_DAMAGE_RATIO + this.getSaberEnergyCardCount() * DATA_DUST_DAMAGE_RATIO_PER_CARD;
    }

    @Nullable
    private LivingEntity resolveLivingTarget(Entity target) {
        if (target instanceof LivingEntity livingTarget) {
            return livingTarget;
        }
        if (target instanceof PartEntity<?> partEntity && partEntity.getParent() instanceof LivingEntity livingParent) {
            return livingParent;
        }
        return null;
    }

    private void applyHoming(LivingEntity target) {
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-6D) {
            return;
        }

        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(this.position());
        double distance = toTarget.length();
        if (toTarget.lengthSqr() < 1.0E-6D) {
            return;
        }

        Vec3 currentDirection = velocity.normalize();
        Vec3 desiredDirection = toTarget.normalize();
        double alignment = Mth.clamp((1.0D - currentDirection.dot(desiredDirection)) * 0.5D, 0.0D, 1.0D);
        double closeRangeBoost = distance <= HOMING_CLOSE_RANGE ? (HOMING_CLOSE_RANGE - distance) / HOMING_CLOSE_RANGE : 0.0D;
        double homingStrength = Mth.clamp(HOMING_STRENGTH + alignment * 0.28D + closeRangeBoost * 0.22D,
                HOMING_STRENGTH, HOMING_MAX_STRENGTH);
        Vec3 adjustedDirection = currentDirection.scale(1.0D - homingStrength).add(desiredDirection.scale(homingStrength));
        if (adjustedDirection.lengthSqr() < 1.0E-6D) {
            return;
        }

        this.setDeltaMovement(adjustedDirection.normalize().scale(speed));
        this.hasImpulse = true;
    }

    @Nullable
    private LivingEntity findNearestHomingTarget() {
        Entity owner = this.getOwner();
        return this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(HOMING_RANGE),
                entity -> entity.isAlive() && !entity.isRemoved() && !(entity instanceof Player) && !(entity instanceof ServerPlayer) && entity != owner)
                .stream()
                .min((left, right) -> Double.compare(this.distanceToSqr(left), this.distanceToSqr(right)))
                .orElse(null);
    }

    private boolean tryForceHomingHit(LivingEntity target) {
        Vec3 start = this.position();
        Vec3 end = start.add(this.getDeltaMovement());
        AABB searchBox = this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(HOMING_HIT_MARGIN);
        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(this.level(), this, start, end, searchBox,
                entity -> entity == target && this.canHitEntity(entity));
        if (hitResult == null) {
            return false;
        }

        this.setPos(hitResult.getLocation());
        this.onHitEntity(hitResult);
        return true;
    }
}
