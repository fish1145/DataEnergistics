package com.fish_dan_.data_energistics.common.solar.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.machine.DataSolarPanelBlock;
import com.fish_dan_.data_energistics.blockentity.machine.DataSolarPanelBlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Server-thread, transient membership and tick ownership for a four-way connected solar array. Energy remains in
 * independently saved cells; neither rebuilding this view nor connecting AE ports creates a second copy of it.
 */
public final class SolarPanelArray {

    private final List<Membership> members;
    private final SolarEnergyPool energy;
    private boolean valid = true;
    private boolean failed;
    private long lastTick = Long.MIN_VALUE;
    private int nextOutput;

    private SolarPanelArray(List<Membership> members) {
        this.members = List.copyOf(members);
        this.energy = new SolarEnergyPool(members.stream().map(member -> member.cell).toList());
    }

    private static SolarPanelArray discover(Membership origin) {
        Level level = origin.owner.getLevel();
        if (!origin.ready || level == null || level.isClientSide || origin.owner.isRemoved()) {
            return new SolarPanelArray(List.of(origin));
        }
        ObjectArrayFIFOQueue<Membership> pending = new ObjectArrayFIFOQueue<>();
        ReferenceOpenHashSet<Membership> visited = new ReferenceOpenHashSet<>();
        ObjectArrayList<Membership> members = new ObjectArrayList<>();
        visited.add(origin);
        pending.enqueue(origin);
        while (!pending.isEmpty()) {
            Membership member = pending.dequeue();
            members.add(member);
            BlockState state = member.owner.getBlockState();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!DataSolarPanelBlock.connectsOnSide(state, direction)) {
                    continue;
                }
                BlockPos neighborPos = member.owner.getBlockPos().relative(direction);
                if (level.hasChunkAt(neighborPos) &&
                        level.getBlockEntity(neighborPos) instanceof DataSolarPanelBlockEntity neighbor &&
                        !neighbor.isRemoved() && neighbor.energyMembership().ready &&
                        DataSolarPanelBlock.connectsOnSide(neighbor.getBlockState(), direction.getOpposite())) {
                    Membership next = neighbor.energyMembership();
                    if (visited.add(next)) {
                        pending.enqueue(next);
                    }
                }
            }
        }
        members.sort(Comparator.comparingLong(member -> member.owner.getBlockPos().asLong()));
        SolarPanelArray array = new SolarPanelArray(members);
        for (Membership member : members) {
            member.invalidate();
        }
        for (Membership member : members) {
            member.array = array;
        }
        return array;
    }

    private void tick(long gameTime) {
        if (this.failed || this.lastTick == gameTime) {
            return;
        }
        this.lastTick = gameTime;
        try {
            for (Membership member : this.members) {
                member.owner.refreshArrayCapacity();
            }
            SolarEnergyPool.Snapshot snapshot = this.energy.snapshot();
            double room = snapshot.capacity() - snapshot.stored();
            double generated = 0.0D;
            for (Membership member : this.members) {
                // A rebuild can happen during the tick: the per-member stamp prevents generating twice.
                double contribution = member.generatedOnce(gameTime);
                generated += Math.min(room - generated, contribution);
            }
            this.energy.insert(generated, Actionable.MODULATE);
            exportToGrid();
        } catch (RuntimeException exception) {
            this.failed = true;
            Data_Energistics.LOGGER.error("Stopped solar array at {} ({} panels) after an energy operation failed",
                    this.members.getFirst().owner.getBlockPos(), this.members.size(), exception);
        }
    }

    private void exportToGrid() {
        double available = this.energy.snapshot().stored();
        if (available == 0.0D) {
            return;
        }
        ReferenceOpenHashSet<IGrid> seen = new ReferenceOpenHashSet<>();
        ObjectArrayList<IGrid> outputs = new ObjectArrayList<>();
        for (Membership member : this.members) {
            IGridNode node = member.owner.getMainNode().getNode();
            if (node != null && member.owner.allowsArrayGeneration() && seen.add(node.getGrid())) {
                outputs.add(node.getGrid());
            }
        }
        if (outputs.isEmpty()) {
            return;
        }
        int start = this.nextOutput % outputs.size();
        this.nextOutput = (start + 1) % outputs.size();
        for (int offset = 0; offset < outputs.size() && available > 0.0D; offset++) {
            // Private buffers cannot be grid injection targets. Try each distinct grid once so full/isolated
            // ports do not delay a working bottom port, while rotating the first recipient for fairness.
            IGrid grid = outputs.get((start + offset) % outputs.size());
            double overflow = grid.getEnergyService().injectPower(available, Actionable.MODULATE);
            if (!Double.isFinite(overflow) || overflow < 0.0D || overflow > available) {
                throw new IllegalStateException("AE grid returned an invalid solar energy remainder: " + overflow);
            }
            double accepted = available - overflow;
            double extracted = this.energy.extract(accepted, Actionable.MODULATE);
            if (Math.abs(extracted - accepted) > Math.ulp(available) * this.members.size()) {
                throw new IllegalStateException("Solar array energy changed during grid injection");
            }
            available = overflow;
        }
    }

    /**
     * One block entity's stable handle. Private node ports retain this handle while its cached component changes.
     * Only onReady/setRemoved/chunk-unload and cardinal connection changes affect membership, never GUI reads.
     */
    public static final class Membership {

        private final DataSolarPanelBlockEntity owner;
        private final SolarEnergyPool.Cell cell;
        private @Nullable SolarPanelArray array;
        private boolean ready;
        private long lastGenerationTick = Long.MIN_VALUE;

        public Membership(DataSolarPanelBlockEntity owner, SolarEnergyPool.Cell cell) {
            this.owner = owner;
            this.cell = cell;
        }

        /** Activates the loaded server member after block-state and node initialization. */
        public void onReady() {
            this.ready = true;
            invalidate();
            Level level = this.owner.getLevel();
            if (level != null) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos pos = this.owner.getBlockPos().relative(direction);
                    if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof DataSolarPanelBlockEntity neighbor) {
                        neighbor.energyMembership().invalidate();
                    }
                }
            }
        }

        /** Releases a loaded view without moving the owner's persisted energy to another chunk. */
        public void onUnavailable() {
            this.ready = false;
            invalidate();
            this.array = null;
        }

        /** Invalidates every member's shared view, without eagerly traversing or changing the world. */
        public void invalidate() {
            if (this.array != null) {
                this.array.valid = false;
            }
        }

        private SolarPanelArray resolve() {
            if (this.array == null || !this.array.valid) {
                this.array = discover(this);
            }
            return this.array;
        }

        public SolarEnergyPool.Snapshot snapshot() {
            return resolve().energy.snapshot();
        }

        /** Returns accepted energy; callers adapt AE's remainder-returning boundary separately. */
        public double insert(double amount, Actionable mode) {
            return !this.ready || this.owner.isRemoved() ? 0.0D : resolve().energy.insert(amount, mode);
        }

        public double extract(double amount, Actionable mode) {
            return !this.ready || this.owner.isRemoved() ? 0.0D : resolve().energy.extract(amount, mode);
        }

        /** Moves any recoverable surplus before the owner's raw capacity setter clamps its own cell. */
        public void beforeCapacityChange(double capacity) {
            double excess = this.cell.stored() - capacity;
            if (excess > 0.0D) {
                resolve().energy.transferToOtherCells(this.cell, excess);
            }
        }

        public void tick() {
            Level level = this.owner.getLevel();
            if (this.ready && level != null && !level.isClientSide) {
                resolve().tick(level.getGameTime());
            }
        }

        private double generatedOnce(long gameTime) {
            if (!this.ready || this.lastGenerationTick == gameTime) {
                return 0.0D;
            }
            this.lastGenerationTick = gameTime;
            if (!this.owner.allowsArrayGeneration()) {
                return 0.0D;
            }
            double generated = this.owner.getGeneratedPowerPerTick();
            if (Double.isNaN(generated) || generated < 0.0D) {
                throw new IllegalStateException("Invalid solar generation at " + this.owner.getBlockPos());
            }
            return generated;
        }
    }
}
