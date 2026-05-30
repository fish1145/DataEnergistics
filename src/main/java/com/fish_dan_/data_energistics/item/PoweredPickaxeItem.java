package com.fish_dan_.data_energistics.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public class PoweredPickaxeItem extends AbstractPoweredTieredItem implements ConditionalDataFlowCellItem {

    private static final float SABER_ENERGY_DESTROY_SPEED_BONUS = 8.0F;

    public PoweredPickaxeItem(Tier tier, Properties properties) {
        super(tier, properties, tier.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE));
    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return net.minecraft.world.item.DiggerItem.createAttributes(tier, attackDamage, attackSpeed);
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
        return stack.is(com.fish_dan_.data_energistics.registry.ModItems.DATA_CRYSTAL_PICKAXE.get()) && ConditionalDataFlowCellItem.super.hasDataFlowCellSupport(stack);
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
            this.tryChainMineOre(stack, (ServerLevel) level, pos, state, miningEntity);
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

    private void tryChainMineOre(ItemStack stack, ServerLevel level, BlockPos origin, BlockState originState, LivingEntity miner) {
        if (!stack.is(com.fish_dan_.data_energistics.registry.ModItems.DATA_CRYSTAL_PICKAXE.get()) || !PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) || !PoweredToolSaberEnergyHelper.consumeDataFlow(stack)) {
            return;
        }

        // The origin block is already broken by the time mineBlock runs, so add its extra copy explicitly here.
        dropDuplicateOreLoot(level, origin, originState, miner, stack);

        for (BlockPos targetPos : PoweredToolSaberEnergyHelper.collectOreVein(level, origin, 128)) {
            if (targetPos.equals(origin)) {
                continue;
            }
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.isAir() || targetState.getDestroySpeed(level, targetPos) < 0.0F) {
                continue;
            }

            dropDuplicateOreLoot(level, targetPos, targetState, miner, stack);
            level.destroyBlock(targetPos, true, miner);
        }
    }

    private static void dropDuplicateOreLoot(ServerLevel level, BlockPos pos, BlockState state, LivingEntity miner, ItemStack tool) {
        for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), miner, tool)) {
            net.minecraft.world.level.block.Block.popResource(level, pos, drop.copy());
        }
    }

    private float getSaberEnergyDestroySpeedBonus(ItemStack stack) {
        return PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) ? SABER_ENERGY_DESTROY_SPEED_BONUS : 0.0F;
    }
}
