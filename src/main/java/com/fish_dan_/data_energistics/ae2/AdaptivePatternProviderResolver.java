package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderProfile;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.parts.IPart;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.parts.crafting.PatternProviderPart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves installed provider stacks through the immutable adaptive-provider registration snapshot.
 *
 * <p>
 * All mod-specific recognition belongs to registered definitions. The resolver performs no namespace, class-name,
 * slot-count or reflection heuristics and rejects ambiguous claims instead of relying on registration order.
 * </p>
 */
public final class AdaptivePatternProviderResolver {

    private static volatile List<AdaptivePatternProviderRegistration> registrations = List.of();
    private static volatile boolean installed;

    private AdaptivePatternProviderResolver() {}

    /**
     * Installs the complete common-setup snapshot exactly once.
     *
     * @param registrations frozen plugin and built-in registrations
     */
    public static synchronized void install(
                                            @NotNull List<@NotNull AdaptivePatternProviderRegistration> registrations) {
        if (installed) {
            throw new IllegalStateException("Adaptive pattern provider definitions are already installed");
        }
        Set<ResourceLocation> registrationIds = new HashSet<>();
        ArrayList<AdaptivePatternProviderRegistration> sorted = new ArrayList<>(registrations);
        sorted.sort(Comparator.comparing(registration -> registration.registrationId().toString()));
        for (AdaptivePatternProviderRegistration registration : sorted) {
            if (!registrationIds.add(registration.registrationId())) {
                throw new IllegalStateException(
                        "Duplicate adaptive pattern provider registration ID: " + registration.registrationId());
            }
        }
        AdaptivePatternProviderResolver.registrations = List.copyOf(sorted);
        installed = true;
    }

    /**
     * Returns whether exactly one installed definition recognizes the stack.
     */
    public static boolean isSupportedProviderStack(@NotNull ItemStack stack) {
        return resolveProviderProfile(stack) != null;
    }

    /**
     * Returns the configured pattern slots, or zero when no definition recognizes the stack.
     */
    public static int getResolvedSlotsPerProvider(@NotNull ItemStack stack) {
        AdaptivePatternProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.slotsPerProvider() : 0;
    }

    /**
     * Returns an independently owned main-menu icon, or {@code null} for an unsupported stack.
     */
    public static @Nullable ItemStack getResolvedProviderMainMenuIcon(@NotNull ItemStack stack) {
        AdaptivePatternProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.mainMenuIcon() : null;
    }

    /**
     * Returns the immutable AE terminal icon, or {@code null} for an unsupported stack.
     */
    public static @Nullable AEItemKey getResolvedProviderTerminalIcon(@NotNull ItemStack stack) {
        AdaptivePatternProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.terminalIcon() : null;
    }

    /**
     * Returns an independently owned display component, or {@code null} for an unsupported stack.
     */
    public static @Nullable Component getResolvedProviderDisplayName(@NotNull ItemStack stack) {
        AdaptivePatternProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.displayName() : null;
    }

    /**
     * Checks one composable behavior without exposing a closed provider-kind enum.
     *
     * @param stack      installed provider stack
     * @param capability stable behavior identifier
     * @return whether the resolved profile declares the behavior
     */
    public static boolean hasResolvedCapability(
                                                @NotNull ItemStack stack,
                                                @NotNull ResourceLocation capability) {
        AdaptivePatternProviderProfile profile = resolveProviderProfile(stack);
        return profile != null && profile.supports(capability);
    }

    /**
     * Detects an AE2 pattern-provider attachment without attempting to infer adaptive profile metadata.
     */
    public static boolean isPatternProviderAttachment(
                                                      @NotNull Level level,
                                                      @NotNull BlockPos pos,
                                                      @Nullable Direction side) {
        if (level.getBlockEntity(pos) instanceof PatternProviderBlockEntity) {
            return true;
        }

        if (!(level.getBlockEntity(pos) instanceof CableBusBlockEntity cableBusBlockEntity)) {
            return false;
        }

        var cableBus = cableBusBlockEntity.getCableBus();
        IPart centerPart = cableBus.getPart(null);
        if (centerPart instanceof PatternProviderPart) {
            return true;
        }

        if (side == null) {
            for (Direction direction : Direction.values()) {
                if (cableBus.getPart(direction) instanceof PatternProviderPart) {
                    return true;
                }
            }
            return false;
        }

        return cableBus.getPart(side) instanceof PatternProviderPart;
    }

    /**
     * Decorates one recognized provider name with the standard block variant label.
     */
    public static @NotNull Component decorateAdaptiveProviderName(@NotNull Component providerName) {
        return decorateAdaptiveProviderName(
                "screen.data_energistics.adaptive_pattern_provider.provider_variant",
                providerName);
    }

    /**
     * Decorates one recognized provider name with the requested terminal variant label.
     */
    public static @NotNull Component decorateAdaptiveProviderName(
                                                                  @NotNull String translationKey,
                                                                  @NotNull Component providerName) {
        return Component.translatable(translationKey, providerName);
    }

    /**
     * Resolves exactly one complete profile from the frozen definitions.
     *
     * @param stack installed provider stack
     * @return matched profile, or {@code null} when no definition recognizes the stack
     */
    public static @Nullable AdaptivePatternProviderProfile resolveProviderProfile(@NotNull ItemStack stack) {
        if (!installed) {
            throw new IllegalStateException("Adaptive pattern provider definitions are not installed");
        }
        if (stack.isEmpty() || stack.is(ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem()) || stack.is(ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get())) {
            return null;
        }

        AdaptivePatternProviderRegistration matchedRegistration = null;
        AdaptivePatternProviderProfile matchedProfile = null;
        for (AdaptivePatternProviderRegistration registration : registrations) {
            AdaptivePatternProviderProfile profile;
            try {
                profile = registration.definition().resolve(stack);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Adaptive pattern provider definition {} failed to resolve {}",
                        registration.registrationId(),
                        stack,
                        exception);
                continue;
            }
            if (profile == null) {
                continue;
            }
            if (matchedRegistration != null) {
                throw new IllegalStateException(
                        "Ambiguous adaptive pattern provider definitions " + matchedRegistration.registrationId() + " and " + registration.registrationId() + " for " + stack);
            }
            matchedRegistration = registration;
            matchedProfile = profile;
        }
        return matchedProfile;
    }
}
