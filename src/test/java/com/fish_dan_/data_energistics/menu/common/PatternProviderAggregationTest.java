package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.util.PatternProviderNameHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternProviderAggregationTest {

    private static final ResourceLocation CRAFTING_TABLE = ResourceLocation.withDefaultNamespace("crafting_table");
    private static final ResourceLocation FURNACE = ResourceLocation.withDefaultNamespace("furnace");

    @Test
    void mergesOrdinaryProvidersWithSameIconAndDisplayName() {
        PatternContainer first = new OrdinaryPatternProvider();
        PatternContainer second = new OrdinaryPatternProvider();
        Map<Long, List<PatternContainer>> targetsById = new HashMap<>();

        var result = aggregate(List.of(
                entry(first, 2, 20, "Assembler", CRAFTING_TABLE, true, 3, 2),
                entry(second, 1, 10, "Assembler", CRAFTING_TABLE, true, 2, 1)), targetsById);

        assertEquals(1, result.providers().size());
        var merged = result.providers().getFirst();
        assertEquals(1, merged.id());
        assertEquals(5, merged.patternSlotCount());
        assertEquals(3, merged.usedPatternSlotCount());
        assertTrue(merged.renameable());
        assertEquals(List.of(second, first), targetsById.get(merged.id()));
    }

    @Test
    void keepsSameIconProvidersWithDifferentDisplayNamesSeparate() {
        var result = aggregate(List.of(
                entry(new OrdinaryPatternProvider(), 1, 10, "Assembler A", CRAFTING_TABLE, true, 2, 0),
                entry(new OrdinaryPatternProvider(), 2, 20, "Assembler B", CRAFTING_TABLE, true, 2, 0)),
                new HashMap<>());

        assertEquals(2, result.providers().size());
    }

    @Test
    void keepsSameNameProvidersWithDifferentIconsSeparate() {
        var result = aggregate(List.of(
                entry(new OrdinaryPatternProvider(), 1, 10, "Assembler", CRAFTING_TABLE, true, 2, 0),
                entry(new OrdinaryPatternProvider(), 2, 20, "Assembler", FURNACE, true, 2, 0)),
                new HashMap<>());

        assertEquals(2, result.providers().size());
    }

    @Test
    void customDisplayNameSeparatesOtherwiseMatchingProviders() {
        var result = aggregate(List.of(
                entry(new OrdinaryPatternProvider(), 1, 10, "Assembler", CRAFTING_TABLE, true, 2, 0),
                entry(new OrdinaryPatternProvider(), 2, 20, "Dedicated Line", CRAFTING_TABLE, true, 2, 0)),
                new HashMap<>());

        assertEquals(2, result.providers().size());
    }

    @Test
    void preservesNeoEcoTierSpecificAggregationKeys() {
        var result = aggregate(List.of(
                entry(new NeoEcoCraftingProviderF4(), 1, 10, "Crafting System", CRAFTING_TABLE, false, 1, 0),
                entry(new NeoEcoCraftingProviderF4(), 2, 20, "Crafting System", CRAFTING_TABLE, false, 2, 1),
                entry(new NeoEcoCraftingProviderF6(), 3, 30, "Crafting System", CRAFTING_TABLE, false, 3, 2)),
                new HashMap<>());

        assertEquals(2, result.providers().size());
        assertTrue(result.providers().stream().anyMatch(provider -> provider.patternSlotCount() == 3 &&
                provider.usedPatternSlotCount() == 1));
        assertTrue(result.providers().stream().anyMatch(provider -> provider.patternSlotCount() == 3 &&
                provider.usedPatternSlotCount() == 2));
    }

    @Test
    void preservesAssemblerMatrixAggregationKey() {
        Map<Long, List<PatternContainer>> targetsById = new HashMap<>();
        var result = aggregate(List.of(
                entry(new TileAssemblerMatrixPattern(), 1, 10, "First", CRAFTING_TABLE, false, 1, 0),
                entry(new TileAssemblerMatrixPattern(), 2, 20, "Second", FURNACE, false, 2, 1)),
                targetsById);

        assertEquals(1, result.providers().size());
        var merged = result.providers().getFirst();
        assertEquals(3, merged.patternSlotCount());
        assertEquals(1, merged.usedPatternSlotCount());
        assertEquals(2, targetsById.get(merged.id()).size());
    }

    @Test
    void aggregateIsRenameableOnlyWhenEveryMemberIsRenameable() {
        var result = aggregate(List.of(
                entry(new OrdinaryPatternProvider(), 1, 10, "Assembler", CRAFTING_TABLE, true, 2, 0),
                entry(new OrdinaryPatternProvider(), 2, 20, "Assembler", CRAFTING_TABLE, false, 2, 0)),
                new HashMap<>());

        assertEquals(1, result.providers().size());
        assertFalse(result.providers().getFirst().renameable());
    }

    @Test
    void providerWithoutCustomNameStorageIsNotRenameable() {
        assertFalse(PatternProviderNameHelper.canRename(new Object()));
    }

    @Test
    void failsFastWhenAggregatedSlotCountOverflows() {
        List<PatternProviderSyncHelper.PatternProviderAggregationEntry> entries = List.of(
                entry(new OrdinaryPatternProvider(), 1, 10, "Assembler", CRAFTING_TABLE, true,
                        Integer.MAX_VALUE, 0),
                entry(new OrdinaryPatternProvider(), 2, 20, "Assembler", CRAFTING_TABLE, true, 1, 0));

        assertThrows(ArithmeticException.class, () -> aggregate(entries, new HashMap<>()));
    }

    @Test
    void renamesAndClearsEveryTargetInGroup() {
        TestRenameTarget first = new TestRenameTarget(true, null, false, false);
        TestRenameTarget second = new TestRenameTarget(true, null, false, false);

        assertTrue(PatternProviderSyncHelper.renamePatternProviderTargets(
                List.of(first, second), "  New Name  "));
        assertEquals("New Name", first.customName().getString());
        assertEquals("New Name", second.customName().getString());
        assertEquals(1, first.syncCount);
        assertEquals(1, second.syncCount);

        assertTrue(PatternProviderSyncHelper.renamePatternProviderTargets(List.of(first, second), "   "));
        assertNull(first.customName());
        assertNull(second.customName());
        assertEquals(2, first.syncCount);
        assertEquals(2, second.syncCount);
    }

    @Test
    void rejectsGroupBeforeChangingAnyTargetWhenOneIsNotRenameable() {
        TestRenameTarget first = new TestRenameTarget(true, null, false, false);
        TestRenameTarget blocked = new TestRenameTarget(false, null, false, false);

        assertFalse(PatternProviderSyncHelper.renamePatternProviderTargets(
                List.of(first, blocked), "New Name"));
        assertNull(first.customName());
        assertEquals(0, first.syncCount);
    }

    @Test
    void rollsBackEarlierTargetsWhenLaterTargetRejectsWrite() {
        TestRenameTarget first = new TestRenameTarget(
                true, Component.literal("Original"), false, false);
        TestRenameTarget failing = new TestRenameTarget(
                true, Component.literal("Locked"), true, false);

        assertFalse(PatternProviderSyncHelper.renamePatternProviderTargets(
                List.of(first, failing), "New Name"));
        assertEquals("Original", first.customName().getString());
        assertEquals("Locked", failing.customName().getString());
        assertEquals(2, first.syncCount);
        assertEquals(0, failing.syncCount);
    }

    @Test
    void rollsBackEarlierTargetsWhenLaterTargetThrows() {
        TestRenameTarget first = new TestRenameTarget(
                true, Component.literal("Original"), false, false);
        TestRenameTarget failing = new TestRenameTarget(
                true, Component.literal("Locked"), false, true);

        assertFalse(PatternProviderSyncHelper.renamePatternProviderTargets(
                List.of(first, failing), "New Name"));
        assertEquals("Original", first.customName().getString());
        assertEquals(2, first.syncCount);
    }

    private static PatternEncodingPreviewMenu.SyncedPatternProviderList aggregate(
                                                                                  List<PatternProviderSyncHelper.PatternProviderAggregationEntry> entries,
                                                                                  Map<Long, List<PatternContainer>> targetsById) {
        return PatternProviderSyncHelper.aggregateSyncedPatternProviders(entries, targetsById, false);
    }

    private static PatternProviderSyncHelper.PatternProviderAggregationEntry entry(
                                                                                   PatternContainer container, long id, long sortOrder, String displayName,
                                                                                   ResourceLocation icon, boolean renameable, int totalSlots, int usedSlots) {
        String specialAggregationKey = switch (container) {
            case TileAssemblerMatrixPattern ignored -> "extendedae:assembler_matrix";
            case NeoEcoCraftingProviderF4 ignored -> "neoecoae:crafting_system:F4";
            case NeoEcoCraftingProviderF6 ignored -> "neoecoae:crafting_system:F6";
            default -> null;
        };
        return new PatternProviderSyncHelper.PatternProviderAggregationEntry(
                container,
                id,
                sortOrder,
                Component.literal(displayName),
                icon,
                specialAggregationKey,
                true,
                renameable,
                totalSlots,
                usedSlots,
                0,
                0,
                0);
    }

    private static class OrdinaryPatternProvider implements PatternContainer {

        @Override
        public IGrid getGrid() {
            return null;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TileAssemblerMatrixPattern extends OrdinaryPatternProvider {}

    private static final class NeoEcoCraftingProviderF4 extends OrdinaryPatternProvider {}

    private static final class NeoEcoCraftingProviderF6 extends OrdinaryPatternProvider {}

    private static final class TestRenameTarget implements PatternProviderSyncHelper.PatternProviderRenameTarget {

        private final boolean renameable;
        private final boolean rejectWrites;
        private final boolean throwOnWrite;
        @Nullable
        private Component customName;
        private int syncCount;

        private TestRenameTarget(boolean renameable, @Nullable Component customName, boolean rejectWrites,
                                 boolean throwOnWrite) {
            this.renameable = renameable;
            this.customName = customName;
            this.rejectWrites = rejectWrites;
            this.throwOnWrite = throwOnWrite;
        }

        @Override
        public boolean canRename() {
            return this.renameable;
        }

        @Override
        public @Nullable Component customName() {
            return this.customName;
        }

        @Override
        public boolean setCustomName(@Nullable Component customName) {
            if (this.throwOnWrite) {
                throw new IllegalStateException("write failed");
            }
            if (this.rejectWrites) {
                return false;
            }
            this.customName = customName;
            return true;
        }

        @Override
        public void syncRename() {
            this.syncCount++;
        }

        @Override
        public String description() {
            return "test target";
        }
    }
}
