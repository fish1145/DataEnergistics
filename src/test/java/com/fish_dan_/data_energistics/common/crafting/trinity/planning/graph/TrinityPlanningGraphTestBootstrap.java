package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.RegistryBuilder;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;

import java.util.List;
import java.util.Map;

/**
 * Initializes the minimal Minecraft and AE key registries needed by direct graph logic tests.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TrinityPlanningGraphTestBootstrap {

    private TrinityPlanningGraphTestBootstrap() {}

    public static void initialize() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        initializeAeKeyTypes();
    }

    private static void initializeAeKeyTypes() {
        synchronized (AEKeyTypesInternal.class) {
            boolean initialized;
            try {
                initialized = AEKeyTypesInternal.getRegistry() != null;
            } catch (IllegalStateException notInitialized) {
                initialized = false;
            }
            if (!initialized) {
                Registry<AEKeyType> registry = new RegistryBuilder<>(AEKeyType.REGISTRY_KEY)
                        .disableRegistrationCheck()
                        .create();
                AEKeyTypesInternal.setRegistry(registry);
                Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
                Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
                registry.freeze();
            }
        }
    }
}
