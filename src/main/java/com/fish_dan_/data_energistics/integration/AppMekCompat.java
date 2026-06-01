package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import org.jetbrains.annotations.Nullable;

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

        return ReflectionAccess.newInstance(CHEMICAL_HANDLER_CLASS, new Class<?>[] { Supplier.class }, logicSupplier);
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
        Object chemicalCapabilityHolder = ReflectionAccess.getField(
                ReflectionAccess.findStaticField(CHEMICAL_CAPABILITIES_CLASS, "CHEMICAL"),
                null);
        Object capability = ReflectionAccess.invokeNoArg(chemicalCapabilityHolder, "block");
        return capability instanceof BlockCapability<?, ?> blockCapability ? (BlockCapability<Object, Direction>) blockCapability : null;
    }
}
