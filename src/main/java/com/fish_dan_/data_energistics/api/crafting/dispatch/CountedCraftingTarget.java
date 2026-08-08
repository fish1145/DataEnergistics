package com.fish_dan_.data_energistics.api.crafting.dispatch;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Immutable provider-local route optionally linked to a provider-independent physical machine identity.
 *
 * <p>
 * The route identity must remain stable for the lifetime of one provider instance. Two adapters that can reach the
 * same physical machine should publish the same machine identity so Data Energistics can avoid overselling it.
 * </p>
 *
 * @param providerScoped  whether this is the single aggregate provider target
 * @param stableIdentity  provider-local route identity
 * @param machineIdentity provider-independent physical machine identity, or empty when it cannot be proven
 */
public record CountedCraftingTarget(boolean providerScoped,
                                    @NotNull String stableIdentity,
                                    @NotNull Optional<@NotNull String> machineIdentity) {

    /**
     * Shared conservative target for adapters that expose no independently routable machine.
     */
    private static final String PROVIDER_IDENTITY = "provider";
    private static final CountedCraftingTarget PROVIDER = new CountedCraftingTarget(true, PROVIDER_IDENTITY, Optional.empty());

    /**
     * Validates and freezes the public target identity before it enters capacity planning.
     */
    public CountedCraftingTarget {
        if (stableIdentity.isBlank()) {
            throw new IllegalArgumentException("Counted crafting target identity must not be blank");
        }
        if (providerScoped && (!PROVIDER_IDENTITY.equals(stableIdentity) || machineIdentity.isPresent())) {
            throw new IllegalArgumentException("Aggregate provider target has invalid route or machine identity");
        }
        machineIdentity.ifPresent(identity -> {
            if (identity.isBlank()) {
                throw new IllegalArgumentException("Counted crafting machine identity must not be blank");
            }
        });
    }

    /**
     * Returns the conservative provider-level route used by legacy counted adapters.
     *
     * @return shared provider route
     */
    public static @NotNull CountedCraftingTarget provider() {
        return PROVIDER;
    }

    /**
     * Creates a provider-local route without claiming a physical machine identity.
     *
     * @param stableIdentity provider-local route identity
     * @return immutable route
     */
    public static @NotNull CountedCraftingTarget route(@NotNull String stableIdentity) {
        return new CountedCraftingTarget(false, stableIdentity, Optional.empty());
    }

    /**
     * Creates a provider-local route linked to a stable physical machine.
     *
     * @param stableIdentity  provider-local route identity
     * @param machineIdentity provider-independent machine identity
     * @return immutable targeted-machine route
     */
    public static @NotNull CountedCraftingTarget machine(
            @NotNull String stableIdentity,
            @NotNull String machineIdentity) {
        return new CountedCraftingTarget(false, stableIdentity, Optional.of(machineIdentity));
    }
}
