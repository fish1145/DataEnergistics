package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.registry.ModFluids;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class ModFluidClientExtensions {

    private static final ResourceLocation ENDER_STILL = ResourceLocation.fromNamespaceAndPath("data_energistics", "block/fluid/ender_still");
    private static final ResourceLocation ENDER_FLOW = ResourceLocation.fromNamespaceAndPath("data_energistics", "block/fluid/ender_flow");
    private static final ResourceLocation DATA_CORROSION_LIQUID_STILL = ResourceLocation.fromNamespaceAndPath("data_energistics", "block/fluid/data_corrosion_liquid_still");
    private static final ResourceLocation DATA_CORROSION_LIQUID_FLOW = ResourceLocation.fromNamespaceAndPath("data_energistics", "block/fluid/data_corrosion_liquid_flow");
    private static final ResourceLocation WATER_OVERLAY = ResourceLocation.withDefaultNamespace("block/water_overlay");

    private ModFluidClientExtensions() {}

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new TintedFluidTypeExtensions(0xFFFFFFFF, ENDER_STILL, ENDER_FLOW), ModFluids.ENDER_TYPE);
        event.registerFluidType(
                new TintedFluidTypeExtensions(0xFFFFFFFF, DATA_CORROSION_LIQUID_STILL, DATA_CORROSION_LIQUID_FLOW),
                ModFluids.DATA_CORROSION_LIQUID_TYPE);
    }

    private record TintedFluidTypeExtensions(int tintColor, ResourceLocation stillTexture,
                                             ResourceLocation flowingTexture)
            implements IClientFluidTypeExtensions {

        @Override
        public int getTintColor() {
            return tintColor;
        }

        @Override
        public ResourceLocation getStillTexture() {
            return stillTexture;
        }

        @Override
        public ResourceLocation getFlowingTexture() {
            return flowingTexture;
        }

        @Override
        public ResourceLocation getOverlayTexture() {
            return WATER_OVERLAY;
        }
    }
}
