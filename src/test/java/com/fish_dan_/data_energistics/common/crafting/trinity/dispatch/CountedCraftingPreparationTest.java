package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

import com.fish_dan_.data_energistics.common.crafting.trinity.CountedCraftingAdmission;

import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CountedCraftingPreparationTest {

    @Test
    void acceptedPreparationBindsAdmissionAndTargetTogether() {
        CountedCraftingAdmission admission = new FixedAdmission();
        CraftingDispatchTarget target = new CraftingDispatchTarget("side:north");
        CraftingDispatchRejection earlierRejection = CraftingDispatchRejection.targeted(
                CraftingDispatchStatus.NO_CAPACITY,
                new CraftingDispatchTarget("side:south"));

        CountedCraftingPreparation preparation = CountedCraftingPreparation.accepted(admission, target, List.of(earlierRejection));

        assertTrue(preparation.accepted());
        assertSame(admission, preparation.admission());
        assertSame(target, preparation.target());
        assertEquals(List.of(earlierRejection), preparation.rejections());
    }

    @Test
    void rejectedPreparationRequiresAtLeastOneExplicitReason() {
        CraftingDispatchRejection rejection = CraftingDispatchRejection.scoped(CraftingDispatchStatus.LOCKED);

        CountedCraftingPreparation preparation = CountedCraftingPreparation.rejected(rejection);

        assertFalse(preparation.accepted());
        assertEquals(List.of(rejection), preparation.rejections());
        assertThrows(
                IllegalArgumentException.class,
                () -> CountedCraftingPreparation.rejected(List.of()));
    }

    @Test
    void preparationCopiesRejectionsAndRejectsInconsistentState() {
        ArrayList<CraftingDispatchRejection> rejections = new ArrayList<>();
        rejections.add(CraftingDispatchRejection.scoped(CraftingDispatchStatus.BUSY));
        CountedCraftingPreparation preparation = CountedCraftingPreparation.rejected(rejections);

        rejections.clear();

        assertEquals(1, preparation.rejections().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CountedCraftingPreparation(new FixedAdmission(), null, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CountedCraftingPreparation(null, CraftingDispatchTarget.provider(), List.of()));
        ArrayList<CraftingDispatchRejection> nullRejection = new ArrayList<>();
        nullRejection.add(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> new CountedCraftingPreparation(null, null, nullRejection));
    }

    @Test
    void rejectionRejectsNonPreparationOutcomes() {
        for (CraftingDispatchStatus status : List.of(
                CraftingDispatchStatus.ACCEPTED,
                CraftingDispatchStatus.STALE,
                CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CraftingDispatchRejection.scoped(status),
                    status.name());
        }
        assertThrows(IllegalArgumentException.class, () -> CraftingDispatchRejection.scoped(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> CraftingDispatchRejection.targeted(CraftingDispatchStatus.BLOCKED, null));
    }

    @Test
    void targetRequiresStableNonblankIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new CraftingDispatchTarget(null));
        assertThrows(IllegalArgumentException.class, () -> new CraftingDispatchTarget(""));
        assertThrows(IllegalArgumentException.class, () -> new CraftingDispatchTarget("  "));
        assertEquals(CraftingDispatchTarget.provider(), CraftingDispatchTarget.provider());
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
}
