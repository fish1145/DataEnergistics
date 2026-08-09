package com.fish_dan_.data_energistics.menu.patternprovider;

import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
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
    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("test", "compressing");
    private static final ResourceLocation OTHER_RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("test", "mixing");
    private static final ResourceLocation WORKSTATION = ResourceLocation.fromNamespaceAndPath("test", "compressor");
    private static final PatternProviderSyncHelper.PatternProviderAggregationKey PROVIDER_KEY = new PatternProviderSyncHelper.PatternProviderAggregationKey.Core(
            new ProviderIdentityDescriptor.External(
                    ResourceLocation.fromNamespaceAndPath("test", "ordinary_provider"), 1));

    @Test
    void mergesProvidersWithSameSemanticIdentity() {
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
    void matchesOnlyRecipeTypesAdvertisedByProviderMetadata() {
        PatternProviderMetadata metadata = new PatternProviderMetadata(
                ResourceLocation.fromNamespaceAndPath("test", "compressor_provider"),
                new ProviderIdentityDescriptor.External(
                        ResourceLocation.fromNamespaceAndPath("test", "compressor"), 1),
                List.of(RECIPE_TYPE),
                List.of(WORKSTATION));

        assertTrue(PatternProviderSyncHelper.matchesRecipeType(
                metadata, PatternEncodingRankingContext.of(RECIPE_TYPE)));
        assertFalse(PatternProviderSyncHelper.matchesRecipeType(
                metadata, PatternEncodingRankingContext.of(OTHER_RECIPE_TYPE)));
        assertFalse(PatternProviderSyncHelper.matchesRecipeType(metadata, null));
    }

    @Test
    void acceptsOnlyMatchingProvidersWithAFreePatternSlot() {
        assertTrue(PatternProviderSyncHelper.isAvailableRecipeTypeCandidate(
                entry(new OrdinaryPatternProvider(), 1, 10, "Compressor", CRAFTING_TABLE, true,
                        2, 1, providerKey("available"), true, "test:available")));
        assertFalse(PatternProviderSyncHelper.isAvailableRecipeTypeCandidate(
                entry(new OrdinaryPatternProvider(), 2, 10, "Compressor", CRAFTING_TABLE, true,
                        2, 2, providerKey("full"), true, "test:full")));
        assertFalse(PatternProviderSyncHelper.isAvailableRecipeTypeCandidate(
                entry(new OrdinaryPatternProvider(), 3, 10, "Mixer", CRAFTING_TABLE, true,
                        2, 0, providerKey("unrelated"), false, "test:unrelated")));
    }

    @Test
    void ranksByLearningThenNormalOrderAndCanonicalDigest() {
        var learned = entry(new OrdinaryPatternProvider(), 1, 100, "Zeta", CRAFTING_TABLE, true,
                2, 0, providerKey("learned"), true, "test:zeta");
        var normalFirst = entry(new OrdinaryPatternProvider(), 2, 10, "Alpha", CRAFTING_TABLE, true,
                2, 0, providerKey("normal_first"), true, "test:zulu");
        var canonicalFirst = entry(new OrdinaryPatternProvider(), 3, 10, "Alpha", CRAFTING_TABLE, true,
                2, 0, providerKey("canonical_first"), true, "test:alpha");

        var learnedResult = PatternProviderSyncHelper.aggregateSyncedPatternProviders(
                List.of(normalFirst, learned, canonicalFirst),
                new HashMap<>(),
                Map.of("test:zeta", 3L));
        assertEquals(List.of(1L, 3L, 2L), learnedResult.providers().stream()
                .map(PatternEncodingPreviewMenu.SyncedPatternProvider::id)
                .toList());

        var fallbackResult = aggregate(List.of(normalFirst, learned, canonicalFirst), new HashMap<>());
        assertEquals(List.of(3L, 2L, 1L), fallbackResult.providers().stream()
                .map(PatternEncodingPreviewMenu.SyncedPatternProvider::id)
                .toList());
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
        return PatternProviderSyncHelper.aggregateSyncedPatternProviders(entries, targetsById);
    }

    private static PatternProviderSyncHelper.PatternProviderAggregationEntry entry(
                                                                                   PatternContainer container, long id, long sortOrder, String displayName,
                                                                                   ResourceLocation icon, boolean renameable, int totalSlots, int usedSlots) {
        return new PatternProviderSyncHelper.PatternProviderAggregationEntry(
                container,
                id,
                sortOrder,
                Component.literal(displayName),
                icon,
                PROVIDER_KEY,
                false,
                true,
                renameable,
                totalSlots,
                usedSlots,
                "test:" + id);
    }

    private static PatternProviderSyncHelper.PatternProviderAggregationEntry entry(
                                                                                   PatternContainer container, long id, long sortOrder, String displayName,
                                                                                   ResourceLocation icon, boolean renameable, int totalSlots, int usedSlots,
                                                                                   PatternProviderSyncHelper.PatternProviderAggregationKey aggregationKey,
                                                                                   boolean exactContextMatch, String providerDigest) {
        return new PatternProviderSyncHelper.PatternProviderAggregationEntry(
                container,
                id,
                sortOrder,
                Component.literal(displayName),
                icon,
                aggregationKey,
                exactContextMatch,
                true,
                renameable,
                totalSlots,
                usedSlots,
                providerDigest);
    }

    private static PatternProviderSyncHelper.PatternProviderAggregationKey providerKey(String path) {
        return new PatternProviderSyncHelper.PatternProviderAggregationKey.Core(
                new ProviderIdentityDescriptor.External(ResourceLocation.fromNamespaceAndPath("test", path), 1));
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
