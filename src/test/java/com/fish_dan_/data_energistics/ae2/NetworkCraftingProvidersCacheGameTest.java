package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture.TrinityCraftingProviderRevision;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.helpers.NetworkCraftingProviders;

import java.util.Collection;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkCraftingProvidersCacheGameTest {

    private NetworkCraftingProvidersCacheGameTest() {}

    @TestHolder("network_crafting_providers_reuses_sorted_patterns_until_provider_changes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reusesSortedPatternsUntilProviderChanges(GameTestHelper helper) {
        GenericStack sharedOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1L);
        IPatternDetails lowPriorityPattern = new TestPattern(AEItemKey.of(Items.CRAFTING_TABLE), sharedOutput);
        IPatternDetails highPriorityPattern = new TestPattern(AEItemKey.of(Items.FURNACE), sharedOutput);
        ICraftingProvider lowPriorityProvider = new TestProvider(lowPriorityPattern, 10);
        ICraftingProvider highPriorityProvider = new TestProvider(highPriorityPattern, 100);
        NetworkCraftingProviders providers = new NetworkCraftingProviders();
        if (!(providers instanceof TrinityCraftingProviderRevision revision)) {
            throw new IllegalStateException("Network crafting providers do not expose a Trinity mutation revision");
        }
        long initialRevision = revision.data_energistics$trinityCraftingProviderRevision();

        providers.addProvider(lowPriorityProvider);
        helper.assertValueEqual(
                revision.data_energistics$trinityCraftingProviderRevision(),
                Math.incrementExact(initialRevision),
                "First same-tick provider mutation revision");
        providers.addProvider(highPriorityProvider);
        helper.assertValueEqual(
                revision.data_energistics$trinityCraftingProviderRevision(),
                Math.addExact(initialRevision, 2L),
                "Second same-tick provider mutation revision");

        Collection<IPatternDetails> initial = providers.getCraftingFor(sharedOutput.what());
        assertPatterns(helper, initial, highPriorityPattern, lowPriorityPattern);
        assertSame(helper, initial, providers.getCraftingFor(sharedOutput.what()),
                "Unchanged pattern lookups must reuse the sorted list");

        providers.removeProvider(highPriorityProvider);
        helper.assertValueEqual(
                revision.data_energistics$trinityCraftingProviderRevision(),
                Math.addExact(initialRevision, 3L),
                "Same-tick provider removal revision");
        Collection<IPatternDetails> afterRemoval = providers.getCraftingFor(sharedOutput.what());
        assertNotSame(helper, initial, afterRemoval,
                "Removing a provider must invalidate the sorted list");
        assertPatterns(helper, afterRemoval, lowPriorityPattern);
        assertSame(helper, afterRemoval, providers.getCraftingFor(sharedOutput.what()),
                "The rebuilt list after removal must be reused");

        providers.addProvider(highPriorityProvider);
        helper.assertValueEqual(
                revision.data_energistics$trinityCraftingProviderRevision(),
                Math.addExact(initialRevision, 4L),
                "Same-tick provider re-add revision");
        Collection<IPatternDetails> afterAddition = providers.getCraftingFor(sharedOutput.what());
        assertNotSame(helper, afterRemoval, afterAddition,
                "Adding a provider must invalidate the sorted list");
        assertPatterns(helper, afterAddition, highPriorityPattern, lowPriorityPattern);
        assertSame(helper, afterAddition, providers.getCraftingFor(sharedOutput.what()),
                "The rebuilt list after addition must be reused");
        helper.succeed();
    }

    private static void assertPatterns(GameTestHelper helper,
                                       Collection<IPatternDetails> actual,
                                       IPatternDetails... expected) {
        helper.assertTrue(List.copyOf(actual).equals(List.of(expected)),
                "Expected pattern order " + List.of(expected) + ", found " + actual);
    }

    private static void assertSame(GameTestHelper helper, Object expected, Object actual, String message) {
        helper.assertTrue(expected == actual, message);
    }

    private static void assertNotSame(GameTestHelper helper, Object unexpected, Object actual, String message) {
        helper.assertTrue(unexpected != actual, message);
    }

    private record TestPattern(AEItemKey definition, GenericStack output) implements IPatternDetails {

        private static final IInput[] NO_INPUTS = {};

        @Override
        public AEItemKey getDefinition() {
            return this.definition;
        }

        @Override
        public IInput[] getInputs() {
            return NO_INPUTS;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(this.output);
        }
    }

    private record TestProvider(IPatternDetails pattern, int priority) implements ICraftingProvider {

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(this.pattern);
        }

        @Override
        public int getPatternPriority() {
            return this.priority;
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new UnsupportedOperationException("Pattern cache tests never submit crafting inputs");
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }
}
