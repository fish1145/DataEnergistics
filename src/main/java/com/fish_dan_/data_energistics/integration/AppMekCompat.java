package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class AppMekCompat {

    private static final String CHEMICAL_CAPABILITIES_CLASS = "mekanism.common.capabilities.Capabilities";
    private static final String CHEMICAL_HANDLER_CLASS = "com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnChemicalHandler";

    private AppMekCompat() {}

    private static boolean isChemicalSupportLoaded() {
        return ModFlags.isMekanismLoaded() && ModFlags.isAppMekLoaded();
    }

    @Nullable
    public static Object createReturnChemicalHandler(Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier) {
        if (!isChemicalSupportLoaded()) {
            return null;
        }

        try {
            Class<?> handlerClass = Class.forName(CHEMICAL_HANDLER_CLASS);
            Constructor<?> constructor = handlerClass.getConstructor(Supplier.class);
            return constructor.newInstance(logicSupplier);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static void registerChemicalBlockEntityCapabilities(RegisterCapabilitiesEvent event) {
        if (!isChemicalSupportLoaded()) {
            return;
        }

        BlockCapability<Object, Direction> chemicalCapability = getChemicalBlockCapability();
        if (chemicalCapability == null) {
            return;
        }

        event.registerBlockEntity(
                chemicalCapability,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalReturnChemicalHandler(context));
    }

    public static void registerChemicalCableBusCapabilities(RegisterCapabilitiesEvent event) {
        if (!isChemicalSupportLoaded()) {
            return;
        }

        BlockCapability<Object, Direction> chemicalCapability = getChemicalBlockCapability();
        if (chemicalCapability == null) {
            return;
        }

        event.registerBlockEntity(
                chemicalCapability,
                AEBlockEntities.CABLE_BUS.get(),
                (CableBusBlockEntity blockEntity, Direction context) -> {
                    if (context == null) {
                        return null;
                    }

                    var part = blockEntity.getPart(context);
                    if (part instanceof AdaptivePatternProviderPart adaptivePart) {
                        return adaptivePart.getExternalReturnChemicalHandler();
                    }

                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static BlockCapability<Object, Direction> getChemicalBlockCapability() {
        try {
            Class<?> capabilitiesClass = Class.forName(CHEMICAL_CAPABILITIES_CLASS);
            Object chemicalCapabilityHolder = capabilitiesClass.getField("CHEMICAL").get(null);
            Method blockMethod = chemicalCapabilityHolder.getClass().getMethod("block");
            return (BlockCapability<Object, Direction>) blockMethod.invoke(chemicalCapabilityHolder);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
