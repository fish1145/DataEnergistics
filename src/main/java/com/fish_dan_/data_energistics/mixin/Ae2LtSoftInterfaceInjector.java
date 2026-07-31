package com.fish_dan_.data_energistics.mixin;

import org.objectweb.asm.tree.ClassNode;

import java.util.Objects;

/**
 * Adds AE2LT 2.0 public interfaces by internal name while the project remains compiled against the published 1.1.4 API.
 */
public final class Ae2LtSoftInterfaceInjector {

    private static final String HOST_MIXIN = "com.fish_dan_.data_energistics.mixin.ae2lt.Ae2lt2AdaptivePatternProviderHostMixin";
    private static final String CONNECTION_MIXIN = "com.fish_dan_.data_energistics.mixin.ae2lt.Ae2lt2AdaptiveWirelessConnectionMixin";
    private static final String WIRELESS_HOST_INTERFACE = "com/moakiee/ae2lt/api/patternprovider/WirelessPatternProviderHost";
    private static final String WIRELESS_CONNECTION_INTERFACE = "com/moakiee/thunderbolt/api/wireless/WirelessConnectionRef";

    private Ae2LtSoftInterfaceInjector() {}

    /**
     * Applies the interface required by a known soft-adapter mixin and ignores unrelated mixins.
     *
     * @param mixinClassName fully qualified mixin class name
     * @param targetClass    target bytecode being transformed
     */
    public static void apply(String mixinClassName, ClassNode targetClass) {
        Objects.requireNonNull(mixinClassName, "mixinClassName");
        Objects.requireNonNull(targetClass, "targetClass");
        if (HOST_MIXIN.equals(mixinClassName)) {
            addInterface(targetClass, WIRELESS_HOST_INTERFACE);
        } else if (CONNECTION_MIXIN.equals(mixinClassName)) {
            addInterface(targetClass, WIRELESS_CONNECTION_INTERFACE);
        }
    }

    /**
     * Adds one interface idempotently because Mixin may revisit a target during audit/export passes.
     */
    private static void addInterface(ClassNode targetClass, String interfaceName) {
        if (!targetClass.interfaces.contains(interfaceName)) {
            targetClass.interfaces.add(interfaceName);
        }
    }
}
