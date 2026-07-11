package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MultiBlockAutoBuildOverlayTest {

    @Test
    void genericDescriptionSupportsDeclaredStructureIdsAndTheFifthSharedIcon() {
        List<MultiBlockAutoBuildSelection> selections = new ArrayList<>();
        MultiBlockAutoBuildOverlay overlay = new MultiBlockAutoBuildOverlay(new MultiBlockAutoBuildOverlayDescription(
                Component.literal("Generic Builder"),
                List.of(
                        structure(7, 4, 1, 1, 1, 2),
                        structure(11, 1, 2, 4, 6, 8)),
                selections::add));

        overlay.selectStructure(11);
        overlay.setRepeatCount(4);
        overlay.setSelectedTierValue(8);
        overlay.setBuildRequested(false);

        MultiBlockAutoBuildSelection selection = overlay.createSelection();

        assertEquals(11, selection.structureId());
        assertFalse(selection.buildRequested());
        assertEquals(4, selection.repeatCount());
        assertEquals(8, selection.tierValue());
        assertTrue(selections.isEmpty());
    }

    @Test
    void genericOverlayKeepsFixedRepeatCountsAndUsesItsDescriptionCallback() {
        List<MultiBlockAutoBuildSelection> selections = new ArrayList<>();
        MultiBlockAutoBuildOverlay overlay = new MultiBlockAutoBuildOverlay(new MultiBlockAutoBuildOverlayDescription(
                Component.literal("Generic Builder"),
                List.of(structure(7, 0, 1, 1, 1, 2)),
                selections::add));

        assertEquals(1, overlay.repeatCount());
        assertThrows(IllegalArgumentException.class, () -> overlay.setRepeatCount(2));

        overlay.updateViewport(320, 240, new Rect2i(64, 48, 180, 132));
        overlay.toggle();
        Rect2i bounds = overlay.bounds();
        assertTrue(overlay.mouseClicked(bounds.getX() + 10, bounds.getY() + 110, 0));

        assertEquals(List.of(new MultiBlockAutoBuildSelection(7, true, 1, 1)), selections);
    }

    @Test
    void trinityRegistersOnlyItsThreeStructuresWhileTheSharedOverlaySupportsFiveIcons() {
        MultiBlockAutoBuildOverlayDescription description = TrinityDataCoreScreen.createAutoBuildDescription(
                MultiBlockAutoBuildOverlayTest::rejectUnexpectedSelection);

        assertEquals(3, description.structures().size());
        assertEquals(TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX, description.structures().get(0).id());
        assertEquals(TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX, description.structures().get(1).id());
        assertEquals(TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX, description.structures().get(2).id());
        assertEquals(0, description.structures().get(0).iconIndex());
        assertEquals(1, description.structures().get(1).iconIndex());
        assertEquals(2, description.structures().get(2).iconIndex());
        assertFalse(description.structures().get(0).repeatable());
        assertTrue(description.structures().get(1).repeatable());
        assertTrue(description.structures().get(2).repeatable());
        assertEquals(10, description.structures().get(0).tierOptions().size());
        assertEquals(10, description.structures().get(1).tierOptions().size());
        assertEquals(3, description.structures().get(2).tierOptions().size());
    }

    @Test
    void trinityOverlayKeepsMainFixedWhileExposingCpuAndCraftingChoices() {
        MultiBlockAutoBuildOverlay overlay = new MultiBlockAutoBuildOverlay(
                TrinityDataCoreScreen.createAutoBuildDescription(
                        MultiBlockAutoBuildOverlayTest::rejectUnexpectedSelection));

        overlay.setSelectedTierValue(10);
        assertEquals(1, overlay.createSelection().repeatCount());
        assertEquals(10, overlay.createSelection().tierValue());

        overlay.selectStructure(TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX);
        overlay.setRepeatCount(1);
        assertEquals(1, overlay.repeatCount());
        overlay.setRepeatCount(12);
        overlay.setSelectedTierValue(10);
        assertEquals(12, overlay.createSelection().repeatCount());
        assertEquals(10, overlay.createSelection().tierValue());

        overlay.selectStructure(TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX);
        overlay.setSelectedTierValue(3);
        assertEquals(3, overlay.createSelection().tierValue());
    }

    @Test
    void trinityOverlayPersistsEachStructureChoiceAcrossCloseAndReopen() {
        MultiBlockAutoBuildOverlay overlay = new MultiBlockAutoBuildOverlay(
                TrinityDataCoreScreen.createAutoBuildDescription(
                        MultiBlockAutoBuildOverlayTest::rejectUnexpectedSelection));

        assertTrue(overlay.buildRequested());
        overlay.setBuildRequested(false);
        overlay.setRepeatCount(1);
        overlay.setSelectedTierValue(10);

        overlay.selectStructure(TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX);
        assertFalse(overlay.buildRequested());
        overlay.setBuildRequested(true);
        overlay.setRepeatCount(6);
        overlay.setSelectedTierValue(7);

        overlay.selectStructure(TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX);
        assertFalse(overlay.buildRequested());
        overlay.setBuildRequested(false);
        overlay.setRepeatCount(3);
        overlay.setSelectedTierValue(3);

        overlay.close();
        overlay.toggle();

        overlay.selectStructure(TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX);
        assertFalse(overlay.buildRequested());
        assertEquals(1, overlay.repeatCount());
        assertEquals(10, overlay.selectedTierValue());

        overlay.selectStructure(TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX);
        assertTrue(overlay.buildRequested());
        assertEquals(6, overlay.repeatCount());
        assertEquals(7, overlay.selectedTierValue());

        overlay.selectStructure(TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX);
        assertFalse(overlay.buildRequested());
        assertEquals(3, overlay.repeatCount());
        assertEquals(3, overlay.selectedTierValue());
    }

    @Test
    void trinityAdapterConvertsGenericSelectionsToExactTierCategories() {
        TrinityAutoBuildRequest main = TrinityDataCoreScreen.toTrinityAutoBuildRequest(
                new MultiBlockAutoBuildSelection(TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX, true, 1, 10));
        TrinityAutoBuildRequest cpu = TrinityDataCoreScreen.toTrinityAutoBuildRequest(
                new MultiBlockAutoBuildSelection(TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX, false, 12, 10));
        TrinityAutoBuildRequest crafting = TrinityDataCoreScreen.toTrinityAutoBuildRequest(
                new MultiBlockAutoBuildSelection(TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX, true, 1, 3));

        assertEquals(10, main.options().tierSelections().get(TrinityAutoBuildBlockMap.STORAGE_CORE));
        assertEquals(1, main.options().repeatCount());
        assertFalse(cpu.options().buildRequested());
        assertEquals(12, cpu.options().repeatCount());
        assertEquals(10, cpu.options().tierSelections().get(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE));
        assertEquals(3, crafting.options().tierSelections().get(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE));
    }

    private static MultiBlockAutoBuildOverlayDescription.Structure structure(int id,
                                                                             int iconIndex,
                                                                             int minimumRepeatCount,
                                                                             int maximumRepeatCount,
                                                                             int firstTier,
                                                                             int secondTier) {
        return new MultiBlockAutoBuildOverlayDescription.Structure(
                id,
                iconIndex,
                Component.literal("Structure " + id),
                Component.literal("Tier"),
                minimumRepeatCount,
                maximumRepeatCount,
                List.of(
                        new MultiBlockAutoBuildOverlayDescription.TierOption(firstTier, Component.literal("First")),
                        new MultiBlockAutoBuildOverlayDescription.TierOption(secondTier, Component.literal("Second"))));
    }

    private static void rejectUnexpectedSelection(MultiBlockAutoBuildSelection selection) {
        throw new AssertionError("Test did not expect the generic overlay to submit a selection: " + selection);
    }
}
