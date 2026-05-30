package com.fish_dan_.data_energistics.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public class PoweredShovelItem extends AbstractPoweredTieredItem implements ConditionalDataFlowCellItem {

    private static final String TAG_BREAK_RADIUS = "SaberEnergyBreakRadius";
    private static final float SABER_ENERGY_DESTROY_SPEED_BONUS = 8.0F;

    public PoweredShovelItem(Tier tier, Properties properties) {
        super(tier, properties, tier.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL));
    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return net.minecraft.world.item.DiggerItem.createAttributes(tier, attackDamage, attackSpeed);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        this.appendConditionalCellInformationToTooltip(stack, lines);
        if (PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this)) {
            int size = this.getBreakSize(stack);
            lines.add(Component.literal(size + "x" + size));
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return this.getConditionalCellTooltipImage(stack);
    }

    @Override
    public boolean hasDataFlowCellSupport(ItemStack stack) {
        return stack.is(com.fish_dan_.data_energistics.registry.ModItems.DATA_CRYSTAL_SHOVEL.get()) && ConditionalDataFlowCellItem.super.hasDataFlowCellSupport(stack);
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
            this.tryAreaMine(stack, (ServerLevel) level, pos, state, miningEntity);
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
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown() && context.getItemInHand().is(com.fish_dan_.data_energistics.registry.ModItems.DATA_CRYSTAL_SHOVEL.get()) && PoweredToolSaberEnergyHelper.hasSaberEnergy(context.getItemInHand(), this)) {
            if (!context.getLevel().isClientSide) {
                this.toggleBreakSize(context.getItemInHand());
                int size = this.getBreakSize(context.getItemInHand());
                context.getPlayer().displayClientMessage(Component.literal(size + "x" + size), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (!this.hasSufficientEnergy(context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = this.tryFlattenOrDouse(context);
        if (result.consumesAction() && !context.getLevel().isClientSide) {
            this.consumeActionEnergy(context.getItemInHand());
        }
        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.is(com.fish_dan_.data_energistics.registry.ModItems.DATA_CRYSTAL_SHOVEL.get()) && PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this)) {
            if (!level.isClientSide) {
                this.toggleBreakSize(stack);
                int size = this.getBreakSize(stack);
                player.displayClientMessage(Component.literal(size + "x" + size), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
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

    private void tryAreaMine(ItemStack stack, ServerLevel level, BlockPos origin, BlockState originState, LivingEntity miner) {
        if (!stack.is(com.fish_dan_.data_energistics.registry.ModItems.DATA_CRYSTAL_SHOVEL.get()) || !PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) || !PoweredToolSaberEnergyHelper.consumeDataFlow(stack)) {
            return;
        }

        int radius = this.getBreakRadius(stack);
        for (BlockPos targetPos : BlockPos.betweenClosed(origin.offset(-radius, 0, -radius), origin.offset(radius, 0, radius))) {
            BlockPos immutablePos = targetPos.immutable();
            if (immutablePos.equals(origin)) {
                continue;
            }
            BlockState targetState = level.getBlockState(immutablePos);
            if (targetState.isAir() || targetState.getBlock() != originState.getBlock() || targetState.getDestroySpeed(level, immutablePos) < 0.0F) {
                continue;
            }
            level.destroyBlock(immutablePos, true, miner);
        }
    }

    private void toggleBreakSize(ItemStack stack) {
        int radius = this.getBreakRadius(stack) == 1 ? 2 : 1;
        CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_BREAK_RADIUS, radius));
    }

    private int getBreakRadius(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getInt(TAG_BREAK_RADIUS) == 2 ? 2 : 1;
    }

    private int getBreakSize(ItemStack stack) {
        return this.getBreakRadius(stack) * 2 + 1;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility itemAbility) {
        return net.neoforged.neoforge.common.ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(itemAbility);
    }

    private InteractionResult tryFlattenOrDouse(UseOnContext context) {
        if (context.getClickedFace() == Direction.DOWN) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockState transformed = state.getToolModifiedState(context, net.neoforged.neoforge.common.ItemAbilities.SHOVEL_FLATTEN, false);
        if (transformed != null && level.getBlockState(pos.above()).isAir()) {
            level.playSound(context.getPlayer(), pos, net.minecraft.sounds.SoundEvents.SHOVEL_FLATTEN,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            transformed = state.getToolModifiedState(context, net.neoforged.neoforge.common.ItemAbilities.SHOVEL_DOUSE, false);
            if (transformed != null && !level.isClientSide) {
                level.levelEvent(null, 1009, pos, 0);
            }
        }

        if (transformed == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            level.setBlock(pos, transformed, 11);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(context.getPlayer(), transformed));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private float getSaberEnergyDestroySpeedBonus(ItemStack stack) {
        return PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) ? SABER_ENERGY_DESTROY_SPEED_BONUS : 0.0F;
    }
}
