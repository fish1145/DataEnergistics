package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CountedCraftingProviderTest {

    @Test
    void legacyAdmissionUsesProviderFallbackTarget() {
        FixedAdmission admission = new FixedAdmission();
        LegacyProvider provider = new LegacyProvider(admission);

        CountedCraftingPreparation preparation = provider.prepareBatch(new TestPattern(), new KeyCounter[0], 1L, ignored -> true);

        assertTrue(preparation.accepted());
        assertSame(admission, preparation.admission());
        assertEquals(CraftingDispatchTarget.provider(), preparation.target());
        assertEquals(1, provider.prepareCalls);
    }

    @Test
    void legacyNullAdmissionMapsToScopedNoCapacity() {
        LegacyProvider provider = new LegacyProvider(null);

        CountedCraftingPreparation preparation = provider.prepareBatch(new TestPattern(), new KeyCounter[0], 1L, ignored -> true);

        assertFalse(preparation.accepted());
        assertEquals(1, preparation.rejections().size());
        assertEquals(CraftingDispatchStatus.NO_CAPACITY, preparation.rejections().getFirst().status());
        assertNull(preparation.rejections().getFirst().target());
    }

    @Test
    void fallbackTargetExclusionSkipsLegacyPreparationCallback() {
        LegacyProvider provider = new LegacyProvider(new FixedAdmission());

        CountedCraftingPreparation preparation = provider.prepareBatch(new TestPattern(), new KeyCounter[0], 1L, ignored -> false);

        assertFalse(preparation.accepted());
        assertEquals(0, provider.prepareCalls);
        assertEquals(CraftingDispatchTarget.provider(), preparation.rejections().getFirst().target());
    }

    private static final class LegacyProvider implements CountedCraftingProvider {

        @Nullable
        private final CountedCraftingAdmission admission;
        private int prepareCalls;

        private LegacyProvider(@Nullable CountedCraftingAdmission admission) {
            this.admission = admission;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new UnsupportedOperationException("Provider bridge test never performs physical dispatch");
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public @Nullable CountedCraftingAdmission prepareBatch(
                                                               IPatternDetails patternDetails,
                                                               KeyCounter[] prototype,
                                                               long requestedCount) {
            this.prepareCalls++;
            return this.admission;
        }
    }

    private static final class FixedAdmission implements CountedCraftingAdmission {

        @Override
        public long count() {
            return 1L;
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            return true;
        }
    }

    private static final class TestPattern implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            throw new UnsupportedOperationException("Provider bridge test never inspects pattern definitions");
        }

        @Override
        public IInput[] getInputs() {
            throw new UnsupportedOperationException("Provider bridge test never inspects pattern inputs");
        }

        @Override
        public List<GenericStack> getOutputs() {
            throw new UnsupportedOperationException("Provider bridge test never inspects pattern outputs");
        }
    }
}
