package com.fish_dan_.data_energistics.mixin;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Chooses version-isolated AE2LT mixins from concrete runtime capabilities instead of fragile version strings.
 */
public final class Ae2LtCompatibilityPolicy {

    /**
     * Class-file capabilities discovered without initializing optional-mod classes.
     */
    public enum Feature {
        WIRELESS_HOST_API,
        THUNDERBOLT_WIRELESS_CONNECTION_API,
        AE2LT_EJECT_REGISTRY,
        AE2LT_EJECT_INTERCEPTOR,
        THUNDERBOLT_EJECT_INTERCEPTOR,
        AE2LT_CHANNEL_HELPER,
        THUNDERBOLT_CHANNEL_HELPER
    }

    /**
     * Compatibility role assigned to mixins whose targets differ across AE2LT generations.
     */
    public enum MixinRole {
        GENERAL,
        LEGACY_WIRELESS,
        MODERN_WIRELESS_ADAPTER,
        DATA_EJECT_INTERCEPTOR,
        LEGACY_CHANNEL_SOURCE,
        MODERN_CHANNEL_SOURCE
    }

    /**
     * Single implementation that owns EJECT interception for one supported runtime combination.
     */
    public enum EjectOwner {
        NONE,
        DATA_ENERGISTICS,
        AE2LT,
        THUNDERBOLT
    }

    private final EnumSet<Feature> features;

    /**
     * Creates a policy from an immutable snapshot of discovered class-file capabilities.
     *
     * @param features discovered runtime features
     */
    public Ae2LtCompatibilityPolicy(Set<Feature> features) {
        Objects.requireNonNull(features, "features");
        this.features = features.isEmpty() ? EnumSet.noneOf(Feature.class) : EnumSet.copyOf(features);
    }

    /**
     * Determines whether one version-specific mixin role is valid for the discovered runtime.
     *
     * @param role mixin compatibility role
     * @return whether the mixin should be applied
     */
    public boolean shouldApply(MixinRole role) {
        Objects.requireNonNull(role, "role");
        return switch (role) {
            case GENERAL -> true;
            case LEGACY_WIRELESS -> !has(Feature.WIRELESS_HOST_API);
            case MODERN_WIRELESS_ADAPTER -> has(Feature.WIRELESS_HOST_API) && has(Feature.THUNDERBOLT_WIRELESS_CONNECTION_API);
            case DATA_EJECT_INTERCEPTOR -> ejectOwner() == EjectOwner.DATA_ENERGISTICS;
            case LEGACY_CHANNEL_SOURCE -> has(Feature.AE2LT_CHANNEL_HELPER);
            case MODERN_CHANNEL_SOURCE -> has(Feature.THUNDERBOLT_CHANNEL_HELPER);
        };
    }

    /**
     * Selects the sole DataE-controlled EJECT owner. Existing native interceptors always take precedence.
     *
     * @return selected interceptor owner
     */
    public EjectOwner ejectOwner() {
        if (has(Feature.THUNDERBOLT_EJECT_INTERCEPTOR)) {
            return EjectOwner.THUNDERBOLT;
        }
        if (has(Feature.AE2LT_EJECT_INTERCEPTOR)) {
            return EjectOwner.AE2LT;
        }
        if (has(Feature.AE2LT_EJECT_REGISTRY)) {
            return EjectOwner.DATA_ENERGISTICS;
        }
        return EjectOwner.NONE;
    }

    /**
     * Tests one discovered capability.
     */
    private boolean has(Feature feature) {
        return this.features.contains(feature);
    }
}
