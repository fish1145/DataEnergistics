package com.fish_dan_.data_energistics.client;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.stacks.AEFluidKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

public final class CustomKeyGuiRendererTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        if (ModList.get() == null) {
            ModList.of(List.of(), List.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void delegatesFluidRenderingToAe2() {
        assertFalse(CustomKeyGuiRenderer.drawCustom(
                null,
                0,
                0,
                AEFluidKey.of(Fluids.WATER)));
    }
}
