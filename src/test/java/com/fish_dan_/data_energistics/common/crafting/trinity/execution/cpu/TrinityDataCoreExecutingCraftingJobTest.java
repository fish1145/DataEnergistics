package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.RoutedCraftingPatternDetails;

import net.minecraft.nbt.CompoundTag;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityDataCoreExecutingCraftingJobTest {

    @Test
    void routedTaskRestoresItsExactRouteAroundDecodedPattern() {
        PatternRoute route = new PatternRoute(UUID.randomUUID(), UUID.randomUUID(), 127);
        IPatternDetails original = new StubPatternDetails();
        IPatternDetails decoded = new StubPatternDetails();
        RoutedCraftingPatternDetails routed = new RoutedCraftingPatternDetails(route, original);

        CompoundTag saved = TrinityDataCoreExecutingCraftingJob.writeTaskDetails(
                routed,
                ignored -> definitionTag());
        assertEquals("trinity", saved.getString("kind"));
        assertEquals("encoded", saved.getCompound("definition").getString("marker"));
        IPatternDetails restored = TrinityDataCoreExecutingCraftingJob.readTaskDetails(
                saved,
                definition -> {
                    assertEquals("encoded", definition.getString("marker"));
                    return decoded;
                });

        RoutedCraftingPatternDetails restoredRoute = (RoutedCraftingPatternDetails) restored;
        assertEquals(route, restoredRoute.route());
        assertSame(decoded, restoredRoute.delegate());
    }

    @Test
    void ordinaryTaskRemainsAnOrdinaryDecodedPattern() {
        IPatternDetails original = new StubPatternDetails();
        IPatternDetails decoded = new StubPatternDetails();

        CompoundTag saved = TrinityDataCoreExecutingCraftingJob.writeTaskDetails(
                original,
                ignored -> definitionTag());
        assertEquals("provider", saved.getString("kind"));
        assertEquals("encoded", saved.getCompound("definition").getString("marker"));
        assertFalse(saved.contains("route"));
        IPatternDetails restored = TrinityDataCoreExecutingCraftingJob.readTaskDetails(saved, ignored -> decoded);

        assertSame(decoded, restored);
    }

    @Test
    void routedTaskRejectsMalformedPersistedRoute() {
        CompoundTag malformed = taskTag("trinity");
        malformed.put("route", new CompoundTag());

        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityDataCoreExecutingCraftingJob.readTaskDetails(
                        malformed,
                        ignored -> new StubPatternDetails()));
    }

    @Test
    void trinityTaskWithoutRouteIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityDataCoreExecutingCraftingJob.readTaskDetails(
                        taskTag("trinity"),
                        ignored -> new StubPatternDetails()));
    }

    @Test
    void providerTaskCannotCarryTrinityRoute() {
        CompoundTag malformed = taskTag("provider");
        malformed.put("route", new PatternRoute(UUID.randomUUID(), UUID.randomUUID(), 0).writeToTag());

        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityDataCoreExecutingCraftingJob.readTaskDetails(
                        malformed,
                        ignored -> new StubPatternDetails()));
    }

    @Test
    void jobSchemaAcceptsLegacyAndPlanVersions() {
        CompoundTag persistedJob = new CompoundTag();
        assertFalse(TrinityDataCoreExecutingCraftingJob.hasSupportedSchema(persistedJob));

        persistedJob.putInt("schema_version", 3);
        assertFalse(TrinityDataCoreExecutingCraftingJob.hasSupportedSchema(persistedJob));

        persistedJob.putInt("schema_version", 1);
        assertTrue(TrinityDataCoreExecutingCraftingJob.hasSupportedSchema(persistedJob));

        persistedJob.putInt("schema_version", 2);
        assertTrue(TrinityDataCoreExecutingCraftingJob.hasSupportedSchema(persistedJob));
    }

    private static CompoundTag definitionTag() {
        CompoundTag definition = new CompoundTag();
        definition.putString("marker", "encoded");
        return definition;
    }

    private static CompoundTag taskTag(String kind) {
        CompoundTag task = new CompoundTag();
        task.putString("kind", kind);
        task.put("definition", definitionTag());
        return task;
    }

    private static final class StubPatternDetails implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }
    }
}
