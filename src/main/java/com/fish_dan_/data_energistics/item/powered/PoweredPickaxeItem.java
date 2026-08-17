package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PoweredPickaxeItem extends AbstractPoweredTieredItem implements ConditionalDataFlowCellItem {

    private static final float SABER_ENERGY_DESTROY_SPEED_BONUS = 8.0F;
    private static final ThreadLocal<Set<BlockPos>> FTB_ULTIMINE_DUPLICATED_POSITIONS = ThreadLocal.withInitial(HashSet::new);

    public PoweredPickaxeItem(Tier tier, Properties properties) {
        super(tier, properties, tier.createToolProperties(BlockTags.MINEABLE_WITH_PICKAXE));
    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return DiggerItem.createAttributes(tier, attackDamage, attackSpeed);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        this.appendConditionalCellInformationToTooltip(stack, lines);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return this.getConditionalCellTooltipImage(stack);
    }

    @Override
    public boolean hasDataFlowCellSupport(ItemStack stack) {
        return stack.is(DEItems.DATA_CRYSTAL_PICKAXE.get()) && ConditionalDataFlowCellItem.super.hasDataFlowCellSupport(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return this.isEnergyBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return this.getEnergyBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return this.getEnergyBarColor(stack);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return false;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float base = super.getDestroySpeed(stack, state);
        return this.hasSufficientEnergy(stack) ? base + this.getSpeedCardDestroySpeedBonus(stack) + this.getSaberEnergyDestroySpeedBonus(stack) : this.getUnpoweredDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return this.hasSufficientEnergy(stack) && super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miningEntity) {
        if (!this.hasSufficientEnergy(stack)) {
            return false;
        }
        boolean result = super.mineBlock(stack, level, state, pos, miningEntity);
        if (result && !level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            if (!consumeFtbUltimineDuplicateMarker(pos)) {
                tryDropDuplicateOreLoot(stack, (ServerLevel) level, pos, state, miningEntity);
            }
            this.consumeActionEnergy(stack);
        }
        return result;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!this.hasSufficientEnergy(stack)) {
            return false;
        }
        boolean result = true;
        if (result && !attacker.level().isClientSide) {
            this.consumeActionEnergy(stack);
        }
        return result;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {}

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!this.hasSufficientEnergy(context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = super.useOn(context);
        if (result.consumesAction() && !context.getLevel().isClientSide) {
            this.consumeActionEnergy(context.getItemInHand());
        }
        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.hasSufficientEnergy(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (result.getResult().consumesAction() && !level.isClientSide) {
            this.consumeActionEnergy(result.getObject());
        }
        return result;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget,
                                                  InteractionHand usedHand) {
        if (!this.hasSufficientEnergy(stack)) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = super.interactLivingEntity(stack, player, interactionTarget, usedHand);
        if (result.consumesAction() && !player.level().isClientSide) {
            this.consumeActionEnergy(stack);
        }
        return result;
    }

    public static boolean tryDropDuplicateOreLoot(ItemStack stack, ServerLevel level, BlockPos pos, BlockState state, LivingEntity miner) {
        if (!(stack.getItem() instanceof PoweredPickaxeItem pickaxe) || !stack.is(DEItems.DATA_CRYSTAL_PICKAXE.get())) {
            return false;
        }

        if (!PoweredToolSaberEnergyHelper.isOreBlock(state)) {
            logDuplicateOreResult("skip_not_ore", level, pos, state, miner, stack, 0);
            return false;
        }
        if (!pickaxe.hasSufficientEnergy(stack)) {
            logDuplicateOreResult("skip_no_ae_energy", level, pos, state, miner, stack, 0);
            return false;
        }
        if (!PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, pickaxe)) {
            logDuplicateOreResult("skip_no_saber_energy_card", level, pos, state, miner, stack, 0);
            return false;
        }
        if (!PoweredToolSaberEnergyHelper.consumeDataFlow(stack)) {
            logDuplicateOreResult("skip_no_data_flow", level, pos, state, miner, stack, 0);
            return false;
        }

        List<ItemStack> drops = createDuplicateOreDrops(state);
        dropDuplicateOreLoot(level, pos, drops);
        logDuplicateOreResult("duplicated", level, pos, state, miner, stack, drops);
        return true;
    }

    private static List<ItemStack> createDuplicateOreDrops(BlockState state) {
        ItemLike item = state.getBlock();
        ItemStack drop = new ItemStack(item);
        return drop.isEmpty() ? List.of() : List.of(drop);
    }

    private static void dropDuplicateOreLoot(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            Block.popResource(level, pos, drop.copy());
        }
    }

    private static void logDuplicateOreResult(String result, ServerLevel level, BlockPos pos, BlockState state,
                                              LivingEntity miner, ItemStack tool, int dropCount) {
        logDuplicateOreResult(result, level, pos, state, miner, tool, List.of(), dropCount);
    }

    private static void logDuplicateOreResult(String result, ServerLevel level, BlockPos pos, BlockState state,
                                              LivingEntity miner, ItemStack tool, List<ItemStack> drops) {
        logDuplicateOreResult(result, level, pos, state, miner, tool, drops, countItems(drops));
    }

    private static void logDuplicateOreResult(String result, ServerLevel level, BlockPos pos, BlockState state,
                                              LivingEntity miner, ItemStack tool, List<ItemStack> drops, int dropCount) {
        if (!DataEnergisticsConfiguration.INSTANCE.developer.verboseRuntimeLogging) {
            return;
        }
        Data_Energistics.LOGGER.info(
                "Data crystal pickaxe duplicate ore result={} level={} pos={} block={} miner={} tool={} aeEnergy={} silkTouch={} fortune={} dataDrops={} drops={}",
                result,
                level.dimension().location(),
                pos,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                miner.getName().getString(),
                BuiltInRegistries.ITEM.getKey(tool.getItem()),
                tool.getItem() instanceof PoweredPickaxeItem pickaxe ? pickaxe.getAECurrentPower(tool) : 0.0D,
                EnchantmentHelper.getItemEnchantmentLevel(
                        level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),
                        tool),
                EnchantmentHelper.getItemEnchantmentLevel(
                        level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE),
                        tool),
                dropCount,
                formatDrops(drops));
    }

    private static int countItems(List<ItemStack> drops) {
        int count = 0;
        for (ItemStack drop : drops) {
            count += drop.getCount();
        }
        return count;
    }

    private static String formatDrops(List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return "[]";
        }

        List<String> formattedDrops = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            formattedDrops.add(drop.getCount() + "x" + BuiltInRegistries.ITEM.getKey(drop.getItem()));
        }
        return formattedDrops.toString();
    }

    public static boolean tryDropDuplicateOreLootFromFtbUltimine(Player player, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        ItemStack stack = serverPlayer.getMainHandItem();
        if (!tryDropDuplicateOreLoot(stack, serverPlayer.serverLevel(), pos, state, serverPlayer)) {
            return false;
        }

        FTB_ULTIMINE_DUPLICATED_POSITIONS.get().add(pos.immutable());
        return true;
    }

    public static void clearFtbUltimineDuplicateMarkers() {
        FTB_ULTIMINE_DUPLICATED_POSITIONS.get().clear();
    }

    private static boolean consumeFtbUltimineDuplicateMarker(BlockPos pos) {
        return FTB_ULTIMINE_DUPLICATED_POSITIONS.get().remove(pos);
    }

    private float getSaberEnergyDestroySpeedBonus(ItemStack stack) {
        return PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) ? SABER_ENERGY_DESTROY_SPEED_BONUS : 0.0F;
    }
}
