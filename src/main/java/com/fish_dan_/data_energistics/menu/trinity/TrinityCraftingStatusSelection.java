package com.fish_dan_.data_energistics.menu.trinity;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.route.TrinityCraftingExecutionRoute;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import appeng.api.storage.ITerminalHost;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Carries one already-validated CPU through AE's synchronous menu factory without exposing AE private state.
 */
public final class TrinityCraftingStatusSelection {

    private static final ThreadLocal<PendingSelection> PENDING = new ThreadLocal<>();

    private TrinityCraftingStatusSelection() {}

    /** Opens one AE menu inside a non-nestable selection scope consumed by its server-side constructor. */
    public static boolean open(ServerPlayer player,
                               ITerminalHost host,
                               Target target,
                               BooleanSupplier opener) {
        if (player == null || host == null || target == null || opener == null) {
            throw new IllegalArgumentException("Trinity crafting-status selection arguments cannot be null");
        }
        if (PENDING.get() != null) {
            throw new IllegalStateException("Nested Trinity crafting-status menu selection is not supported");
        }

        PendingSelection selection = new PendingSelection(player, host, target);
        boolean opened;
        PENDING.set(selection);
        try {
            opened = opener.getAsBoolean();
        } finally {
            PENDING.remove();
        }
        if (opened && (!selection.claimed || !selection.selected)) {
            if (player.containerMenu instanceof CraftingStatusMenu) {
                player.closeContainer();
            }
            throw new IllegalStateException("AE crafting-status menu did not apply its Trinity CPU selection");
        }
        return opened;
    }

    /** Claims the exact player/host scoped selection from the matching server-side AE menu constructor. */
    public static @Nullable Target claim(Player player, ITerminalHost host) {
        PendingSelection selection = PENDING.get();
        if (selection == null || selection.player != player || selection.host != host) {
            return null;
        }
        if (selection.claimed) {
            throw new IllegalStateException("Trinity crafting-status selection was claimed more than once");
        }
        selection.claimed = true;
        return selection.target;
    }

    /** Marks the claimed target as selected before AE sends the menu's first full synchronization. */
    public static void markSelected(Target target) {
        PendingSelection selection = PENDING.get();
        if (target == null || selection == null || !selection.claimed || selection.target != target) {
            throw new IllegalStateException("No matching Trinity crafting-status selection is being constructed");
        }
        if (selection.selected) {
            throw new IllegalStateException("Trinity crafting-status selection was applied more than once");
        }
        selection.selected = true;
    }

    /** Exposes the immutable server target retained by the mixed-in AE submenu. */
    public interface TargetedMenu {

        /** Returns the exact Trinity target retained for stale-state validation and Back navigation. */
        @Nullable
        Target dataEnergistics$getTrinityTarget();
    }

    /** Captures the immutable menu route and the exact CPU pin selected while the worker is published. */
    public record Target(UUID hostId,
                         TrinityDataCoreCraftingRuntime runtime,
                         TrinityDataCoreVirtualCpu cpu,
                         TrinityCraftingExecutionRoute route) {

        public Target {
            if (hostId == null || runtime == null || cpu == null || route == null) {
                throw new IllegalArgumentException("Trinity crafting-status target identities cannot be null");
            }
        }

        /** Classifies the exact CPU pin while preserving a valid route after a worker finishes or is cancelled. */
        public TargetState currentState(@Nullable UUID currentHostId,
                                        @Nullable TrinityDataCoreCraftingRuntime currentRuntime,
                                        @Nullable TrinityCraftingExecutionRoute currentRoute) {
            List<TrinityDataCoreVirtualCpu> publishedCpus = currentRuntime == null ? List.of() : currentRuntime.publishedCpus();
            if (!matchesCurrentExecutionRoute(currentHostId, currentRuntime, currentRoute)) {
                return TargetState.STALE_ROUTE;
            }
            for (TrinityDataCoreVirtualCpu publishedCpu : publishedCpus) {
                if (publishedCpu == null) {
                    throw new IllegalStateException("Published Trinity CPU collection contains null");
                }
                if (publishedCpu == this.cpu &&
                        this.route.serviceGrid().getCraftingService().getCpus().contains(this.cpu)) {
                    return TargetState.CURRENT_CPU;
                }
            }
            return this.cpu.number() != 0 && !this.cpu.isBusy() ?
                    TargetState.RETIRED_WORKER : TargetState.STALE_ROUTE;
        }

        /** Verifies the immutable Host, runtime and Grid route independently of the retired worker object. */
        public boolean isRouteCurrent(@Nullable UUID currentHostId,
                                      @Nullable TrinityDataCoreCraftingRuntime currentRuntime,
                                      @Nullable TrinityCraftingExecutionRoute currentRoute) {
            return matchesCurrentExecutionRoute(currentHostId, currentRuntime, currentRoute);
        }

        /** Compares the Host/runtime identity and uninterrupted lease-membership route token. */
        private boolean matchesCurrentExecutionRoute(@Nullable UUID currentHostId,
                                                     @Nullable TrinityDataCoreCraftingRuntime currentRuntime,
                                                     @Nullable TrinityCraftingExecutionRoute currentRoute) {
            return this.hostId.equals(currentHostId) &&
                    this.runtime == currentRuntime &&
                    this.route.isCurrent(currentRoute);
        }
    }

    /** Stable lifecycle states used by the menu to distinguish worker retirement from a stale route. */
    public enum TargetState {
        CURRENT_CPU,
        RETIRED_WORKER,
        STALE_ROUTE
    }

    /**
     * Compares opaque identities without requiring Minecraft objects, so the stale-target contract remains directly
     * testable.
     */
    static <R, C, G> TargetState classifyCurrentState(UUID expectedHostId,
                                                      R expectedRuntime,
                                                      C expectedCpu,
                                                      G expectedGrid,
                                                      @Nullable UUID currentHostId,
                                                      @Nullable R currentRuntime,
                                                      List<C> currentPublishedCpus,
                                                      @Nullable G currentGrid,
                                                      BiPredicate<G, C> gridContainsCpu,
                                                      boolean retiredWorker) {
        if (expectedHostId == null || expectedRuntime == null || expectedCpu == null || expectedGrid == null ||
                currentPublishedCpus == null || gridContainsCpu == null) {
            throw new IllegalArgumentException("Trinity crafting-status identity collaborators cannot be null");
        }
        if (!matchesCurrentRoute(
                expectedHostId,
                expectedRuntime,
                expectedGrid,
                currentHostId,
                currentRuntime,
                currentGrid)) {
            return TargetState.STALE_ROUTE;
        }

        boolean published = false;
        for (C currentCpu : currentPublishedCpus) {
            if (currentCpu == null) {
                throw new IllegalStateException("Published Trinity CPU collection contains null");
            }
            if (currentCpu == expectedCpu) {
                published = true;
                break;
            }
        }
        if (published && gridContainsCpu.test(currentGrid, expectedCpu)) {
            return TargetState.CURRENT_CPU;
        }
        return retiredWorker ? TargetState.RETIRED_WORKER : TargetState.STALE_ROUTE;
    }

    static <R, G> boolean matchesCurrentRoute(UUID expectedHostId,
                                              R expectedRuntime,
                                              G expectedGrid,
                                              @Nullable UUID currentHostId,
                                              @Nullable R currentRuntime,
                                              @Nullable G currentGrid) {
        if (expectedHostId == null || expectedRuntime == null || expectedGrid == null) {
            throw new IllegalArgumentException("Trinity crafting-status route identities cannot be null");
        }
        return expectedHostId.equals(currentHostId) &&
                expectedRuntime == currentRuntime &&
                expectedGrid == currentGrid;
    }

    static <R, C, G> boolean matchesCurrentIdentity(UUID expectedHostId,
                                                    R expectedRuntime,
                                                    C expectedCpu,
                                                    G expectedGrid,
                                                    @Nullable UUID currentHostId,
                                                    @Nullable R currentRuntime,
                                                    List<C> currentPublishedCpus,
                                                    @Nullable G currentGrid,
                                                    BiPredicate<G, C> gridContainsCpu) {
        return classifyCurrentState(
                expectedHostId,
                expectedRuntime,
                expectedCpu,
                expectedGrid,
                currentHostId,
                currentRuntime,
                currentPublishedCpus,
                currentGrid,
                gridContainsCpu,
                false) == TargetState.CURRENT_CPU;
    }

    private static final class PendingSelection {

        private final ServerPlayer player;
        private final ITerminalHost host;
        private final Target target;
        private boolean claimed;
        private boolean selected;

        private PendingSelection(ServerPlayer player, ITerminalHost host, Target target) {
            this.player = player;
            this.host = host;
            this.target = target;
        }
    }
}
