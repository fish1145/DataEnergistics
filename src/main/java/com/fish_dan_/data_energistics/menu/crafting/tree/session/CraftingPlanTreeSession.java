package com.fish_dan_.data_energistics.menu.crafting.tree.session;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;

import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Single-owner, server-thread-only plan lifetime. Menus transfer through a private handoff token before opening
 * another container, because Minecraft removes the old container before constructing the new one.
 * No inventory is reserved and no job is submitted by transferring this session.
 */
public final class CraftingPlanTreeSession {

    private final UUID id = UUID.randomUUID();
    private final CraftingPlanTreeRequest request;
    private @Nullable Object owner;
    private @Nullable CraftingPlanTreeResult result;
    private @Nullable Future<ICraftingPlan> calculation;
    private @Nullable ICraftingCPU selectedCpu;
    private long revision;

    public CraftingPlanTreeSession(CraftingPlanTreeRequest request, CraftingPlanTreeResult result,
                                   long revision, Object owner, @Nullable ICraftingCPU selectedCpu) {
        if (revision < 0) throw new IllegalArgumentException("Negative plan-tree revision");
        this.request = request;
        this.result = result;
        this.revision = revision;
        this.owner = owner;
        this.selectedCpu = selectedCpu;
    }

    public UUID id() {
        return this.id;
    }

    public CraftingPlanTreeRequest request() {
        return this.request;
    }

    public long revision() {
        return this.revision;
    }

    public @Nullable CraftingPlanTreeResult result() {
        return this.result;
    }

    public @Nullable ICraftingCPU selectedCpu() {
        return this.selectedCpu;
    }

    public boolean isPlanning() {
        return this.calculation != null;
    }

    public boolean isOwnedBy(Object candidate) {
        return this.owner == candidate;
    }

    /** Fails before changing either owner if a stale menu attempts a second transfer. */
    public void transfer(Object expectedOwner, Object nextOwner) {
        requireOwner(expectedOwner);
        this.owner = nextOwner;
    }

    public void selectCpu(Object owner, @Nullable ICraftingCPU cpu) {
        requireOwner(owner);
        this.selectedCpu = cpu;
    }

    /** Invalidates the old result before the new future can be observed or submitted. */
    public void beginPlanning(Object owner, Future<ICraftingPlan> calculation) {
        requireOwner(owner);
        if (this.calculation != null) this.calculation.cancel(true);
        this.revision = Math.incrementExact(this.revision);
        this.result = null;
        this.calculation = calculation;
    }

    /** Returns only completed work; never waits on a planner from the server tick. */
    public @Nullable ICraftingPlan takeCompletedPlan(Object owner) throws ExecutionException, InterruptedException {
        requireOwner(owner);
        Future<ICraftingPlan> pending = this.calculation;
        if (pending == null || !pending.isDone()) return null;
        this.calculation = null;
        return pending.get();
    }

    public void publish(Object owner, CraftingPlanTreeResult result) {
        requireOwner(owner);
        if (this.calculation != null) throw new IllegalStateException("Cannot publish over a pending calculation");
        this.result = result;
    }

    /** Removal of a menu that already transferred ownership is intentionally not terminal. */
    public void closeIfOwnedBy(Object candidate) {
        if (this.owner != candidate) return;
        if (this.calculation != null) this.calculation.cancel(true);
        this.calculation = null;
        this.result = null;
        this.selectedCpu = null;
        this.owner = null;
    }

    private void requireOwner(Object candidate) {
        if (this.owner == null || this.owner != candidate) {
            throw new IllegalStateException("Plan-tree session is closed or belongs to another menu");
        }
    }
}
