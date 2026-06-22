package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.world.PersistentFarmlandSavedData;

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
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;
import java.util.Optional;

public class PoweredHoeItem extends AbstractPoweredTieredItem implements ConditionalDataFlowCellItem {

    private static final float SABER_ENERGY_DESTROY_SPEED_BONUS = 8.0F;

    public PoweredHoeItem(Tier tier, Properties properties) {
        super(tier, properties, tier.createToolProperties(BlockTags.MINEABLE_WITH_HOE));
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
        return stack.is(ModItems.DATA_CRYSTAL_HOE.get()) && ConditionalDataFlowCellItem.super.hasDataFlowCellSupport(stack);
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
        InteractionResult result = this.tryTill(context);
        if (result.consumesAction() && !context.getLevel().isClientSide) {
            this.tryMarkPersistentFarmland(context);
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

    private void tryMarkPersistentFarmland(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!stack.is(ModItems.DATA_CRYSTAL_HOE.get()) || !PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) || !(context.getLevel() instanceof ServerLevel serverLevel) || !PoweredToolSaberEnergyHelper.consumeDataFlow(stack)) {
            return;
        }

        BlockPos farmlandPos = context.getClickedPos().relative(context.getClickedFace());
        BlockState farmlandState = serverLevel.getBlockState(farmlandPos);
        if (!(farmlandState.getBlock() instanceof FarmBlock)) {
            farmlandPos = context.getClickedPos();
            farmlandState = serverLevel.getBlockState(farmlandPos);
        }

        if (farmlandState.getBlock() instanceof FarmBlock && farmlandState.hasProperty(FarmBlock.MOISTURE)) {
            serverLevel.setBlock(farmlandPos, farmlandState.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 3);
            PersistentFarmlandSavedData.get(serverLevel).add(farmlandPos);
        }
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return ItemAbilities.DEFAULT_HOE_ACTIONS.contains(itemAbility);
    }

    private InteractionResult tryTill(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState targetState = level.getBlockState(pos).getToolModifiedState(context,
                ItemAbilities.HOE_TILL, false);
        if (targetState == null) {
            return InteractionResult.PASS;
        }

        level.playSound(context.getPlayer(), pos, SoundEvents.HOE_TILL,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!level.isClientSide) {
            level.setBlock(pos, targetState, 11);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos,
                    Context.of(context.getPlayer(), targetState));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private float getSaberEnergyDestroySpeedBonus(ItemStack stack) {
        return PoweredToolSaberEnergyHelper.hasSaberEnergy(stack, this) ? SABER_ENERGY_DESTROY_SPEED_BONUS : 0.0F;
    }
}
