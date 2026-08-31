package com.fish_dan_.data_energistics.network.trinity.crafting.assembly;

import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic.TrinityCraftingExactShortage;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic.TrinityCraftingUnresolvedDemand;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleHeader;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingExactPlanAmounts;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCyclePayload;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.ExactPlanAmounts;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.ExactShortage;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.Header;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.InventoryUsage;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.Material;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.UnresolvedDemand;

import appeng.api.stacks.AEKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reassembles independently delivered batches and exposes no partial Trinity confirmation state.
 */
public final class TrinityCraftConfirmCycleAssembler {

    private final Map<Integer, Assembly> assemblies = new HashMap<>();

    /** Accepts one validated batch and publishes exactly once after all unique batches arrive. */
    public synchronized Optional<Snapshot> accept(TrinityCraftConfirmCyclePayload payload) {
        Assembly assembly = this.assemblies.get(payload.containerId());
        if (assembly != null && payload.revision() < assembly.revision) {
            return Optional.empty();
        }
        if (assembly == null || payload.revision() > assembly.revision) {
            assembly = new Assembly(payload.revision(), payload.batchCount(), payload.totalRecordCount());
            this.assemblies.put(payload.containerId(), assembly);
        }
        return assembly.accept(payload);
    }

    /** Drops all state when the client replaces the active confirmation menu instance. */
    public synchronized void clear() {
        this.assemblies.clear();
    }

    private static TrinityCraftingCycleSummary rebuildSummary(List<TrinityCraftConfirmCycleRecord> records) {
        ArrayList<TrinityCraftingCycleHeader> cycles = new ArrayList<>();
        ArrayList<TrinityCraftingCycleMaterialContribution> contributions = new ArrayList<>();
        LinkedHashMap<AEKey, Integer> inventoryUsage = new LinkedHashMap<>();
        ArrayList<TrinityCraftingExactShortage> exactShortages = new ArrayList<>();
        ArrayList<TrinityCraftingUnresolvedDemand> unresolvedDemands = new ArrayList<>();
        ArrayList<TrinityCraftingExactPlanAmounts> exactPlanAmounts = new ArrayList<>();
        for (TrinityCraftConfirmCycleRecord record : records) {
            switch (record) {
                case Header entry -> cycles.add(entry.value());
                case Material entry -> contributions.add(entry.value());
                case ExactShortage entry -> exactShortages.add(entry.value());
                case UnresolvedDemand entry -> unresolvedDemands.add(entry.value());
                case ExactPlanAmounts entry -> exactPlanAmounts.add(entry.value());
                case InventoryUsage entry -> {
                    if (inventoryUsage.containsKey(entry.key())) {
                        throw new IllegalArgumentException(
                                "Trinity crafting confirmation summary repeats an inventory usage record");
                    }
                    inventoryUsage.put(entry.key(), entry.basisPoints());
                }
            }
        }
        return TrinityCraftingCycleSummary.create(
                inventoryUsage,
                cycles,
                contributions,
                exactShortages,
                unresolvedDemands,
                exactPlanAmounts);
    }

    /** One complete summary revision ready for delivery to its matching menu. */
    public record Snapshot(int containerId, long revision, TrinityCraftingCycleSummary summary) {}

    /** Mutable state for exactly one container revision. */
    private static final class Assembly {

        private final long revision;
        private final int batchCount;
        private final int totalRecordCount;
        private final Map<Integer, List<TrinityCraftConfirmCycleRecord>> batches = new HashMap<>();
        private int receivedRecordCount;
        private boolean published;

        private Assembly(long revision, int batchCount, int totalRecordCount) {
            this.revision = revision;
            this.batchCount = batchCount;
            this.totalRecordCount = totalRecordCount;
        }

        private Optional<Snapshot> accept(TrinityCraftConfirmCyclePayload payload) {
            if (payload.batchCount() != this.batchCount || payload.totalRecordCount() != this.totalRecordCount) {
                throw new IllegalArgumentException(
                        "Trinity crafting confirmation batch metadata changed within one revision");
            }
            if (this.published || this.batches.containsKey(payload.batchIndex())) {
                throw new IllegalArgumentException("Duplicate Trinity crafting confirmation batch");
            }

            this.batches.put(payload.batchIndex(), payload.records());
            this.receivedRecordCount = Math.addExact(this.receivedRecordCount, payload.records().size());
            if (this.receivedRecordCount > this.totalRecordCount) {
                throw new IllegalArgumentException(
                        "Trinity crafting confirmation batches exceed their declared total");
            }
            if (this.batches.size() != this.batchCount) {
                return Optional.empty();
            }
            if (this.receivedRecordCount != this.totalRecordCount) {
                throw new IllegalArgumentException(
                        "Trinity crafting confirmation batches do not match their declared total");
            }

            ArrayList<TrinityCraftConfirmCycleRecord> records = new ArrayList<>(this.totalRecordCount);
            for (int batchIndex = 0; batchIndex < this.batchCount; batchIndex++) {
                records.addAll(this.batches.get(batchIndex));
            }
            TrinityCraftingCycleSummary summary = rebuildSummary(records);
            this.published = true;
            return Optional.of(new Snapshot(payload.containerId(), this.revision, summary));
        }
    }
}
