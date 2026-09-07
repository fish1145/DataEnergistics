package com.fish_dan_.data_energistics.common.solar.energy;

import appeng.api.config.Actionable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.List;

/**
 * Provides a transient aggregate view over the raw energy stored by a connected group of solar panels.
 *
 * <p>
 * The pool owns no energy and performs no world, grid, persistence, or synchronization work. Each cell remains the
 * sole owner of its stored amount, so rebuilding pools after a topology change cannot duplicate or discard energy.
 * Instances are confined to the logical server thread and are not thread-safe.
 * </p>
 */
public final class SolarEnergyPool {

    private final ObjectArrayList<Cell> cells;
    private int nextInsertIndex;
    private int nextExtractIndex;

    /**
     * Creates a pool from the supplied cells, retaining their order and removing repeated object identities.
     *
     * <p>
     * The membership snapshot is immutable for the lifetime of this pool. Callers must create a new pool when the
     * connected component changes.
     * </p>
     *
     * @param cells cells whose raw storage participates in this pool
     */
    public SolarEnergyPool(List<? extends Cell> cells) {
        this.cells = new ObjectArrayList<>(cells.size());
        ReferenceOpenHashSet<Cell> uniqueCells = new ReferenceOpenHashSet<>(cells.size());
        for (Cell cell : cells) {
            if (uniqueCells.add(cell)) {
                this.cells.add(cell);
            }
        }
    }

    /**
     * Computes the current aggregate without changing cell state or the round-robin cursors.
     *
     * @return finite aggregate stored energy and capacity
     * @throws IllegalStateException if a cell reports a non-finite or negative value, or if the aggregate overflows
     */
    public Snapshot snapshot() {
        double stored = 0.0D;
        double capacity = 0.0D;
        for (Cell cell : this.cells) {
            stored = addFinite(stored, validateCellState(cell.stored(), "stored energy"), "stored energy");
            capacity = addFinite(capacity, validateCellState(cell.capacity(), "capacity"), "capacity");
        }
        return new Snapshot(stored, capacity);
    }

    /**
     * Inserts energy across the cells in round-robin order.
     *
     * @param amount finite, non-negative amount offered to the pool
     * @param mode   whether the operation may mutate the cells
     * @return the amount accepted by the cells, in the inclusive range {@code [0, amount]}
     * @throws IllegalArgumentException if {@code amount} is negative or non-finite
     * @throws IllegalStateException    if a cell violates its return-value contract
     */
    public double insert(double amount, Actionable mode) {
        validateAmount(amount);
        if (amount == 0.0D || this.cells.isEmpty()) {
            return 0.0D;
        }

        double remaining = amount;
        double transferred = 0.0D;
        int startIndex = this.nextInsertIndex;
        int lastAcceptedIndex = -1;
        for (int offset = 0; offset < this.cells.size() && remaining > 0.0D; offset++) {
            int index = (startIndex + offset) % this.cells.size();
            double accepted = validateAccepted(
                    this.cells.get(index).insert(remaining, mode),
                    remaining,
                    "insert");
            if (accepted > 0.0D) {
                remaining -= accepted;
                transferred += accepted;
                lastAcceptedIndex = index;
            }
        }

        if (mode == Actionable.MODULATE) {
            this.nextInsertIndex = nextIndex(lastAcceptedIndex >= 0 ? lastAcceptedIndex : startIndex);
        }
        return transferred;
    }

    /**
     * Extracts energy across the cells in round-robin order.
     *
     * @param amount finite, non-negative amount requested from the pool
     * @param mode   whether the operation may mutate the cells
     * @return the amount extracted from the cells, in the inclusive range {@code [0, amount]}
     * @throws IllegalArgumentException if {@code amount} is negative or non-finite
     * @throws IllegalStateException    if a cell violates its return-value contract
     */
    public double extract(double amount, Actionable mode) {
        validateAmount(amount);
        if (amount == 0.0D || this.cells.isEmpty()) {
            return 0.0D;
        }

        double remaining = amount;
        double transferred = 0.0D;
        int startIndex = this.nextExtractIndex;
        int lastAcceptedIndex = -1;
        for (int offset = 0; offset < this.cells.size() && remaining > 0.0D; offset++) {
            int index = (startIndex + offset) % this.cells.size();
            double accepted = validateAccepted(
                    this.cells.get(index).extract(remaining, mode),
                    remaining,
                    "extract");
            if (accepted > 0.0D) {
                remaining -= accepted;
                transferred += accepted;
                lastAcceptedIndex = index;
            }
        }

        if (mode == Actionable.MODULATE) {
            this.nextExtractIndex = nextIndex(lastAcceptedIndex >= 0 ? lastAcceptedIndex : startIndex);
        }
        return transferred;
    }

    /**
     * Moves energy out of one member and into other members before the source capacity is reduced.
     *
     * <p>
     * Only energy accepted by another cell is removed from {@code source}. If the other cells cannot accept the full
     * amount, the remainder stays in the source for the caller's subsequent local capacity update. This operation
     * always
     * modulates raw cell state.
     * </p>
     *
     * @param source    pool member whose stored energy is being relocated
     * @param maxAmount finite, non-negative maximum amount to relocate
     * @return the amount moved from the source into other cells
     * @throws IllegalArgumentException if the source is not a member, or if {@code maxAmount} is invalid
     * @throws IllegalStateException    if a cell violates its simulation or mutation contract
     */
    public double transferToOtherCells(Cell source, double maxAmount) {
        validateAmount(maxAmount);
        int sourceIndex = identityIndexOf(source);
        if (sourceIndex < 0) {
            throw new IllegalArgumentException("Source cell must be a member of this pool");
        }
        if (maxAmount == 0.0D || this.cells.size() < 2) {
            return 0.0D;
        }

        double remaining = maxAmount;
        double moved = 0.0D;
        int startIndex = this.nextInsertIndex;
        int lastAcceptedIndex = -1;
        for (int offset = 0; offset < this.cells.size() && remaining > 0.0D; offset++) {
            int index = (startIndex + offset) % this.cells.size();
            if (index == sourceIndex) {
                continue;
            }

            Cell target = this.cells.get(index);
            double available = validateAccepted(
                    source.extract(remaining, Actionable.SIMULATE),
                    remaining,
                    "source extract simulation");
            if (available == 0.0D) {
                break;
            }
            double receivable = validateAccepted(
                    target.insert(available, Actionable.SIMULATE),
                    available,
                    "target insert simulation");
            if (receivable == 0.0D) {
                continue;
            }

            double extracted = validateAccepted(
                    source.extract(receivable, Actionable.MODULATE),
                    receivable,
                    "source extract");
            double inserted = validateAccepted(
                    target.insert(extracted, Actionable.MODULATE),
                    extracted,
                    "target insert");
            if (inserted < extracted) {
                double rollbackAmount = extracted - inserted;
                double rolledBack = validateAccepted(
                        source.insert(rollbackAmount, Actionable.MODULATE),
                        rollbackAmount,
                        "source rollback");
                if (rolledBack != rollbackAmount) {
                    throw new IllegalStateException("Source cell did not accept the full transfer rollback");
                }
            }

            if (inserted > 0.0D) {
                moved += inserted;
                remaining -= inserted;
                lastAcceptedIndex = index;
            }
        }

        this.nextInsertIndex = nextIndex(lastAcceptedIndex >= 0 ? lastAcceptedIndex : startIndex);
        return moved;
    }

    private int identityIndexOf(Cell expected) {
        for (int index = 0; index < this.cells.size(); index++) {
            if (this.cells.get(index) == expected) {
                return index;
            }
        }
        return -1;
    }

    private int nextIndex(int index) {
        return (index + 1) % this.cells.size();
    }

    private static void validateAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D) {
            throw new IllegalArgumentException("Energy amount must be finite and non-negative: " + amount);
        }
    }

    private static double validateCellState(double amount, String description) {
        if (!Double.isFinite(amount) || amount < 0.0D) {
            throw new IllegalStateException("Cell reported invalid " + description + ": " + amount);
        }
        return amount;
    }

    private static double validateAccepted(double accepted, double offered, String operation) {
        if (!Double.isFinite(accepted) || accepted < 0.0D || accepted > offered) {
            throw new IllegalStateException(
                    "Cell reported invalid accepted amount for " + operation + ": " + accepted + " of " + offered);
        }
        return accepted;
    }

    private static double addFinite(double total, double value, String description) {
        double result = total + value;
        if (!Double.isFinite(result)) {
            throw new IllegalStateException("Aggregate " + description + " exceeds the finite double range");
        }
        return result;
    }

    /**
     * A raw, independently persisted energy contribution to a pool.
     *
     * <p>
     * Cells are called only on the logical server thread and must remain valid for the pool's lifetime. Insert and
     * extract implementations must operate on this cell alone and must not call back into a shared pool, which would
     * recurse. For modulating operations the cell is also responsible for marking its owning state for persistence.
     * </p>
     */
    public interface Cell {

        /**
         * Returns this cell's raw stored amount without changing state.
         *
         * @return a finite, non-negative amount owned by this cell
         */
        double stored();

        /**
         * Returns this cell's raw storage capacity without changing state.
         *
         * @return a finite, non-negative capacity owned by this cell
         */
        double capacity();

        /**
         * Offers energy to this cell's raw storage.
         *
         * <p>
         * {@link Actionable#SIMULATE} must report the amount that could be accepted without changing state.
         * {@link Actionable#MODULATE} must apply the returned amount and mark the owning state for persistence.
         * </p>
         *
         * @param amount finite, non-negative amount offered
         * @param mode   whether raw state may be mutated
         * @return the accepted amount in the inclusive range {@code [0, amount]}
         */
        double insert(double amount, Actionable mode);

        /**
         * Requests energy from this cell's raw storage.
         *
         * <p>
         * {@link Actionable#SIMULATE} must report the amount that could be extracted without changing state.
         * {@link Actionable#MODULATE} must apply the returned amount and mark the owning state for persistence.
         * </p>
         *
         * @param amount finite, non-negative amount requested
         * @param mode   whether raw state may be mutated
         * @return the extracted amount in the inclusive range {@code [0, amount]}
         */
        double extract(double amount, Actionable mode);
    }

    /**
     * An immutable aggregate reading of the pool.
     *
     * @param stored   total raw energy held by all distinct cells
     * @param capacity total raw capacity contributed by all distinct cells
     */
    public record Snapshot(double stored, double capacity) {}
}
