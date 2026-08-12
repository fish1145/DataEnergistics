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

import java.util.List;

/** Verifies persisted information-exchange-depot identifiers from earlier 3.0 builds resolve to the current entries. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DELegacyRegistryAliasesGameTest {

    private DELegacyRegistryAliasesGameTest() {}

    @TestHolder("legacy_information_exchange_depot_ids_resolve_to_current_entries")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void legacyAccessHatchIdsResolveToCurrentEntries(GameTestHelper helper) {
        for (ResourceLocation legacyId : List.of(
                Data_Energistics.id("trinity_access_hatch"),
                Data_Energistics.id("me_access_hatch"))) {
            helper.assertTrue(
                    BuiltInRegistries.BLOCK.get(legacyId) == DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT.get(),
                    "Legacy block states must resolve directly to the Trinity information exchange depot block");
            helper.assertTrue(
                    BuiltInRegistries.ITEM.get(legacyId) == DEItems.TRINITY_INFORMATION_EXCHANGE_DEPOT.get(),
                    "Legacy item stacks and AE keys must resolve directly to the Trinity information exchange depot item");
            helper.assertTrue(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.get(legacyId) ==
                            DEBlockEntities.TRINITY_INFORMATION_EXCHANGE_DEPOT_BLOCK_ENTITY.get(),
                    "Legacy block entity NBT must resolve directly to the Trinity information exchange depot block entity type");
        }
        helper.succeed();
    }
}
