package com.fish_dan_.data_energistics.common.crafting.flower;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class DigitalConstructFlowerExecutingCraftingJobTest {

    @Test
    void routedTaskRestoresItsExactRouteAroundDecodedPattern() {
        PatternRoute route = new PatternRoute(UUID.randomUUID(), UUID.randomUUID(), 127);
        IPatternDetails original = new StubPatternDetails();
        IPatternDetails decoded = new StubPatternDetails();
        RoutedCraftingPatternDetails routed = new RoutedCraftingPatternDetails(route, original);

        CompoundTag saved = DigitalConstructFlowerExecutingCraftingJob.writeTaskDetails(
                routed,
                ignored -> definitionTag());
        IPatternDetails restored = DigitalConstructFlowerExecutingCraftingJob.readTaskDetails(
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

        CompoundTag saved = DigitalConstructFlowerExecutingCraftingJob.writeTaskDetails(
                original,
                ignored -> definitionTag());
        IPatternDetails restored = DigitalConstructFlowerExecutingCraftingJob.readTaskDetails(saved, ignored -> decoded);

        assertSame(decoded, restored);
    }

    @Test
    void routedTaskRejectsMalformedPersistedRoute() {
        CompoundTag malformed = definitionTag();
        malformed.put("route", new CompoundTag());

        assertThrows(
                IllegalArgumentException.class,
                () -> DigitalConstructFlowerExecutingCraftingJob.readTaskDetails(
                        malformed,
                        ignored -> new StubPatternDetails()));
    }

    private static CompoundTag definitionTag() {
        CompoundTag definition = new CompoundTag();
        definition.putString("marker", "encoded");
        return definition;
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
