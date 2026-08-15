package com.fish_dan_.data_energistics.client.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEFluids;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.function.IntSupplier;

public final class DEFluidClientExtensions {

    private static final ResourceLocation ENDER_STILL = Data_Energistics.id("block/fluid/ender_still");
    private static final ResourceLocation ENDER_FLOW = Data_Energistics.id("block/fluid/ender_flow");
    private static final ResourceLocation DATA_CORROSION_LIQUID_STILL = Data_Energistics.id("block/fluid/data_corrosion_liquid_still");
    private static final ResourceLocation DATA_CORROSION_LIQUID_FLOW = Data_Energistics.id("block/fluid/data_corrosion_liquid_flow");
    private static final ResourceLocation WATER_OVERLAY = ResourceLocation.withDefaultNamespace("block/water_overlay");

    private DEFluidClientExtensions() {}

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new TintedFluidTypeExtensions(
                () -> ((DEFluids.ClientTintedFluidType) DEFluids.ENDER_TYPE.get()).getTintColor(),
                ENDER_STILL,
                ENDER_FLOW), DEFluids.ENDER_TYPE);
        event.registerFluidType(
                new TintedFluidTypeExtensions(
                        () -> ((DEFluids.ClientTintedFluidType) DEFluids.DATA_CORROSION_LIQUID_TYPE.get()).getTintColor(),
                        DATA_CORROSION_LIQUID_STILL,
                        DATA_CORROSION_LIQUID_FLOW),
                DEFluids.DATA_CORROSION_LIQUID_TYPE);
    }

    private record TintedFluidTypeExtensions(IntSupplier tintColor, ResourceLocation stillTexture,
                                             ResourceLocation flowingTexture)
            implements IClientFluidTypeExtensions {

        @Override
        public int getTintColor() {
            return this.tintColor.getAsInt();
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
