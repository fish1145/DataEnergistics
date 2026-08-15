package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Immutable counted-provider preparation containing either one admission or explicit rejection facts.
 *
 * @param admission  prepared one-shot admission, or {@code null} when no target was accepted
 * @param target     target fixed by the admission, or {@code null} for a rejected preparation
 * @param rejections target or provider rejections observed while preparing this result
 */
public record CountedCraftingPreparation(@Nullable CountedCraftingAdmission admission,
                                         @Nullable CraftingDispatchTarget target,
                                         List<CraftingDispatchRejection> rejections) {

    public CountedCraftingPreparation {
        if (rejections == null) {
            throw new IllegalArgumentException("Counted crafting preparation rejections must not be null");
        }
        for (CraftingDispatchRejection rejection : rejections) {
            if (rejection == null) {
                throw new IllegalArgumentException("Counted crafting preparation rejections must not contain null");
            }
        }
        if ((admission == null) != (target == null)) {
            throw new IllegalArgumentException("Counted crafting admission and target must be present together");
        }
        if (admission == null && rejections.isEmpty()) {
            throw new IllegalArgumentException("Rejected counted crafting preparation must explain its rejection");
        }
        rejections = List.copyOf(rejections);
    }

    /**
     * Creates an accepted preparation with no preceding target rejections.
     *
     * @param admission prepared one-shot admission
     * @param target    fixed provider-local target
     * @return accepted preparation
     */
    public static CountedCraftingPreparation accepted(
                                                      CountedCraftingAdmission admission,
                                                      CraftingDispatchTarget target) {
        return accepted(admission, target, List.of());
    }

    /**
     * Creates an accepted preparation while retaining targets rejected earlier in provider order.
     *
     * @param admission  prepared one-shot admission
     * @param target     fixed provider-local target
     * @param rejections earlier preparation rejections
     * @return accepted preparation with diagnostics
     */
    public static CountedCraftingPreparation accepted(
                                                      CountedCraftingAdmission admission,
                                                      CraftingDispatchTarget target,
                                                      List<CraftingDispatchRejection> rejections) {
        if (admission == null || target == null) {
            throw new IllegalArgumentException("Accepted counted crafting preparation requires an admission and target");
        }
        return new CountedCraftingPreparation(admission, target, rejections);
    }

    /**
     * Creates a rejected preparation from one explicit reason.
     *
     * @param rejection rejection fact
     * @return rejected preparation
     */
    public static CountedCraftingPreparation rejected(CraftingDispatchRejection rejection) {
        return rejected(List.of(rejection));
    }

    /**
     * Creates a rejected preparation from all target or provider reasons observed in one scan.
     *
     * @param rejections non-empty rejection facts
     * @return rejected preparation
     */
    public static CountedCraftingPreparation rejected(List<CraftingDispatchRejection> rejections) {
        return new CountedCraftingPreparation(null, null, rejections);
    }

    /**
     * Returns whether this preparation fixed a target and admission.
     *
     * @return whether physical submission may proceed
     */
    public boolean accepted() {
        return this.admission != null;
    }
}
