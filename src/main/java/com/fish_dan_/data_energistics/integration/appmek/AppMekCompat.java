package com.fish_dan_.data_energistics.integration.appmek;

import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderReturnChemicalHandler;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class AppMekCompat {

    private AppMekCompat() {}

    @Nullable
    public static Object createReturnChemicalHandler(Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier) {
        return new AdaptivePatternProviderReturnChemicalHandler(logicSupplier);
    }

    public static void registerChemicalBlockEntityCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                getChemicalBlockCapability(),
                DEBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> asChemicalHandler(blockEntity.getExternalReturnChemicalHandler(context)));
    }

    public static void registerChemicalCableBusCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                getChemicalBlockCapability(),
                AEBlockEntities.CABLE_BUS.get(),
                (CableBusBlockEntity blockEntity, @Nullable Direction context) -> {
                    if (context == null) {
                        return null;
                    }

                    var part = blockEntity.getPart(context);
                    if (part instanceof AdaptivePatternProviderPart adaptivePart) {
                        return asChemicalHandler(adaptivePart.getExternalReturnChemicalHandler());
                    }

                    return null;
                });
    }

    @Nullable
    private static IChemicalHandler asChemicalHandler(@Nullable Object handler) {
        return handler instanceof IChemicalHandler chemicalHandler ? chemicalHandler : null;
    }

    private static BlockCapability<IChemicalHandler, @Nullable Direction> getChemicalBlockCapability() {
        return Capabilities.CHEMICAL.block();
    }
}
