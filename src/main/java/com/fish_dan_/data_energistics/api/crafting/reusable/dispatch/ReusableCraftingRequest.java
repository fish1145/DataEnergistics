package com.fish_dan_.data_energistics.api.crafting.reusable.dispatch;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import lombok.Builder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ephemeral, read-only server-thread proposal. Preparation must not consume the offered assets or retain world,
 * pattern or actor references. One logical sequence has one immutable contract; the admission reports the actual
 * physical transfer separately from the per-operation recipe inputs.
 */
@Builder
public record ReusableCraftingRequest(UUID sessionId, UUID jobId, String cpuOwner, long sequence,
                                      Target target, IPatternDetails pattern, List<Input> inputs,
                                      List<SlotStack> offeredTools, long requestedCount,
                                      Optional<ResourceLocation> recipeId, IActionSource actionSource,
                                      ServerLevel level) {

    public ReusableCraftingRequest {
        inputs = List.copyOf(inputs);
        offeredTools = List.copyOf(offeredTools);
        if (sequence < 0L || requestedCount <= 0L || cpuOwner.isBlank()) {
            throw new IllegalArgumentException("A reusable submission needs an owner, sequence and positive count");
        }
        if (inputs.size() != pattern.getInputs().length) {
            throw new IllegalArgumentException("Reusable submission must describe every original input slot");
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (inputs.get(slot).slot() != slot) {
                throw new IllegalArgumentException("Reusable inputs must retain original slot order");
            }
        }
    }

    /** Restart-stable executor identity; the existing counted route alone is not a durable locator. */
    public record Target(String persistentIdentity, CountedCraftingTarget route,
                         Optional<ResourceLocation> mode) {

        public Target {
            if (persistentIdentity.isBlank() || route.providerScoped()) {
                throw new IllegalArgumentException("Reusable execution requires a concrete recoverable target");
            }
        }
    }

    /** Ordinary inputs are per operation. Tools are held once per lane, not multiplied by batch count. */
    public record Input(int slot, List<GenericStack> consumedPerOperation, Optional<Tool> tool) {

        public Input {
            consumedPerOperation = List.copyOf(consumedPerOperation);
            if (slot < 0 || consumedPerOperation.isEmpty() && tool.isEmpty()) {
                throw new IllegalArgumentException("A reusable input slot must have a material or tool requirement");
            }
            for (GenericStack input : consumedPerOperation) {
                if (input.amount() <= 0L) {
                    throw new IllegalArgumentException("Per-operation material quantities must be positive");
                }
            }
        }
    }

    /**
     * Frozen held-unit contract. An operationState constrains every operation in this append to that exact key;
     * captured CPU firings always provide it. Empty allows a native caller's deterministic consecutive state chain.
     */
    public record Tool(long heldAmount, Ownership ownership, ReusableInputRule rule, Optional<AEItemKey> operationState) {

        public Tool {
            if (heldAmount <= 0L) {
                throw new IllegalArgumentException("A retained tool requirement must be positive");
            }
            operationState.ifPresent(rule::guaranteedUses);
        }
    }

    /** Exact physical quantity at an original pattern slot; stack keys must retain all components. */
    public record SlotStack(int slot, GenericStack stack) {

        public SlotStack {
            if (slot < 0 || stack.amount() <= 0L) {
                throw new IllegalArgumentException("A physical input requires a slot and positive quantity");
            }
        }
    }
}
