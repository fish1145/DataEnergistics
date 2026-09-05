package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderCapabilities;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderProfile;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Declarative built-in adaptive provider profiles for AE2 and supported integrations.
 *
 * <p>
 * Optional-mod entries match registry IDs only, so this catalog can be loaded without linking an optional mod's
 * implementation classes. Integrations can replace these compatibility declarations with their own public
 * entrypoints without adding recognition branches to the resolver.
 * </p>
 */
@DataEnergisticsEntrypoint
public final class AdaptivePatternProviderBuiltIns implements DataEnergisticsPlugin {

    private static final int SIMPLE_PATTERN_SLOTS = 5;
    private static final int BASE_PATTERN_SLOTS = 9;
    private static final int EXTENDED_PATTERN_SLOTS = 36;
    private static final int METEORITE_PATTERN_SLOTS = 63;

    /**
     * Public constructor required by the common entrypoint scanner.
     */
    public AdaptivePatternProviderBuiltIns() {}

    /**
     * Registers every built-in profile through the same staged lifecycle used by integrations.
     */
    @Override
    public void register(DataEnergisticsRegistry registry) {
        registrations().forEach(registry.adaptivePatternProviders()::register);
    }

    /**
     * Returns every built-in definition in deterministic registration order.
     *
     * @return immutable built-in registration list
     */
    public static List<AdaptivePatternProviderRegistration> registrations() {
        return List.of(
                fixed(
                        "ae2/standard",
                        stack -> AEBlocks.PATTERN_PROVIDER.is(stack) || AEParts.PATTERN_PROVIDER.is(stack),
                        BASE_PATTERN_SLOTS,
                        Set.of()),
                fixed(
                        "ae2cs/simple",
                        itemIds("ae2cs:simple_pattern_provider", "ae2cs:simple_pattern_provider_part"),
                        SIMPLE_PATTERN_SLOTS,
                        Set.of()),
                fixed(
                        "ae2cs/resonating",
                        itemIds("ae2cs:resonating_pattern_provider", "ae2cs:resonating_pattern_provider_part"),
                        BASE_PATTERN_SLOTS,
                        Set.of(AdaptivePatternProviderCapabilities.RESONATING)),
                fixed(
                        "ae2cs/extended_resonating",
                        itemIds(
                                "ae2cs:extended_resonating_pattern_provider",
                                "ae2cs:extended_resonating_pattern_provider_part",
                                "ae2cs:ex_resonating_pattern_provider",
                                "ae2cs:ex_resonating_pattern_provider_part"),
                        EXTENDED_PATTERN_SLOTS,
                        Set.of(AdaptivePatternProviderCapabilities.RESONATING)),
                fixed(
                        "ae2cs/meteorite",
                        itemIds("ae2cs:meteorite_pattern_provider", "ae2cs:meteorite_pattern_provider_part"),
                        METEORITE_PATTERN_SLOTS,
                        Set.of(AdaptivePatternProviderCapabilities.METEORITE)),
                fixed(
                        "advanced_ae/small",
                        itemIds(
                                "advanced_ae:small_adv_pattern_provider",
                                "advanced_ae:small_adv_pattern_provider_part"),
                        BASE_PATTERN_SLOTS,
                        Set.of(
                                AdaptivePatternProviderCapabilities.ADVANCED_PATTERN,
                                AdaptivePatternProviderCapabilities.FILTERED_IMPORT)),
                fixed(
                        "advanced_ae/extended",
                        itemIds("advanced_ae:adv_pattern_provider", "advanced_ae:adv_pattern_provider_part"),
                        EXTENDED_PATTERN_SLOTS,
                        Set.of(
                                AdaptivePatternProviderCapabilities.ADVANCED_PATTERN,
                                AdaptivePatternProviderCapabilities.FILTERED_IMPORT)),
                fixed(
                        "appliedcreate/andesite",
                        itemIds("appliedcreate:andesite_pattern_provider"),
                        BASE_PATTERN_SLOTS,
                        Set.of(AdaptivePatternProviderCapabilities.MECHANICAL_CRAFTING)),
                fixed(
                        "appliedcreate/brass",
                        itemIds("appliedcreate:brass_pattern_provider"),
                        EXTENDED_PATTERN_SLOTS,
                        Set.of(AdaptivePatternProviderCapabilities.MECHANICAL_CRAFTING)),
                fixed(
                        "extendedae/extended",
                        itemIds(
                                "extendedae:ex_pattern_provider",
                                "extendedae:ex_pattern_provider_part",
                                "extendedae:wireless_ex_pat"),
                        EXTENDED_PATTERN_SLOTS,
                        Set.of()));
    }

    /**
     * Creates one complete fixed profile when its item matcher accepts the installed stack.
     */
    private static AdaptivePatternProviderRegistration fixed(
                                                             String path,
                                                             Predicate<ItemStack> matcher,
                                                             int slotsPerProvider,
                                                             Set<ResourceLocation> capabilities) {
        return new AdaptivePatternProviderRegistration(
                Data_Energistics.id("adaptive_pattern_provider/" + path),
                providerStack -> {
                    if (!matcher.test(providerStack)) {
                        return null;
                    }
                    ItemStack icon = providerStack.copyWithCount(1);
                    AEItemKey terminalIcon = AEItemKey.of(icon);
                    if (terminalIcon == null) {
                        throw new IllegalStateException("Adaptive pattern provider item has no AE item key");
                    }
                    return new AdaptivePatternProviderProfile(
                            slotsPerProvider,
                            icon,
                            terminalIcon,
                            icon.getHoverName(),
                            capabilities);
                });
    }

    /**
     * Creates a registry-ID matcher without linking optional implementation classes.
     */
    private static Predicate<ItemStack> itemIds(String... ids) {
        Set<ResourceLocation> itemIds = Arrays.stream(ids)
                .map(ResourceLocation::parse)
                .collect(Collectors.toUnmodifiableSet());
        return stack -> itemIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
