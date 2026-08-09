package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;

import java.util.Set;

/**
 * Immutable presentation and behavior facts for one installed provider stack.
 *
 * @param slotsPerProvider number of pattern slots contributed by one installed provider
 * @param mainMenuIcon     icon shown by the provider menu
 * @param terminalIcon     icon used by AE terminal rows
 * @param displayName      provider name shown to players
 * @param capabilities     composable behavior identifiers implemented by the provider
 */
public record AdaptivePatternProviderProfile(
                                             int slotsPerProvider,
                                             ItemStack mainMenuIcon,
                                             AEItemKey terminalIcon,
                                             Component displayName,
                                             Set<ResourceLocation> capabilities) {

    /**
     * Validates profile invariants and detaches mutable values at the public boundary.
     */
    public AdaptivePatternProviderProfile {
        if (slotsPerProvider <= 0) {
            throw new IllegalArgumentException("Adaptive pattern provider slot count must be positive");
        }
        mainMenuIcon = mainMenuIcon.copy();
        if (mainMenuIcon.isEmpty()) {
            throw new IllegalArgumentException("Adaptive pattern provider main-menu icon must not be empty");
        }
        displayName = displayName.copy();
        capabilities = Set.copyOf(capabilities);
    }

    /**
     * Returns a defensive icon copy because {@link ItemStack} is mutable.
     */
    @Override
    public ItemStack mainMenuIcon() {
        return this.mainMenuIcon.copy();
    }

    /**
     * Returns a defensive component copy so callers cannot retain mutable text state.
     */
    @Override
    public Component displayName() {
        return this.displayName.copy();
    }

    /**
     * Checks whether the provider implements one known behavior.
     *
     * @param capability stable behavior identifier
     * @return whether the capability was declared
     */
    public boolean supports(ResourceLocation capability) {
        return this.capabilities.contains(capability);
    }
}
