package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

/** Verifies persisted access-hatch identifiers from earlier 3.0 builds resolve to the current entries. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DELegacyRegistryAliasesGameTest {

    private DELegacyRegistryAliasesGameTest() {}

    @TestHolder("legacy_trinity_access_hatch_ids_resolve_to_me_access_hatch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void legacyAccessHatchIdsResolveToCurrentEntries(GameTestHelper helper) {
        ResourceLocation legacyId = Data_Energistics.id("trinity_access_hatch");
        helper.assertTrue(
                BuiltInRegistries.BLOCK.get(legacyId) == DEBlocks.TRINITY_ACCESS_HATCH.get(),
                "Legacy block states must resolve to the ME access hatch block");
        helper.assertTrue(
                BuiltInRegistries.ITEM.get(legacyId) == DEItems.TRINITY_ACCESS_HATCH.get(),
                "Legacy item stacks and AE keys must resolve to the ME access hatch item");
        helper.assertTrue(
                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(legacyId) ==
                        DEBlockEntities.TRINITY_ACCESS_HATCH_BLOCK_ENTITY.get(),
                "Legacy block entity NBT must resolve to the ME access hatch block entity type");
        helper.succeed();
    }
}
