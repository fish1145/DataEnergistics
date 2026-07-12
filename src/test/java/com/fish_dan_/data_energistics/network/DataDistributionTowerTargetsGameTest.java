package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetKind;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.util.PinyinUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.List;

/** Verifies target search after the client has atomically assembled multiple payload batches. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataDistributionTowerTargetsGameTest {

    private DataDistributionTowerTargetsGameTest() {}

    @TestHolder("data_distribution_tower_searches_after_first_target_batch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void searchesAfterFirstTargetBatch(GameTestHelper helper) {
        List<DataDistributionTowerTargetEntry> entries = entries(70);
        List<DataDistributionTowerTargetsPayload> payloads = DataDistributionTowerTargetsPayload.batches(17, 23L, entries);
        DataDistributionTowerTargetsAssembler assembler = new DataDistributionTowerTargetsAssembler();

        helper.assertTrue(assembler.accept(payloads.getFirst()).isEmpty(),
                "The first target batch must not publish a partial GUI list");
        DataDistributionTowerTargetsSnapshot snapshot = assembler.accept(payloads.getLast()).orElseThrow();
        String filter = PinyinUtil.normalizeSearch("Target 69");
        List<DataDistributionTowerTargetEntry> matches = snapshot.entries().stream()
                .filter(entry -> PinyinUtil.matchesSearch(
                        entry.displayName() + " (" + entry.kind().name() + ")", filter))
                .toList();

        helper.assertValueEqual(snapshot.entries().size(), 70, "Both target batches must publish atomically");
        helper.assertValueEqual(matches.size(), 1, "Search must find exactly one target after entry 64");
        helper.assertValueEqual(matches.getFirst(), entries.get(69), "Search must return target 69");
        helper.succeed();
    }

    private static List<DataDistributionTowerTargetEntry> entries(int count) {
        ArrayList<DataDistributionTowerTargetEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new DataDistributionTowerTargetEntry(
                    ResourceLocation.parse("minecraft:stone"),
                    "Target " + index,
                    1,
                    ResourceLocation.parse("minecraft:overworld"),
                    new BlockPos(index, 64, 0),
                    TargetKind.AE,
                    TargetTransferMode.AUTO,
                    new TargetTransferInfo(index, true, false, 0L, 0L, false, false)));
        }
        return List.copyOf(entries);
    }
}
