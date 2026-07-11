package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;

import java.util.List;

/**
 * Routes ordered Trinity crafting output batches without allowing CPU-reserved items to leak into general storage.
 */
public interface TrinityPatternOutputRouter {

    /**
     * Supplies the amount that crafting CPUs are currently waiting for.
     */
    @FunctionalInterface
    interface RequestedAmount {

        /**
         * @param key crafted item key
         * @return current requested amount across the lease grid's crafting CPUs
         */
        long get(AEItemKey key);
    }

    /**
     * Inserts an item amount into either crafting CPUs or the host's main storage.
     */
    @FunctionalInterface
    interface OutputSink {

        /**
         * @param key    crafted item key
         * @param amount positive amount offered
         * @param mode   side-effect-free simulation or mutating insertion
         * @return accepted amount in the inclusive range from zero to {@code amount}
         */
        long insert(AEItemKey key, long amount, Actionable mode);
    }

    /** Persists exact ordered route-owned remainders after each output stack changes external state. */
    @FunctionalInterface
    interface OutputCheckpoint {

        /**
         * Durably replaces the route-owned state before routing may perform another mutating insertion.
         *
         * @param remaining exact outputs, in routing order, that must remain after the completed insertion
         */
        void replace(List<ItemStack> remaining);
    }

    /**
     * Reports the exact remainder after one routing pass.
     *
     * @param remaining defensively copied stacks that must stay on the core
     * @param inserted  total amount delivered to CPUs or main storage
     */
    record RoutingResult(List<ItemStack> remaining, long inserted) {

        /**
         * Validates and defensively copies the routing outcome.
         */
        public RoutingResult {
            if (inserted < 0L) {
                throw new IllegalArgumentException("A Trinity output routing result cannot report negative insertion");
            }
            remaining = remaining.stream().map(ItemStack::copy).toList();
        }
    }

    /**
     * Routes each pending stack to waiting CPUs first and only offers its non-requested portion to main storage.
     * Pending order is significant: when a stack retains a CPU-requested amount, the router checkpoints that stack and
     * every later stack, then ends the pass. The current stack's non-requested portion may still enter main storage
     * before that barrier. A remainder caused only by main-storage capacity does not block later stacks.
     *
     * @param pending         pending route-owned outputs in required routing order
     * @param requestedAmount lease-grid CPU request lookup
     * @param cpuSink         lease-grid crafting CPU insertion
     * @param storageSink     Trinity main storage insertion
     * @param checkpoint      persistent route-output replacement after every changed stack
     * @return exact retained outputs and total inserted amount
     */
    RoutingResult route(List<ItemStack> pending,
                        RequestedAmount requestedAmount,
                        OutputSink cpuSink,
                        OutputSink storageSink,
                        OutputCheckpoint checkpoint);
}
