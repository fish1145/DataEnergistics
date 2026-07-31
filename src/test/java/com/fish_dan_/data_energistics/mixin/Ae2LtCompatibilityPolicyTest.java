package com.fish_dan_.data_energistics.mixin;

import com.fish_dan_.data_energistics.integration.ae2lt.Ae2LtSoftInterfaceInjector;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

import java.util.EnumSet;
import java.util.List;

import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.EjectOwner.AE2LT;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.EjectOwner.DATA_ENERGISTICS;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.EjectOwner.THUNDERBOLT;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.AE2LT_CHANNEL_HELPER;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.AE2LT_EJECT_INTERCEPTOR;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.AE2LT_EJECT_REGISTRY;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.AE2LT_MAX_FLOW;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.THUNDERBOLT_CHANNEL_HELPER;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.THUNDERBOLT_EJECT_INTERCEPTOR;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.THUNDERBOLT_MAX_FLOW;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.THUNDERBOLT_WIRELESS_CONNECTION_API;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.WIRELESS_HOST_API;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.MixinRole.DATA_EJECT_INTERCEPTOR;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.MixinRole.GENERAL;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.MixinRole.LEGACY_CHANNEL_SOURCE;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.MixinRole.LEGACY_MAX_FLOW_GUARD;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.MixinRole.LEGACY_WIRELESS;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.MixinRole.MODERN_CHANNEL_SOURCE;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.MixinRole.MODERN_MAX_FLOW_GUARD;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.MixinRole.MODERN_WIRELESS_ADAPTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Ae2LtCompatibilityPolicyTest {

    @Test
    void supportedRuntimeMatrixSelectsOneEjectOwnerAndOneWirelessAbi() {
        var legacyFallback = policy(AE2LT_EJECT_REGISTRY);
        assertEquals(DATA_ENERGISTICS, legacyFallback.ejectOwner());
        assertTrue(legacyFallback.shouldApply(DATA_EJECT_INTERCEPTOR));

        var ae2Lt114 = policy(
                AE2LT_EJECT_REGISTRY, AE2LT_EJECT_INTERCEPTOR, AE2LT_MAX_FLOW, AE2LT_CHANNEL_HELPER);
        assertEquals(AE2LT, ae2Lt114.ejectOwner());
        assertFalse(ae2Lt114.shouldApply(DATA_EJECT_INTERCEPTOR));
        assertTrue(ae2Lt114.shouldApply(LEGACY_WIRELESS));
        assertFalse(ae2Lt114.shouldApply(MODERN_WIRELESS_ADAPTER));
        assertTrue(ae2Lt114.shouldApply(LEGACY_MAX_FLOW_GUARD));
        assertTrue(ae2Lt114.shouldApply(LEGACY_CHANNEL_SOURCE));
        assertTrue(ae2Lt114.shouldApply(GENERAL));

        var ae2Lt20 = policy(
                AE2LT_EJECT_REGISTRY,
                THUNDERBOLT_EJECT_INTERCEPTOR,
                THUNDERBOLT_MAX_FLOW,
                THUNDERBOLT_CHANNEL_HELPER,
                WIRELESS_HOST_API,
                THUNDERBOLT_WIRELESS_CONNECTION_API);
        assertEquals(THUNDERBOLT, ae2Lt20.ejectOwner());
        assertFalse(ae2Lt20.shouldApply(DATA_EJECT_INTERCEPTOR));
        assertFalse(ae2Lt20.shouldApply(LEGACY_WIRELESS));
        assertTrue(ae2Lt20.shouldApply(MODERN_WIRELESS_ADAPTER));
        assertTrue(ae2Lt20.shouldApply(MODERN_MAX_FLOW_GUARD));
        assertTrue(ae2Lt20.shouldApply(MODERN_CHANNEL_SOURCE));

        assertFalse(policy(WIRELESS_HOST_API).shouldApply(MODERN_WIRELESS_ADAPTER));

        ClassNode host = new ClassNode();
        Ae2LtSoftInterfaceInjector.apply(
                "com.fish_dan_.data_energistics.mixin.ae2lt.Ae2lt2AdaptivePatternProviderHostMixin", host);
        Ae2LtSoftInterfaceInjector.apply(
                "com.fish_dan_.data_energistics.mixin.ae2lt.Ae2lt2AdaptivePatternProviderHostMixin", host);
        assertEquals(List.of("com/moakiee/ae2lt/api/patternprovider/WirelessPatternProviderHost"), host.interfaces);

        ClassNode connection = new ClassNode();
        Ae2LtSoftInterfaceInjector.apply(
                "com.fish_dan_.data_energistics.mixin.ae2lt.Ae2lt2AdaptiveWirelessConnectionMixin", connection);
        Ae2LtSoftInterfaceInjector.apply("unrelated.Mixin", connection);
        assertEquals(List.of("com/moakiee/thunderbolt/api/wireless/WirelessConnectionRef"),
                connection.interfaces);
    }

    private static Ae2LtCompatibilityPolicy policy(Ae2LtCompatibilityPolicy.Feature... features) {
        EnumSet<Ae2LtCompatibilityPolicy.Feature> detected = EnumSet.noneOf(
                Ae2LtCompatibilityPolicy.Feature.class);
        for (var feature : features) {
            detected.add(feature);
        }
        return new Ae2LtCompatibilityPolicy(detected);
    }
}
