package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.registry.DEMenus;

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
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Generic order-package item whose optional target is configured independently from crafting execution.
 */
public final class OrderPackageItem extends Item implements IMenuItem {

    /** Creates the non-stackable package item. */
    public OrderPackageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) {
            MenuOpener.open(DEMenus.ORDER_PACKAGE.get(), player, MenuLocators.forHand(player, usedHand));
        }
        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide()) {
            MenuOpener.open(DEMenus.ORDER_PACKAGE.get(), player, MenuLocators.forItemUseContext(context));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public @Nullable ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator,
                                                 @Nullable BlockHitResult hitResult) {
        return new OrderPackageMenuHost(this, player, locator);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        OrderPackageTarget.get().getTarget(stack)
                .ifPresent(target -> lines.add(Component.translatable(
                        "tooltip.data_energistics.order_package.target",
                        target.getDisplayName())));
    }
}
