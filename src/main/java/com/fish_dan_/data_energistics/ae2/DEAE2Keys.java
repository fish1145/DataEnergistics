package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.ae2.key.ManifestBinaryKeyType;

import net.neoforged.neoforge.registries.IRegistryExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import java.util.List;

/**
 * Owns the complete catalog of custom AE keys and key types supplied by Data Energistics.
 */
public final class DEAE2Keys {

    private static final List<AEKeyType> TYPES = List.of(
            DigitalizationKeyType.TYPE,
            ManifestBinaryKeyType.TYPE);
    private static final List<AEKey> KEYS = List.of(
            DataFlowKey.of(),
            DataKey.of(),
            EchoKey.of(),
            CelestialEnergyKey.of());

    private DEAE2Keys() {}

    public static void register(RegisterEvent event) {
        if (!event.getRegistryKey().equals(AEKeyType.REGISTRY_KEY)) {
            return;
        }
        for (AEKeyType type : TYPES) {
            event.register(AEKeyType.REGISTRY_KEY, type.getId(), () -> type);
        }
        IRegistryExtension<?> registry = (IRegistryExtension<?>) event.getRegistry();
        registry.addAlias(DataFlowKey.ID, DigitalizationKeyType.TYPE.getId());
        registry.addAlias(EchoKey.ID, DigitalizationKeyType.TYPE.getId());
        registry.addAlias(CelestialEnergyKey.ID, DigitalizationKeyType.TYPE.getId());
        registry.addAlias(DataKey.ID, ManifestBinaryKeyType.TYPE.getId());
    }

    /**
     * Returns the ordered key-type catalog used for registration and generic integration hooks.
     */
    public static List<AEKeyType> types() {
        return TYPES;
    }

    /**
     * Returns every singleton key supplied by this mod in stable display order.
     */
    public static List<AEKey> keys() {
        return KEYS;
    }

    /**
     * Identifies whether a type belongs to this mod's custom resource catalog.
     */
    public static boolean isCustomType(AEKeyType type) {
        return type != null && TYPES.contains(type);
    }

    /**
     * Identifies whether a key belongs to this mod's custom resource catalog.
     */
    public static boolean isCustomKey(AEKey key) {
        return key != null && isCustomType(key.getType());
    }
}
