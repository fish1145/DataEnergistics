package com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission;

import appeng.api.networking.crafting.ICraftingPlan;

/**
 * Explicit opt-in boundary for non-AE2 crafting plans whose complete semantics are executable by a Trinity CPU.
 *
 * <p>
 * Third-party extended plans must implement this contract deliberately. Merely implementing
 * {@link ICraftingPlan} is insufficient because it may hide host, seed, borrowing or scheduling semantics owned by a
 * different crafting CPU implementation.
 */
public interface TrinityCpuExecutablePlan extends ICraftingPlan {}
