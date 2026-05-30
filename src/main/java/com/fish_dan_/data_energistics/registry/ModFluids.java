package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModFluids {

    private static final String ENDER_TELEPORT_TAG = "data_energistics.ender_fluid_teleport_time";
    private static final String DATA_CORROSION_DEATH_MESSAGE_KEY = "death.attack.data_energistics.data_corrosion_liquid";
    private static final long ENDER_TELEPORT_COOLDOWN_TICKS = 20L;
    private static final int ENDER_TELEPORT_HALF_RANGE = 4;
    private static final int ENDER_TELEPORT_ATTEMPTS = 16;
    private static final String DATA_CORROSION_LIQUID_DAMAGE_TAG = "data_energistics.data_corrosion_liquid_fluid_damage_time";
    private static final long DATA_CORROSION_LIQUID_DAMAGE_COOLDOWN_TICKS = 20L;
    private static final float VANILLA_DRAGON_BREATH_DAMAGE = 6.0F;
    private static final float DATA_CORROSION_LIQUID_DAMAGE_MULTIPLIER = 2.0F;
    private static final float DATA_CORROSION_LIQUID_FLUID_DAMAGE = VANILLA_DRAGON_BREATH_DAMAGE * DATA_CORROSION_LIQUID_DAMAGE_MULTIPLIER;

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Data_Energistics.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, Data_Energistics.MODID);

    public static final DeferredHolder<FluidType, FluidType> ENDER_TYPE = FLUID_TYPES.register(
            "ender",
            () -> new EnderTeleportFluidType(FluidType.Properties.create()
                    .descriptionId("fluid.data_energistics.ender")
                    .canSwim(false)
                    .canDrown(false)
                    .canHydrate(false)
                    .canExtinguish(false)
                    .density(1100)
                    .viscosity(1400)
                    .temperature(300)
                    .rarity(Rarity.UNCOMMON)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
    public static final DeferredHolder<FluidType, FluidType> DATA_CORROSION_LIQUID_TYPE = FLUID_TYPES.register(
            "data_corrosion_liquid",
            () -> new DragonBreathDamageFluidType(FluidType.Properties.create()
                    .descriptionId("fluid.data_energistics.data_corrosion_liquid")
                    .canSwim(false)
                    .canDrown(false)
                    .canHydrate(false)
                    .canExtinguish(false)
                    .density(1250)
                    .viscosity(1800)
                    .temperature(450)
                    .lightLevel(4)
                    .rarity(Rarity.RARE)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, FlowingFluid> ENDER = FLUIDS.register("ender",
            () -> new BaseFlowingFluid.Source(enderProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_ENDER = FLUIDS.register("flowing_ender",
            () -> new BaseFlowingFluid.Flowing(enderProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> DATA_CORROSION_LIQUID = FLUIDS.register("data_corrosion_liquid",
            () -> new BaseFlowingFluid.Source(dataCorrosionLiquidProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_DATA_CORROSION_LIQUID = FLUIDS.register("flowing_data_corrosion_liquid",
            () -> new BaseFlowingFluid.Flowing(dataCorrosionLiquidProperties()));

    public static final DeferredBlock<LiquidBlock> ENDER_BLOCK = ModBlocks.BLOCKS.register(
            "ender",
            () -> new LiquidBlock((FlowingFluid) ENDER.get(),
                    BlockBehaviour.Properties.ofLegacyCopy(Blocks.WATER)
                            .noLootTable()
                            .replaceable()
                            .strength(100.0F)
                            .noCollission()));
    public static final DeferredBlock<LiquidBlock> DATA_CORROSION_LIQUID_BLOCK = ModBlocks.BLOCKS.register(
            "data_corrosion_liquid",
            () -> new LiquidBlock((FlowingFluid) DATA_CORROSION_LIQUID.get(),
                    BlockBehaviour.Properties.ofLegacyCopy(Blocks.WATER)
                            .noLootTable()
                            .replaceable()
                            .strength(100.0F)
                            .lightLevel(state -> 4)
                            .noCollission()));

    public static final DeferredItem<Item> ENDER_BUCKET = ModItems.ITEMS.register(
            "ender_bucket",
            () -> new BucketItem(ENDER.get(), new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));
    public static final DeferredItem<Item> DATA_CORROSION_LIQUID_BUCKET = ModItems.ITEMS.register(
            "data_corrosion_liquid_bucket",
            () -> new BucketItem(DATA_CORROSION_LIQUID.get(),
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET).rarity(Rarity.RARE)));

    private ModFluids() {}

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    private static BaseFlowingFluid.Properties enderProperties() {
        return baseProperties(ENDER_TYPE, ENDER, FLOWING_ENDER, ENDER_BUCKET, ENDER_BLOCK, 8, 2, 1400.0F);
    }

    private static BaseFlowingFluid.Properties dataCorrosionLiquidProperties() {
        return baseProperties(DATA_CORROSION_LIQUID_TYPE, DATA_CORROSION_LIQUID, FLOWING_DATA_CORROSION_LIQUID,
                DATA_CORROSION_LIQUID_BUCKET, DATA_CORROSION_LIQUID_BLOCK, 12, 2, 1800.0F);
    }

    private static BaseFlowingFluid.Properties baseProperties(
                                                              Supplier<? extends FluidType> fluidType,
                                                              Supplier<? extends Fluid> still,
                                                              Supplier<? extends Fluid> flowing,
                                                              Supplier<? extends Item> bucket,
                                                              Supplier<? extends LiquidBlock> block,
                                                              int tickRate,
                                                              int levelDecreasePerBlock,
                                                              float explosionResistance) {
        return new BaseFlowingFluid.Properties(fluidType, still, flowing)
                .bucket(bucket)
                .block(block)
                .tickRate(tickRate)
                .levelDecreasePerBlock(levelDecreasePerBlock)
                .slopeFindDistance(4)
                .explosionResistance(explosionResistance);
    }

    private static final class EnderTeleportFluidType extends FluidType {

        private EnderTeleportFluidType(Properties properties) {
            super(properties);
        }

        @Override
        public boolean move(FluidState state, LivingEntity entity, Vec3 movementVector, double gravity) {
            if (!(entity.level() instanceof ServerLevel serverLevel) || !entity.isAlive() || entity.isSpectator()) {
                return false;
            }

            CompoundTag persistentData = entity.getPersistentData();
            long gameTime = serverLevel.getGameTime();
            if (persistentData.getLong(ENDER_TELEPORT_TAG) > gameTime) {
                return false;
            }

            if (teleportRandomly(serverLevel, entity, serverLevel.getRandom())) {
                persistentData.putLong(ENDER_TELEPORT_TAG, gameTime + ENDER_TELEPORT_COOLDOWN_TICKS);
            }
            return false;
        }

        private static boolean teleportRandomly(ServerLevel level, LivingEntity entity, RandomSource random) {
            BlockPos origin = entity.blockPosition();
            for (int i = 0; i < ENDER_TELEPORT_ATTEMPTS; i++) {
                int x = origin.getX() + random.nextIntBetweenInclusive(-ENDER_TELEPORT_HALF_RANGE, ENDER_TELEPORT_HALF_RANGE);
                int y = origin.getY() + random.nextIntBetweenInclusive(-ENDER_TELEPORT_HALF_RANGE, ENDER_TELEPORT_HALF_RANGE);
                int z = origin.getZ() + random.nextIntBetweenInclusive(-ENDER_TELEPORT_HALF_RANGE, ENDER_TELEPORT_HALF_RANGE);
                BlockPos target = new BlockPos(x, y, z);
                if (target.getY() <= level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight() - 2) {
                    continue;
                }

                BlockPos floor = target.below();
                if (level.getBlockState(floor).isAir()) {
                    continue;
                }
                if (!level.getBlockState(target).isAir() || !level.getBlockState(target.above()).isAir()) {
                    continue;
                }

                double targetX = target.getX() + 0.5D;
                double targetY = target.getY();
                double targetZ = target.getZ() + 0.5D;
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.teleportTo(level, targetX, targetY, targetZ, java.util.Set.of(),
                            serverPlayer.getYRot(), serverPlayer.getXRot());
                } else {
                    entity.teleportTo(targetX, targetY, targetZ);
                }
                entity.fallDistance = 0.0F;
                return true;
            }
            return false;
        }
    }

    private static final class DragonBreathDamageFluidType extends FluidType {

        private DragonBreathDamageFluidType(Properties properties) {
            super(properties);
        }

        @Override
        public boolean move(FluidState state, LivingEntity entity, Vec3 movementVector, double gravity) {
            if (!(entity.level() instanceof ServerLevel serverLevel) || !entity.isAlive() || entity.isSpectator()) {
                return false;
            }

            CompoundTag persistentData = entity.getPersistentData();
            long gameTime = serverLevel.getGameTime();
            if (persistentData.getLong(DATA_CORROSION_LIQUID_DAMAGE_TAG) > gameTime) {
                return false;
            }

            if (entity.hurt(new DataCorrosionDamageSource(serverLevel.damageSources().dragonBreath().typeHolder()),
                    DATA_CORROSION_LIQUID_FLUID_DAMAGE)) {
                persistentData.putLong(DATA_CORROSION_LIQUID_DAMAGE_TAG,
                        gameTime + DATA_CORROSION_LIQUID_DAMAGE_COOLDOWN_TICKS);
            }
            return false;
        }
    }

    private static final class DataCorrosionDamageSource extends DamageSource {

        private DataCorrosionDamageSource(Holder<DamageType> type) {
            super(type);
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity victim) {
            return Component.translatable(DATA_CORROSION_DEATH_MESSAGE_KEY, victim.getDisplayName());
        }
    }
}
