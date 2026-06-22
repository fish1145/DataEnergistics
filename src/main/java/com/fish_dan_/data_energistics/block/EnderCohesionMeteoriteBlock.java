package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.entity.DispersingDataEntity;

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

import java.util.Set;

public class EnderCohesionMeteoriteBlock extends Block {

    private static final int TELEPORT_HALF_RANGE = 3;
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

        this.handleSpecialMining(serverLevel, player, pos, state, tool, hasSilkTouch(serverLevel, tool), getFortuneLevel(serverLevel, tool));
    }

    public void handleSpecialMining(ServerLevel serverLevel, Player player, BlockPos pos, BlockState state, ItemStack tool, boolean silkTouch, int fortuneLevel) {
        if (!tool.isCorrectToolForDrops(state)) {
            return;
        }

        if (!silkTouch) {
            this.spawnDispersingData(serverLevel, pos, serverLevel.getRandom(), fortuneLevel);
        }

        RandomSource random = serverLevel.getRandom();
        this.dropFortuneScaledResource(serverLevel, pos, random, new ItemStack(AEItems.ENDER_DUST.asItem()), this.enderDustChance, fortuneLevel);
        this.dropFortuneScaledResource(serverLevel, pos, random, new ItemStack(AEItems.SKY_DUST.asItem()), this.skyDustChance, fortuneLevel);
        if (this.teleportChance > 0.0F && random.nextFloat() < this.teleportChance && player instanceof ServerPlayer serverPlayer) {
            teleportRandomly(serverLevel, serverPlayer, random);
        }
    }

    public static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),
                tool) > 0;
    }

    public static int getFortuneLevel(ServerLevel level, ItemStack tool) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE),
                tool);
    }

    private void spawnDispersingData(ServerLevel level, BlockPos pos, RandomSource random, int fortuneLevel) {
        int rolls = getFortuneScaledRolls(this.dispersingDataChance, fortuneLevel, random);
        if (rolls <= 0) {
            return;
        }

        for (int roll = 0; roll < rolls; roll++) {
            int count = this.teleportChance > 0.0F ? 1 + random.nextInt(2) : 1;
            for (int i = 0; i < count; i++) {
                DispersingDataEntity.spawnAt(level, pos, random);
            }
        }
    }

    private void dropFortuneScaledResource(ServerLevel level, BlockPos pos, RandomSource random, ItemStack stack, float baseChance, int fortuneLevel) {
        int rolls = getFortuneScaledRolls(baseChance, fortuneLevel, random);
        for (int i = 0; i < rolls; i++) {
            popResource(level, pos, stack.copy());
        }
    }

    private static int getFortuneScaledRolls(float baseChance, int fortuneLevel, RandomSource random) {
        float scaledChance = baseChance * (Math.max(0, fortuneLevel) + 1);
        int rolls = (int) scaledChance;
        return random.nextFloat() < scaledChance - rolls ? rolls + 1 : rolls;
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

            player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot());
            player.fallDistance = 0.0F;
            return;
        }
    }
}
