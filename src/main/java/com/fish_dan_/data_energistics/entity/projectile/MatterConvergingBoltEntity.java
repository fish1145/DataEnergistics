package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.effect.ChromaticGlow;
import com.fish_dan_.data_energistics.entity.projectile.cannon.CannonShot;
import com.fish_dan_.data_energistics.entity.projectile.cannon.ElementalGrenade;
import com.fish_dan_.data_energistics.entity.projectile.cannon.GrenadePayload;
import com.fish_dan_.data_energistics.entity.projectile.cannon.WeaponDamage;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonBallistics;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.AmmunitionRules;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEEntities;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.ids.AEComponents;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.items.misc.PaintBallItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Setter;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class MatterConvergingBoltEntity extends ThrowableItemProjectile {

    private static final float MATTER_BALL_DAMAGE = 10.0F;
    private static final float SINGULARITY_DAMAGE = 25.0F;
    private static final float DEFAULT_DATA_DUST_DAMAGE_RATIO = 0.01F;
    private static final float DATA_DUST_BASE_DAMAGE = 10.0F;
    private static final double SPECIAL_LIGHT_SABER_ENERGY = 20_000.0D;
    private static final float CRIT_DAMAGE_BONUS = 1.5F;
    private static final double MAX_TRAVEL_DISTANCE = 256.0D;
    private static final double HOMING_RANGE = 24.0D;
    private static final double HOMING_STRENGTH = 0.35D;
    private static final double HOMING_MAX_STRENGTH = 0.85D;
    private static final double HOMING_CLOSE_RANGE = 8.0D;
    private static final double HOMING_HIT_MARGIN = 0.75D;
    private static final DustParticleOptions SINGULARITY_TRAIL_PARTICLE = new DustParticleOptions(new Vector3f(0.48F, 0.24F, 1.0F), 1.2F);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(MatterConvergingBoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PIERCE_LEVEL = SynchedEntityData.defineId(MatterConvergingBoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HOMING = SynchedEntityData.defineId(MatterConvergingBoltEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SABER_ENERGY_CARD_COUNT = SynchedEntityData.defineId(MatterConvergingBoltEntity.class, EntityDataSerializers.INT);
    private static final String TAG_DATA_DUST_DAMAGE_RATIO = "DataDustDamageRatio";
    private static final String TAG_CONSUMED_PIERCE_COUNT = "ConsumedPierceCount";
    private static final EntityDataAccessor<Integer> DATA_FIRING_MODE = SynchedEntityData.defineId(MatterConvergingBoltEntity.class, EntityDataSerializers.INT);

    private double traveledDistance;
    private ItemStack weaponStack = ItemStack.EMPTY;
    private final IntSet piercedEntityIds = new IntOpenHashSet();
    private int consumedPierceCount;
    @Setter
    private boolean critical;
    private CannonShot cannonShot = CannonShot.CROSSBOW;
    private boolean modernEffects;
    private float fragmentDamage;
    private int singularityTicks;

    public MatterConvergingBoltEntity(EntityType<? extends MatterConvergingBoltEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public MatterConvergingBoltEntity(Level level, LivingEntity shooter, ItemStack ammo) {
        super(DEEntities.MATTER_CONVERGING_BOLT.get(), shooter, level);
        this.setNoGravity(true);
        this.setItem(ammo);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COLOR, -1);
        builder.define(DATA_PIERCE_LEVEL, 0);
        builder.define(DATA_HOMING, false);
        builder.define(DATA_SABER_ENERGY_CARD_COUNT, 0);
        builder.define(DATA_FIRING_MODE, MatterConvergingCrossbowMode.CROSSBOW.id());
    }

    @Override
    public void tick() {
        if (this.singularityTicks > 0) {
            this.tickSingularity();
            return;
        }
        Vec3 previousPosition = this.position();
        if (!this.level().isClientSide && this.isHoming()) {
            this.applyHoming();
            if (this.tryForceHomingHit()) {
                return;
            }
        }
        super.tick();
        this.setNoGravity(this.firingMode() != MatterConvergingCrossbowMode.GRENADE);

        if (!this.isRemoved()) {
            Vec3 currentPosition = this.position();
            this.spawnLaunchParticles(previousPosition, currentPosition);
            this.spawnTrailParticles(previousPosition);
            this.traveledDistance += previousPosition.distanceTo(currentPosition);
            if (this.traveledDistance >= MAX_TRAVEL_DISTANCE) {
                this.discardWithEffects();
            }
        }
    }

    @Override
    protected double getDefaultGravity() {
        return this.firingMode() == MatterConvergingCrossbowMode.GRENADE ? CannonBallistics.GRENADE_GRAVITY : 0.0D;
    }

    public void configureCannonShot(CannonShot shot) {
        this.cannonShot = shot;
        this.entityData.set(DATA_FIRING_MODE, shot.mode().id());
        this.setNoGravity(shot.mode() != MatterConvergingCrossbowMode.GRENADE);
        if (shot.mode() != MatterConvergingCrossbowMode.CROSSBOW) this.setHoming(false);
    }

    /** Returns this projectile's synchronized firing mode for gameplay and client rendering, including after reload. */
    public MatterConvergingCrossbowMode firingMode() {
        return MatterConvergingCrossbowMode.fromId(this.entityData.get(DATA_FIRING_MODE));
    }

    @Override
    public void setItem(ItemStack stack) {
        super.setItem(stack);
        this.getEntityData().set(DATA_COLOR, this.resolveColor(stack));
    }

    public void setWeaponStack(ItemStack stack) {
        this.modernEffects = true;
        this.weaponStack = stack.copy();
        this.getEntityData().set(DATA_SABER_ENERGY_CARD_COUNT, this.getSaberEnergyCardCount(stack));
    }

    public void setPierceLevel(int pierceLevel) {
        this.getEntityData().set(DATA_PIERCE_LEVEL, Math.max(0, pierceLevel));
    }

    public int getPierceLevel() {
        return this.getEntityData().get(DATA_PIERCE_LEVEL);
    }

    public void setHoming(boolean homing) {
        this.getEntityData().set(DATA_HOMING, homing);
    }

    public boolean isHoming() {
        return this.getEntityData().get(DATA_HOMING);
    }

    @Override
    protected Item getDefaultItem() {
        return AEItems.MATTER_BALL.asItem();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.cannonShot.save(tag);
        tag.putBoolean("ModernEffects", this.modernEffects);
        tag.putFloat("FragmentDamage", this.fragmentDamage);
        tag.putInt("SingularityTicks", this.singularityTicks);
        tag.putDouble("TraveledDistance", this.traveledDistance);
        tag.putInt("BoltColor", this.getColor());
        tag.putInt("PierceLevel", this.getPierceLevel());
        tag.putInt(TAG_CONSUMED_PIERCE_COUNT, this.consumedPierceCount);
        tag.putBoolean("Homing", this.isHoming());
        tag.putBoolean("Critical", this.critical);
        tag.putInt("SaberEnergyCardCount", this.getSaberEnergyCardCount());
        if (!this.weaponStack.isEmpty()) {
            tag.put("WeaponStack", this.weaponStack.save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.modernEffects = tag.getBoolean("ModernEffects");
        this.fragmentDamage = Math.max(0, tag.getFloat("FragmentDamage"));
        this.singularityTicks = Math.clamp(tag.getInt("SingularityTicks"), 0, 10);
        this.traveledDistance = tag.getDouble("TraveledDistance");
        this.getEntityData().set(DATA_COLOR, tag.getInt("BoltColor"));
        this.getEntityData().set(DATA_PIERCE_LEVEL, tag.getInt("PierceLevel"));
        this.consumedPierceCount = Math.max(0, tag.getInt(TAG_CONSUMED_PIERCE_COUNT));
        this.getEntityData().set(DATA_HOMING, tag.getBoolean("Homing"));
        this.getEntityData().set(DATA_SABER_ENERGY_CARD_COUNT, Math.max(0, tag.getInt("SaberEnergyCardCount")));
        this.critical = tag.getBoolean("Critical");
        if (tag.contains("WeaponStack", 10)) {
            this.weaponStack = ItemStack.parse(this.registryAccess(), tag.getCompound("WeaponStack"))
                    .orElse(ItemStack.EMPTY);
        } else {
            this.weaponStack = ItemStack.EMPTY;
        }
        if (!this.weaponStack.isEmpty()) {
            this.getEntityData().set(DATA_SABER_ENERGY_CARD_COUNT, this.getSaberEnergyCardCount(this.weaponStack));
        }
        this.configureCannonShot(CannonShot.load(tag));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.detonatePayload(result)) return;
        Entity owner = this.getOwner();
        Entity target = result.getEntity();
        LivingEntity livingTarget = this.resolveLivingTarget(target);
        if (this.modernEffects && this.getItem().is(DEItems.SINGULARITY_BLOCK.get())) {
            if (livingTarget != null) {
                var cube = AmmunitionRules.cube(this.focusingCards());
                WeaponDamage.hurt(livingTarget, WeaponDamage.source(livingTarget, owner), this.fragmentDamage > 0 ? this.fragmentDamage : cube.damage());
                if (this.fragmentDamage == 0) this.splitCube(result.getLocation(), cube);
            }
            this.discardWithEffects();
            return;
        }
        if (this.isDataDustAmmo() && livingTarget != null) {
            DamageSource damageSource = owner instanceof LivingEntity livingOwner ? this.damageSources().mobProjectile(this, livingOwner) : this.damageSources().thrown(this, owner);
            float baseDamage = this.getDataDustBaseDamage();
            this.resetTargetInvulnerability(target);
            if (livingTarget != target) {
                this.resetTargetInvulnerability(livingTarget);
            }
            if (baseDamage > 0.0F) {
                livingTarget.hurt(damageSource, baseDamage);
                this.resetTargetInvulnerability(livingTarget);
            }
            this.applyDataDustDamage(livingTarget, owner);
            this.discardWithEffects();
            return;
        }
        DamageSource damageSource = owner instanceof LivingEntity livingOwner ? this.damageSources().mobProjectile(this, livingOwner) : this.damageSources().thrown(this, owner);
        float damage = this.getImpactDamage();
        if (this.level() instanceof ServerLevel serverLevel && !this.weaponStack.isEmpty()) {
            damage = EnchantmentHelper.modifyDamage(serverLevel, this.weaponStack, target, damageSource, damage);
        }
        damage *= this.cannonShot.damageScale();

        this.resetTargetInvulnerability(target);
        if (livingTarget != null && livingTarget != target) {
            this.resetTargetInvulnerability(livingTarget);
        }
        boolean wasAlive = target.isAlive();
        boolean damaged = target.hurt(damageSource, damage);
        if (!damaged && livingTarget != null && livingTarget != target) {
            wasAlive = livingTarget.isAlive();
            damaged = livingTarget.hurt(damageSource, damage);
            target = livingTarget;
        }
        if (this.modernEffects && livingTarget != null && damaged && this.getItem().getItem() instanceof PaintBallItem) {
            ChromaticGlow.apply(livingTarget, this.getColor());
        }
        if (this.modernEffects && this.isSingularityAmmo()) {
            this.startSingularity(result.getLocation());
            return;
        }
        if (this.shouldContinuePiercing(target, damaged, wasAlive)) {
            return;
        }

        this.discardWithEffects();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.detonatePayload(result)) return;
        if (this.modernEffects && this.isSingularityAmmo()) {
            this.startSingularity(result.getLocation());
            return;
        }
        super.onHitBlock(result);
        this.discardWithEffects();
    }

    private boolean detonatePayload(HitResult result) {
        if (this.modernEffects && this.firingMode() == MatterConvergingCrossbowMode.GRENADE && ElementalGrenade.accepts(this.getItem())) {
            if (!this.isRemoved() && this.level() instanceof ServerLevel serverLevel) {
                this.discard();
                ElementalGrenade.detonate(serverLevel, this.getItem(), result.getLocation(),
                        this.getOwner() instanceof LivingEntity living ? living : null, this.focusingCards());
            }
            return true;
        }
        if (this.firingMode() != MatterConvergingCrossbowMode.GRENADE || !GrenadePayload.isExplosive(this.getItem())) return false;
        if (!this.isRemoved() && this.level() instanceof ServerLevel serverLevel) {
            this.discard();
            Vec3 impact = result.getLocation();
            BlockHitResult blockHit = result instanceof BlockHitResult hit ? hit : null;
            if (blockHit != null) {
                impact = impact.add(Vec3.atLowerCornerOf(blockHit.getDirection().getNormal()).scale(0.001D));
            }
            GrenadePayload.detonate(serverLevel, this.getItem(), impact, blockHit == null ? null : blockHit.getDirection(),
                    this.getOwner() instanceof LivingEntity livingOwner ? livingOwner : null);
        }
        return true;
    }

    private boolean isSingularityAmmo() {
        return this.getItem().is(AEItems.SINGULARITY.asItem());
    }

    private int focusingCards() {
        return Math.clamp(this.getSaberEnergyCardCount(), 0, 2);
    }

    /** Legacy preloaded projectiles retain their original one-shot payload behavior. */
    public void setModernEffects(boolean modernEffects) {
        this.modernEffects = modernEffects;
    }

    private void splitCube(Vec3 center, AmmunitionRules.Cube cube) {
        if (!(this.level() instanceof ServerLevel level)) return;
        for (int i = 0; i < cube.fragments(); i++) {
            double angle = 2 * Math.PI * i / cube.fragments();
            MatterConvergingBoltEntity fragment = new MatterConvergingBoltEntity(DEEntities.MATTER_CONVERGING_BOLT.get(), level);
            fragment.setOwner(this.getOwner());
            fragment.setItem(this.getItem().copyWithCount(1));
            fragment.setWeaponStack(this.weaponStack);
            fragment.fragmentDamage = cube.fragmentDamage();
            fragment.setHoming(this.isHoming());
            fragment.setPos(center.add(Math.cos(angle) * 0.35, 0.15, Math.sin(angle) * 0.35));
            fragment.setDeltaMovement(new Vec3(Math.cos(angle), 0.15, Math.sin(angle)).normalize().scale(1.5));
            level.addFreshEntity(fragment);
        }
    }

    private void startSingularity(Vec3 center) {
        this.setPos(center);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.singularityTicks = 10;
    }

    private void tickSingularity() {
        if (!(this.level() instanceof ServerLevel level)) return;
        int cards = this.focusingCards();
        var targets = level.getEntitiesOfClass(LivingEntity.class, ElementalGrenade.area(this.position(), cards == 0 ? 3 : 5),
                target -> target.isAlive() && target != this.getOwner());
        for (LivingEntity target : targets) {
            Vec3 pull = this.position().subtract(target.getBoundingBox().getCenter()).scale(0.25);
            if (pull.lengthSqr() > 0.36) pull = pull.normalize().scale(0.6);
            target.setDeltaMovement(pull);
            target.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 8, 0.6, 0.6, 0.6, 0.05);
        if (--this.singularityTicks == 0) {
            if (cards == 2) {
                for (LivingEntity target : targets) WeaponDamage.hurt(target, WeaponDamage.source(target, this.getOwner()), target.getMaxHealth() * 0.15F);
                level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
            }
            this.discardWithEffects();
        }
    }

    private boolean isDataDustAmmo() {
        return this.getItem().is(DEItems.DATA_LIGHT_SABER.get()) && Math.abs(this.getItem().getOrDefault(AEComponents.STORED_ENERGY, 0.0D) - SPECIAL_LIGHT_SABER_ENERGY) < 1.0E-4D;
    }

    public int getColor() {
        return this.getEntityData().get(DATA_COLOR);
    }

    private float getDamageForAmmo() {
        float baseDamage = this.isSingularityAmmo() ? SINGULARITY_DAMAGE : MATTER_BALL_DAMAGE;
        return baseDamage * this.getSaberEnergyDamageMultiplier();
    }

    private float getImpactDamage() {
        float speed = this.cannonShot.damageSpeed((float) this.getDeltaMovement().length());
        float damage = this.getDamageForAmmo() * speed;
        if (this.critical) {
            damage *= CRIT_DAMAGE_BONUS;
        }
        return Mth.clamp(damage, 0.0F, Float.MAX_VALUE);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        return !this.piercedEntityIds.contains(target.getId());
    }

    private boolean shouldContinuePiercing(Entity target, boolean damaged, boolean wasAlive) {
        if (this.getPierceLevel() <= 0 || !damaged || (!target.isAlive() && !wasAlive)) {
            return false;
        }

        if (this.piercedEntityIds.add(target.getId())) {
            this.consumedPierceCount++;
        }
        return this.consumedPierceCount <= this.getPierceLevel();
    }

    private void spawnTrailParticles(Vec3 previousPosition) {
        if (!(this.level() instanceof ServerLevel serverLevel) || !this.isSingularityAmmo()) {
            return;
        }

        Vec3 currentPosition = this.position();
        Vec3 delta = currentPosition.subtract(previousPosition);
        if (delta.lengthSqr() < 1.0E-6D) {
            return;
        }

        Vec3 direction = delta.normalize();
        Vec3 trailOrigin = currentPosition.subtract(direction.scale(0.6D));
        Vec3 axisA = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (axisA.lengthSqr() < 1.0E-6D) {
            axisA = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        axisA = axisA.normalize();
        Vec3 axisB = direction.cross(axisA).normalize();

        double radius = 0.18D;
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D * i) / 8.0D;
            Vec3 offset = axisA.scale(Math.cos(angle) * radius).add(axisB.scale(Math.sin(angle) * radius));
            Vec3 point = trailOrigin.add(offset);
            serverLevel.sendParticles(SINGULARITY_TRAIL_PARTICLE,
                    point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        serverLevel.sendParticles(SINGULARITY_TRAIL_PARTICLE,
                trailOrigin.x, trailOrigin.y, trailOrigin.z,
                3, 0.04D, 0.04D, 0.04D, 0.01D);
        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                trailOrigin.x, trailOrigin.y, trailOrigin.z,
                2, 0.03D, 0.03D, 0.03D, 0.0D);
    }

    private void spawnLaunchParticles(Vec3 previousPosition, Vec3 currentPosition) {
        if (!(this.level() instanceof ServerLevel serverLevel) || !this.isDataDustAmmo() || this.tickCount != 1) {
            return;
        }

        Vec3 delta = currentPosition.subtract(previousPosition);
        Vec3 origin = delta.lengthSqr() > 1.0E-6D ? previousPosition.add(delta.normalize().scale(0.35D)) : currentPosition;
        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                origin.x, origin.y, origin.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private int resolveColor(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof PaintBallItem paintBallItem) {
            return paintBallItem.getColor().mediumVariant;
        }
        if (stack.is(AEItems.SINGULARITY.asItem())) {
            return 0x7A3DFF;
        }
        return 0xD8D8D8;
    }

    private void applyHoming() {
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-6D) {
            return;
        }

        LivingEntity target = this.findNearestHomingTarget();
        if (target == null) {
            return;
        }

        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(this.position());
        double distance = toTarget.length();
        Vec3 desiredDirection = toTarget;
        if (desiredDirection.lengthSqr() < 1.0E-6D) {
            return;
        }

        Vec3 currentDirection = velocity.normalize();
        Vec3 desiredNormalized = desiredDirection.normalize();
        double alignment = Mth.clamp((1.0D - currentDirection.dot(desiredNormalized)) * 0.5D, 0.0D, 1.0D);
        double closeRangeBoost = distance <= HOMING_CLOSE_RANGE ? (HOMING_CLOSE_RANGE - distance) / HOMING_CLOSE_RANGE : 0.0D;
        double homingStrength = Mth.clamp(HOMING_STRENGTH + alignment * 0.28D + closeRangeBoost * 0.22D,
                HOMING_STRENGTH, HOMING_MAX_STRENGTH);
        Vec3 adjustedDirection = currentDirection.scale(1.0D - homingStrength)
                .add(desiredNormalized.scale(homingStrength));
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
                entity -> entity.isAlive() && !entity.isRemoved() && !(entity instanceof Player) && !(entity instanceof ServerPlayer) && entity != owner && !this.piercedEntityIds.contains(entity.getId()))
                .stream()
                .min((left, right) -> Double.compare(this.distanceToSqr(left), this.distanceToSqr(right)))
                .orElse(null);
    }

    private void applyDataDustDamage(LivingEntity target, @Nullable Entity owner) {
        float damage = target.getMaxHealth() * this.getDataDustDamageRatio() * this.cannonShot.damageScale();
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

    private float getDataDustDamageRatio() {
        Float ratio = this.getItem().get(DEDataComponents.MATTER_CONVERGING_BOLT_DAMAGE_RATIO.get());
        if (ratio != null) {
            return Mth.clamp(ratio, DEFAULT_DATA_DUST_DAMAGE_RATIO, 0.05F);
        }
        CompoundTag tag = this.getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Mth.clamp(tag.getFloat(TAG_DATA_DUST_DAMAGE_RATIO), DEFAULT_DATA_DUST_DAMAGE_RATIO, 0.05F);
    }

    private float getDataDustBaseDamage() {
        float damage = DATA_DUST_BASE_DAMAGE * this.getSaberEnergyDamageMultiplier() * this.cannonShot.damageSpeed((float) this.getDeltaMovement().length()) * this.cannonShot.damageScale();
        if (this.critical) {
            damage *= CRIT_DAMAGE_BONUS;
        }
        return Mth.clamp(damage, 0.0F, Float.MAX_VALUE);
    }

    private int getSaberEnergyCardCount() {
        return Math.max(0, this.getEntityData().get(DATA_SABER_ENERGY_CARD_COUNT));
    }

    private int getSaberEnergyCardCount(ItemStack stack) {
        return Math.max(0, UpgradeInventories.forItem(stack, 6)
                .getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()));
    }

    private float getSaberEnergyDamageMultiplier() {
        int cardCount = this.getSaberEnergyCardCount();
        return cardCount > 0 ? cardCount * 2.0F : 1.0F;
    }

    private boolean tryForceHomingHit() {
        LivingEntity target = this.findNearestHomingTarget();
        if (target == null) {
            return false;
        }

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

    private void resetTargetInvulnerability(Entity target) {
        target.invulnerableTime = 0;
        if (target instanceof LivingEntity livingTarget) {
            livingTarget.hurtTime = 0;
            livingTarget.hurtDuration = 0;
            livingTarget.lastHurt = 0.0F;
        }
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

    private void discardWithEffects() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()),
                    this.getX(), this.getY(), this.getZ(), 8, 0.08D, 0.08D, 0.08D, 0.02D);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK,
                    this.getSoundSource(), 0.35F, 1.4F);
        }
        this.discard();
    }
}
