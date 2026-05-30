package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.entity.DispersingDataEntity;
import com.fish_dan_.data_energistics.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ResidualDataOreBlock extends DropExperienceBlock {

    private static final UniformInt EXPERIENCE_RANGE = UniformInt.of(50, 75);
    private static final int EFFECT_DURATION_TICKS = 200;
    private static final Holder<MobEffect>[] RANDOM_BUFFS = new Holder[] {
            MobEffects.REGENERATION,
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DAMAGE_BOOST,
            MobEffects.DAMAGE_RESISTANCE,
            MobEffects.DIG_SPEED,
            MobEffects.ABSORPTION,
            MobEffects.JUMP,
            MobEffects.LUCK,
            MobEffects.WEAKNESS,
            MobEffects.POISON,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.DIG_SLOWDOWN,
            MobEffects.BLINDNESS,
            MobEffects.HUNGER,
            MobEffects.WITHER,
            MobEffects.CONFUSION
    };

    public ResidualDataOreBlock(Properties properties) {
        super(EXPERIENCE_RANGE, properties);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        this.tryDropExperience(serverLevel, pos, tool, EXPERIENCE_RANGE);

        RandomSource random = serverLevel.getRandom();
        int amount = 1 + random.nextInt(9);
        switch (random.nextInt(5)) {
            case 0 -> player.hurt(serverLevel.damageSources().genericKill(), amount);
            case 1 -> player.heal(amount);
            case 2 -> reduceFood(player, amount);
            case 3 -> restoreFood(player, amount);
            case 4 -> applyRandomBuff(player, random);
            default -> {}
        }

        spawnDispersingData(serverLevel, pos, random, tool);
    }

    private static void reduceFood(Player player, int amount) {
        FoodData foodData = player.getFoodData();
        int newFoodLevel = Math.max(0, foodData.getFoodLevel() - amount);
        foodData.setFoodLevel(newFoodLevel);
        foodData.setSaturation(Math.min(foodData.getSaturationLevel(), newFoodLevel));
    }

    private static void restoreFood(Player player, int amount) {
        player.getFoodData().eat(amount, 0.0F);
    }

    private static void applyRandomBuff(Player player, RandomSource random) {
        Holder<MobEffect> effect = RANDOM_BUFFS[random.nextInt(RANDOM_BUFFS.length)];
        player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION_TICKS, 0));
    }

    private static void spawnDispersingData(ServerLevel level, BlockPos pos, RandomSource random, ItemStack tool) {
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().holderOrThrow(Enchantments.FORTUNE), tool);
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < fortuneLevel; i++) {
            count += random.nextInt(2);
        }
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
}
