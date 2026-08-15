package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.effect.RadixLossEffectLogic;
import com.fish_dan_.data_energistics.item.powered.PoweredEnergyItem;
import com.fish_dan_.data_energistics.registry.DEEntities;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class LightBladeChargeEntity extends ThrowableItemProjectile {

    private static final int LIFETIME_TICKS = 120;
    private static final int RADIX_LOSS_DURATION_TICKS = 30;
    private static final double MAX_TRAVEL_DISTANCE = 128.0D;
    private static final float SIZE_SCALE = 2.0F;
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(LightBladeChargeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(LightBladeChargeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<ItemStack> DATA_WEAPON_STACK = SynchedEntityData.defineId(LightBladeChargeEntity.class, EntityDataSerializers.ITEM_STACK);

    private double traveledDistance;

    public LightBladeChargeEntity(EntityType<? extends LightBladeChargeEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public LightBladeChargeEntity(Level level, LivingEntity owner, int color, float damage) {
        super(DEEntities.LIGHT_BLADE_CHARGE.get(), owner, level);
        this.setNoGravity(true);
        this.setColor(color);
        this.setDamage(damage);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COLOR, 0xFFFFFF);
        builder.define(DATA_DAMAGE, 1.0F);
        builder.define(DATA_WEAPON_STACK, ItemStack.EMPTY);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AMETHYST_SHARD;
    }

    @Override
    public void tick() {
        Vec3 previousPosition = this.position();
        super.tick();
        this.setNoGravity(true);

        if (this.isRemoved()) {
            return;
        }

        this.traveledDistance += previousPosition.distanceTo(this.position());
        if (this.tickCount >= LIFETIME_TICKS || this.traveledDistance >= MAX_TRAVEL_DISTANCE) {
            this.discardWithEffects();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource damageSource = owner instanceof Player player ? this.damageSources().playerAttack(player) : owner instanceof LivingEntity livingOwner ? this.damageSources().mobAttack(livingOwner) : this.damageSources().magic();
        float damage = this.getDamageAmount();

        if (this.level() instanceof ServerLevel serverLevel) {
            damage = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponStack(), target, damageSource, damage);
        }

        this.resetTargetInvulnerability(target);
        if (target.hurt(damageSource, damage) && this.level() instanceof ServerLevel serverLevel) {
            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, damageSource, this.getWeaponStack());
            if (target instanceof LivingEntity livingTarget && this.canApplyRadixLoss()) {
                RadixLossEffectLogic.applyOrBurst(livingTarget, RADIX_LOSS_DURATION_TICKS, owner, damage);
            }
        }
        this.discardWithEffects();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.discardWithEffects();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }

        Entity owner = this.getOwner();
        return target != owner && !(target instanceof Player) && !(target instanceof ServerPlayer);
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(SIZE_SCALE);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Color", this.getColor());
        tag.putFloat("Damage", this.getDamageAmount());
        tag.putDouble("TraveledDistance", this.traveledDistance);
        if (!this.getWeaponStack().isEmpty()) {
            tag.put("WeaponStack", this.getWeaponStack().save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setColor(tag.getInt("Color"));
        this.setDamage(tag.getFloat("Damage"));
        this.traveledDistance = tag.getDouble("TraveledDistance");
        if (tag.contains("WeaponStack", 10)) {
            this.setWeaponStack(ItemStack.parse(this.registryAccess(), tag.getCompound("WeaponStack"))
                    .orElse(ItemStack.EMPTY));
        }
    }

    public void setColor(int color) {
        this.entityData.set(DATA_COLOR, color & 0xFFFFFF);
    }

    public int getColor() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setDamage(float damage) {
        this.entityData.set(DATA_DAMAGE, Math.max(0.0F, damage));
    }

    public float getDamageAmount() {
        return this.entityData.get(DATA_DAMAGE);
    }

    public void setWeaponStack(ItemStack stack) {
        this.entityData.set(DATA_WEAPON_STACK, stack == null ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public ItemStack getWeaponStack() {
        return this.entityData.get(DATA_WEAPON_STACK);
    }

    private boolean canApplyRadixLoss() {
        ItemStack weaponStack = this.getWeaponStack();
        return weaponStack.is(DEItems.DATA_LIGHT_SABER.get()) && weaponStack.getItem() instanceof PoweredEnergyItem poweredEnergyItem && poweredEnergyItem.getSaberEnergyCardCount(weaponStack) > 0;
    }

    private void resetTargetInvulnerability(Entity target) {
        target.invulnerableTime = 0;
        if (target instanceof LivingEntity livingTarget) {
            livingTarget.hurtTime = 0;
            livingTarget.hurtDuration = 0;
            livingTarget.lastHurt = 0.0F;
        }
    }

    private void discardWithEffects() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_HIT,
                    this.getSoundSource(), 0.35F, 1.8F);
        }
        this.discard();
    }
}
