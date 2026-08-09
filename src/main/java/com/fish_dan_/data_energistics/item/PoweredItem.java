package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.integration.extendedae.ExtendedAeRenamerCompat;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.QuartzKnifeMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PoweredItem extends Item implements PoweredEnergyItem, IMenuItem {

    public PoweredItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        this.appendEnergyHoverText(stack, lines);
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
    public InteractionResult useOn(UseOnContext context) {
        if (!this.hasSufficientEnergy(context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        InteractionResult renamerResult = ExtendedAeRenamerCompat.tryOpenRenamer(context);
        if (renamerResult.consumesAction()) {
            if (!context.getLevel().isClientSide) {
                this.consumeActionEnergy(context.getItemInHand());
            }
            return renamerResult;
        }
        if (this.isCuttingKnife(context.getItemInHand()) && context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (!context.getLevel().isClientSide && context.getPlayer() != null) {
                MenuOpener.open(QuartzKnifeMenu.TYPE, context.getPlayer(), MenuLocators.forItemUseContext(context));
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
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
        if (this.isCuttingKnife(stack) && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                MenuOpener.open(QuartzKnifeMenu.TYPE, player, MenuLocators.forHand(player, hand));
            }
            player.swing(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (result.getResult().consumesAction() && !level.isClientSide) {
            this.consumeActionEnergy(result.getObject());
        }
        return result;
    }

    @Override
    public @Nullable ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        ItemStack stack = locator.locateItem(player);
        return this.isCuttingKnife(stack) ? new ItemMenuHost<>(this, player, locator) : null;
    }

    private boolean isCuttingKnife(ItemStack stack) {
        return stack.is(DEItems.DATA_CRYSTAL_CUTTING_KNIFE.get());
    }
}
