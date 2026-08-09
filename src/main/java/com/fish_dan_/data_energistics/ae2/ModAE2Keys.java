package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKeyType;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.DataKeyType;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.ae2.key.EchoKeyType;

import net.neoforged.neoforge.registries.RegisterEvent;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import java.util.List;

/**
 * Owns the complete catalog of custom AE keys and key types supplied by Data Energistics.
 */
public final class ModAE2Keys {

    private static final List<AEKeyType> TYPES = List.of(
            DataFlowKeyType.TYPE,
            DataKeyType.TYPE,
            EchoKeyType.TYPE);
    private static final List<AEKey> KEYS = List.of(
            DataFlowKey.of(),
            DataKey.of(),
            EchoKey.of());

    private ModAE2Keys() {}

    public static void register(RegisterEvent event) {
        for (AEKeyType type : TYPES) {
            event.register(AEKeyType.REGISTRY_KEY, type.getId(), () -> type);
        }
    }

    /**
     * Returns the ordered key-type catalog used for registration and generic integration hooks.
     */
    public static List<AEKeyType> types() {
        return TYPES;
    }

    /**
     * Returns the singleton keys corresponding one-to-one with {@link #types()}.
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
