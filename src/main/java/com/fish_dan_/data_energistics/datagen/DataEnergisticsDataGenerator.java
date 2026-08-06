package com.fish_dan_.data_energistics.datagen;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class DataEnergisticsDataGenerator {

    private DataEnergisticsDataGenerator() {}

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        var generator = event.getGenerator();
        var packOutput = generator.getPackOutput();
        var lookupProvider = event.getLookupProvider();

        generator.addProvider(
                event.includeServer(),
                new ModRecipeProvider(packOutput, lookupProvider));
    }
}
