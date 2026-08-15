package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

final class BuiltinDataPackRegistrar {

    private static final ResourceLocation MODPACK_FIXES_PACK = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "resourcepacks/modpack_fixes");

    private BuiltinDataPackRegistrar() {}

    static void register(AddPackFindersEvent event) {
        event.addPackFinders(
                MODPACK_FIXES_PACK,
                PackType.SERVER_DATA,
                Component.literal("Data Energistics Modpack Fixes"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP);
    }
}
