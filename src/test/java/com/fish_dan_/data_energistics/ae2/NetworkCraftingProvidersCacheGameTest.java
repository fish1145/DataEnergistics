package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
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

    @TestHolder("network_crafting_provider_publication_identity_lifecycle")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesPublicationIdentityLifecycle(GameTestHelper helper) {
        IPatternDetails sharedPattern = new TestPattern(
                AEItemKey.of(Items.CRAFTING_TABLE),
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        IPatternDetails equalPattern = new TestPattern(
                AEItemKey.of(Items.CRAFTING_TABLE),
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        ICraftingProvider firstProvider = new TestProvider(sharedPattern, 10);
        ICraftingProvider secondProvider = new TestProvider(sharedPattern, 20);
        ICraftingProvider equalityIsolatedProvider = new TestProvider(equalPattern, 30);
        NetworkCraftingProviders providers = new NetworkCraftingProviders();
        if (!(providers instanceof CraftingProviderPublicationIndex publicationIndex)) {
            throw new IllegalStateException("Network crafting providers do not expose a publication index");
        }

        providers.addProvider(firstProvider);
        CraftingProviderId firstId = onlyProviderId(publicationIndex, sharedPattern);
        helper.assertTrue(publicationIndex.resolveLiveProvider(firstId) == firstProvider,
                "Current provider ID must resolve to the registered provider");
        helper.assertTrue(publicationIndex.providerIdsFor(sharedPattern).getFirst().equals(firstId),
                "Repeated lookup must retain the registration ID");

        providers.addProvider(secondProvider);
        List<CraftingProviderId> sharedIds = publicationIndex.providerIdsFor(sharedPattern);
        helper.assertValueEqual(sharedIds.size(), 2, "Shared pattern publication multiplicity");
        helper.assertTrue(sharedIds.getFirst().equals(firstId), "Publication order must retain the first provider");

        providers.addProvider(equalityIsolatedProvider);
        CraftingProviderId equalityIsolatedId = onlyProviderId(publicationIndex, equalPattern);
        helper.assertTrue(sharedPattern != equalPattern, "Identity contract requires distinct pattern objects");
        helper.assertTrue(sharedPattern.equals(equalPattern), "Identity contract must be tested against equal patterns");
        helper.assertTrue(publicationIndex.resolveLiveProvider(equalityIsolatedId) == equalityIsolatedProvider,
                "Equal but distinct pattern must resolve only its own provider");
        helper.assertValueEqual(publicationIndex.providerIdsFor(sharedPattern).size(), 2,
                "Equality-equivalent pattern must not enter the identity lookup");

        providers.removeProvider(firstProvider);
        helper.assertTrue(publicationIndex.resolveLiveProvider(firstId) == null,
                "Removed provider ID must become stale");
        providers.addProvider(firstProvider);
        List<CraftingProviderId> republishedIds = publicationIndex.providerIdsFor(sharedPattern);
        CraftingProviderId republishedId = republishedIds.get(1);
        helper.assertTrue(!republishedId.equals(firstId), "Republishing must allocate a new provider ID");
        helper.assertTrue(publicationIndex.resolveLiveProvider(republishedId) == firstProvider,
                "Republished ID must resolve to the live provider");
        helper.assertValueEqual(publicationIndex.publicationRevision(), 5L,
                "Every publication and removal must advance the index revision");
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

    private static CraftingProviderId onlyProviderId(
                                                     CraftingProviderPublicationIndex publicationIndex,
                                                     IPatternDetails pattern) {
        List<CraftingProviderId> providerIds = publicationIndex.providerIdsFor(pattern);
        if (providerIds.size() != 1) {
            throw new IllegalStateException("Expected one provider publication, found " + providerIds);
        }
        return providerIds.getFirst();
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
