package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;
import java.util.Optional;

public class PoweredAxeItem extends AbstractPoweredTieredItem implements ConditionalDataFlowCellItem {

    private static final float SABER_ENERGY_DESTROY_SPEED_BONUS = 8.0F;

    public PoweredAxeItem(Tier tier, Properties properties) {
        super(tier, properties, tier.createToolProperties(BlockTags.MINEABLE_WITH_AXE));
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
        return stack.is(DEItems.DATA_CRYSTAL_AXE.get()) && ConditionalDataFlowCellItem.super.hasDataFlowCellSupport(stack);
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
            this.tryChainBreakTree(stack, (ServerLevel) level, pos, miningEntity);
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
        InteractionResult result = this.tryTransformBlock(context);
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

    private void tryChainBreakTree(ItemStack stack, ServerLevel level, BlockPos origin, LivingEntity breaker) {
        if (!stack.is(DEItems.DATA_CRYSTAL_AXE.get()) || !PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) || !PoweredToolSaberEnergyHelper.consumeDataFlow(stack)) {
            return;
        }

        for (BlockPos targetPos : PoweredToolSaberEnergyHelper.collectTree(level, origin, 256)) {
            if (targetPos.equals(origin)) {
                continue;
            }
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.isAir() || targetState.getDestroySpeed(level, targetPos) < 0.0F) {
                continue;
            }
            level.destroyBlock(targetPos, true, breaker);
        }
    }

    private float getSaberEnergyDestroySpeedBonus(ItemStack stack) {
        return PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) ? SABER_ENERGY_DESTROY_SPEED_BONUS : 0.0F;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(itemAbility);
    }

    private InteractionResult tryTransformBlock(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockState transformed = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false);
        if (transformed != null) {
            level.playSound(context.getPlayer(), pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            transformed = state.getToolModifiedState(context, ItemAbilities.AXE_SCRAPE, false);
            if (transformed != null) {
                level.playSound(context.getPlayer(), pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.levelEvent(context.getPlayer(), 3005, pos, 0);
            } else {
                transformed = state.getToolModifiedState(context, ItemAbilities.AXE_WAX_OFF, false);
                if (transformed != null) {
                    level.playSound(context.getPlayer(), pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.levelEvent(context.getPlayer(), 3004, pos, 0);
                }
            }
        }

        if (transformed == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            level.setBlock(pos, transformed, 11);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(context.getPlayer(), transformed));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
