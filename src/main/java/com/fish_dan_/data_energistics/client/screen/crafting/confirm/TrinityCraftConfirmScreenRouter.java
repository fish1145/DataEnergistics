package com.fish_dan_.data_energistics.client.screen.crafting.confirm;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanSessionTransfer;

import appeng.client.gui.me.crafting.CraftConfirmScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Routes only AE2's native crafting confirmation screen to the Trinity-authored presentation. */
public final class TrinityCraftConfirmScreenRouter {

    private static final Set<Screen> FAILED_SCREENS = Collections.newSetFromMap(new WeakHashMap<>());

    private TrinityCraftConfirmScreenRouter() {}

    public static @Nullable Screen routeOpeningScreen(@Nullable Screen currentScreen) {
        return replacement(currentScreen);
    }

    public static boolean replaceSynchronizedNativeScreen(CraftConfirmScreen currentScreen) {
        Screen replacement = replacement(currentScreen);
        if (replacement == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != currentScreen) {
            return false;
        }
        minecraft.setScreen(replacement);
        return true;
    }

    private static @Nullable Screen replacement(@Nullable Screen currentScreen) {
        if (currentScreen == null) {
            return null;
        }
        if (currentScreen.getClass() != CraftConfirmScreen.class || FAILED_SCREENS.contains(currentScreen)) {
            return null;
        }
        CraftConfirmScreen screen = (CraftConfirmScreen) currentScreen;
        if (!((CraftingPlanSessionTransfer) screen.getMenu()).data_energistics$hasTrinityCpu()) {
            return null;
        }
        try {
            return new TrinityCraftConfirmScreen(
                    screen.getMenu(),
                    screen.getMenu().getPlayerInventory(),
                    screen.getTitle());
        } catch (RuntimeException failure) {
            FAILED_SCREENS.add(currentScreen);
            Data_Energistics.LOGGER.error(
                    "Could not create the Trinity crafting confirmation screen; retaining AE2's native screen",
                    failure);
            return null;
        }
    }
}
