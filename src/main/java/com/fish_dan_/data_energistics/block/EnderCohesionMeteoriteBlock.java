package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.entity.DispersingDataEntity;
import com.fish_dan_.data_energistics.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.core.definitions.AEItems;

public class EnderCohesionMeteoriteBlock extends Block {

    private static final int TELEPORT_HALF_RANGE = 3;
    private static final float FORTUNE_BONUS_PER_LEVEL = 0.03F;
    private final float dispersingDataChance;
    private final float enderDustChance;
    private final float skyDustChance;
    private final float teleportChance;

    public EnderCohesionMeteoriteBlock(Properties properties, float dispersingDataChance, float enderDustChance, float skyDustChance, float teleportChance) {
        super(properties);
        this.dispersingDataChance = dispersingDataChance;
        this.enderDustChance = enderDustChance;
        this.skyDustChance = skyDustChance;
        this.teleportChance = teleportChance;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!(level instanceof ServerLevel serverLevel) || !tool.isCorrectToolForDrops(state)) {
            return;
        }

        int silkTouchLevel = EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),
                tool);
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE),
                tool);
        if (silkTouchLevel <= 0) {
            this.spawnDispersingData(serverLevel, pos, serverLevel.getRandom(), fortuneLevel);
        }

        RandomSource random = serverLevel.getRandom();
        if (random.nextFloat() < this.getFortuneAdjustedChance(this.enderDustChance, fortuneLevel)) {
            popResource(serverLevel, pos, new ItemStack(AEItems.ENDER_DUST.asItem()));
        }
        if (random.nextFloat() < this.getFortuneAdjustedChance(this.skyDustChance, fortuneLevel)) {
            popResource(serverLevel, pos, new ItemStack(AEItems.SKY_DUST.asItem()));
        }
        if (this.teleportChance > 0.0F && random.nextFloat() < this.teleportChance && player instanceof ServerPlayer serverPlayer) {
            teleportRandomly(serverLevel, serverPlayer, random);
        }
    }

    private void spawnDispersingData(ServerLevel level, BlockPos pos, RandomSource random, int fortuneLevel) {
        float dispersingDataChance = this.getFortuneAdjustedChance(this.dispersingDataChance, fortuneLevel);
        if (dispersingDataChance <= 0.0F || random.nextFloat() >= dispersingDataChance) {
            return;
        }

        int count = this.teleportChance > 0.0F ? 1 + random.nextInt(2) : 1;
        for (int i = 0; i < count; i++) {
            DispersingDataEntity entity = ModEntities.DISPERSING_DATA.get().create(level);
            if (entity == null) {
                continue;
            }

            double x = pos.getX() + 0.35D + random.nextDouble() * 0.3D;
            double y = pos.getY() + 0.7D + random.nextDouble() * 0.4D;
            double z = pos.getZ() + 0.35D + random.nextDouble() * 0.3D;
            entity.setPos(x, y, z);
            entity.setTextureVariant(random.nextInt(4));
            entity.setDeltaMovement(
                    (random.nextDouble() - 0.5D) * 0.08D,
                    0.01D + random.nextDouble() * 0.03D,
                    (random.nextDouble() - 0.5D) * 0.08D);
            level.addFreshEntity(entity);
        }
    }

    private float getFortuneAdjustedChance(float baseChance, int fortuneLevel) {
        return Math.min(1.0F, baseChance + fortuneLevel * FORTUNE_BONUS_PER_LEVEL);
    }

    private static void teleportRandomly(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos origin = player.blockPosition();
        for (int i = 0; i < 16; i++) {
            int x = origin.getX() + random.nextIntBetweenInclusive(-TELEPORT_HALF_RANGE, TELEPORT_HALF_RANGE);
            int y = origin.getY() + random.nextIntBetweenInclusive(-TELEPORT_HALF_RANGE, TELEPORT_HALF_RANGE);
            int z = origin.getZ() + random.nextIntBetweenInclusive(-TELEPORT_HALF_RANGE, TELEPORT_HALF_RANGE);
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

            player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, java.util.Set.of(), player.getYRot(), player.getXRot());
            player.fallDistance = 0.0F;
            return;
        }
    }
}
