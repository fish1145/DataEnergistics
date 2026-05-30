package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.Nullable;

public interface UniversalTerminalAdapter {

    String name();

    boolean matches(ItemStack stack);

    default boolean canInstall(ItemStack stack) {
        return this.matches(stack);
    }

    default ItemStack createStoredTerminal(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    ItemStack createIcon();

    MenuType<?> getMenuType();

    default boolean requiresCustomMenuLocator() {
        return false;
    }

    default @Nullable IConfigManager createConfigManager(Runnable saveAction) {
        return null;
    }

    default <T> @Nullable T resolveMenuHost(UniversalTerminalPart part, Player player, Class<T> hostInterface) {
        return UniversalTerminalMenuHostResolver.resolve(part, hostInterface);
    }

    default UniversalTerminalConfigProfile configProfile() {
        return UniversalTerminalConfigProfile.STANDARD;
    }
}
