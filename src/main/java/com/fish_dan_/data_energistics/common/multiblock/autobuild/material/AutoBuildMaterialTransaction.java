package com.fish_dan_.data_energistics.common.multiblock.autobuild.material;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Reserves exact materials in source order and accounts for every actual deduction until publication or refund.
 * Instances belong to one server-thread build attempt. Planning never mutates sources; failed commit attempts
 * must call rollback, including when an extraction threw after earlier deductions succeeded.
 */
public final class AutoBuildMaterialTransaction {

    private final Player player;
    private final List<SourceBudget> sources;
    private final boolean creative;
    private final Long2ObjectMap<Reservation> reservations = new Long2ObjectLinkedOpenHashMap<>();
    private boolean commitStarted;
    private boolean committed;
    private boolean closed;
    private boolean refunding;

    /** Opens ME and recursive player sources; creative players never query either source. */
    public static AutoBuildMaterialTransaction open(Player player, Supplier<@Nullable IGrid> connectedGrid) {
        if (player.isCreative()) return new AutoBuildMaterialTransaction(player, List.of());
        List<AutoBuildInventoryTraversal.Slot> slots = new AutoBuildInventoryTraversal().collect(
                new InvWrapper(player.getInventory()));
        List<AutoBuildMaterialSource> sources = new ObjectArrayList<>();
        AutoBuildMEAccess.addSources(sources, player, connectedGrid, slots);
        for (AutoBuildInventoryTraversal.Slot slot : slots) {
            sources.add(slot);
            sources.add(new AutoBuildFluidContainerSource(slot, player));
        }
        return new AutoBuildMaterialTransaction(player, sources);
    }

    private AutoBuildMaterialTransaction(Player player, List<AutoBuildMaterialSource> sources) {
        this.player = player;
        this.creative = player.isCreative();
        this.sources = sources.stream().map(SourceBudget::new).toList();
    }

    /** Tests the remaining simulated budget for an exact nonempty candidate without reserving it. */
    public boolean hasAvailable(GenericStack material) {
        requirePlanning();
        if (material.amount() <= 0) throw new IllegalArgumentException("Material amount must be positive");
        if (creative) return true;
        for (int first = 0; first < sources.size(); first++) {
            if (plan(material, first) != null) return true;
        }
        return false;
    }

    /**
     * Reserves one approved candidate for a unique world position, preferring source order before candidate order.
     * Returns the exact selected key and quantity, or null when none is available; missing positions are not reserved.
     */
    public @Nullable GenericStack reserve(BlockPos position, List<GenericStack> candidates) {
        requirePlanning();
        long packedPosition = position.asLong();
        if (reservations.containsKey(packedPosition)) throw new IllegalArgumentException("Position already reserved: " + position);
        if (candidates.isEmpty()) return null;
        if (creative) {
            reservations.put(packedPosition, new Reservation(candidates.getFirst().what(), List.of()));
            return candidates.getFirst();
        }
        for (int first = 0; first < sources.size(); first++) {
            for (GenericStack candidate : candidates) {
                List<Debit> debits = plan(candidate, first);
                if (debits == null) continue;
                for (Debit debit : debits) {
                    debit.source().reserved.mergeLong(candidate.what(), debit.amount(), Math::addExact);
                }
                reservations.put(packedPosition, new Reservation(candidate.what(), List.copyOf(debits)));
                return candidate;
            }
        }
        return null;
    }

    private @Nullable List<Debit> plan(GenericStack material, int first) {
        long needed = material.amount();
        if (needed <= 0) throw new IllegalArgumentException("Material amount must be positive");
        if (sources.get(first).extractable(material.what(), needed) == 0) return null;
        List<Debit> debits = new ObjectArrayList<>();
        // Retry from later sources when an earlier partial allocation would require splitting a whole bucket.
        for (int index = first; index < sources.size(); index++) {
            SourceBudget source = sources.get(index);
            long amount = source.extractable(material.what(), needed);
            if (amount == 0) continue;
            debits.add(new Debit(source, amount));
            needed -= amount;
            if (needed == 0) return debits;
        }
        return null;
    }

    /**
     * Revalidates all selected sources, then extracts their reservations. False means sources changed; the caller
     * must roll back any earlier deductions before returning. This method may only be called once.
     */
    public boolean commit() {
        requirePlanning();
        commitStarted = true;
        if (creative) {
            committed = true;
            return true;
        }
        for (SourceBudget source : sources) {
            for (Object2LongMap.Entry<AEKey> entry : source.reserved.object2LongEntrySet()) {
                if (source.source.available(entry.getKey(), entry.getLongValue()) != entry.getLongValue()) return false;
            }
        }
        for (SourceBudget source : sources) {
            for (Object2LongMap.Entry<AEKey> entry : source.reserved.object2LongEntrySet()) {
                long requested = entry.getLongValue();
                long extracted = checked(source.source.extract(entry.getKey(), requested), requested);
                source.outstanding.put(entry.getKey(), extracted);
                if (extracted != requested) return false;
            }
        }
        player.getInventory().setChanged();
        committed = true;
        return true;
    }

    /** Marks published positions as consumed, then returns every remaining actual deduction. */
    public RefundOutcome settlePublicationFailure(List<BlockPos> published) {
        if (!committed || closed) throw new IllegalStateException("Cannot settle an uncommitted material transaction");
        for (BlockPos position : published) {
            long packedPosition = position.asLong();
            Reservation reservation = reservations.remove(packedPosition);
            if (reservation == null) throw new IllegalArgumentException("Unreserved publication: " + position);
            for (Debit debit : reservation.debits()) {
                Object2LongMap<AEKey> outstanding = debit.source().outstanding;
                outstanding.put(reservation.key(), outstanding.getLong(reservation.key()) - debit.amount());
            }
        }
        return rollback();
    }

    /** Returns outstanding deductions once; repeated rollback after successful settlement has no effect. */
    public RefundOutcome rollback() {
        if (closed) return new RefundOutcome(true, null);
        if (refunding) return new RefundOutcome(false, "material refund is already in progress");
        refunding = true;
        try {
            return refundOutstanding();
        } finally {
            refunding = false;
        }
    }

    private RefundOutcome refundOutstanding() {
        boolean failed = false;
        for (SourceBudget source : sources) {
            for (Object2LongMap.Entry<AEKey> entry : source.outstanding.object2LongEntrySet()) {
                if (entry.getLongValue() == 0) continue;
                try {
                    refund(source.source, entry);
                } catch (RuntimeException exception) {
                    failed = true;
                    Data_Energistics.LOGGER.error("Auto-build refund failed for player {}, material {}, remaining {}",
                            player.getUUID(), entry.getKey(), entry.getLongValue(), exception);
                }
            }
        }
        player.getInventory().setChanged();
        if (failed) return new RefundOutcome(false, "one or more material refunds failed; see server log");
        close();
        return new RefundOutcome(true, null);
    }

    /** Closes a successful publication, retaining every deducted material as consumed. */
    public void complete() {
        if (!committed || closed) throw new IllegalStateException("Cannot complete an uncommitted material transaction");
        close();
    }

    private void refund(AutoBuildMaterialSource source, Object2LongMap.Entry<AEKey> entry) {
        long remaining = entry.getLongValue();
        entry.setValue(remaining - checked(source.refund(entry.getKey(), remaining), remaining));
        if (!(entry.getKey() instanceof AEItemKey item)) {
            for (SourceBudget alternate : sources) {
                if (entry.getLongValue() == 0) return;
                if (alternate.source == source) continue;
                long offered = entry.getLongValue();
                entry.setValue(offered - checked(alternate.source.refund(entry.getKey(), offered), offered));
            }
            if (entry.getLongValue() > 0) {
                AutoBuildStackDelivery.give(player, GenericStack.wrapInItemStack(entry.getKey(), entry.getLongValue()));
                entry.setValue(0);
            }
            return;
        }
        int maxStackSize = item.toStack(1).getMaxStackSize();
        while (entry.getLongValue() > 0) {
            int count = (int) Math.min(entry.getLongValue(), maxStackSize);
            ItemStack stack = item.toStack(count);
            try {
                player.getInventory().add(stack);
            } finally {
                entry.setValue(entry.getLongValue() - (count - stack.getCount()));
            }
            if (stack.isEmpty()) continue;
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(),
                    stack);
            if (!player.level().addFreshEntity(entity)) throw new IllegalStateException("World rejected material refund");
            entry.setValue(entry.getLongValue() - stack.getCount());
        }
    }

    private static long checked(long amount, long requested) {
        if (amount < 0 || amount > requested) throw new IllegalStateException("Invalid material transfer amount: " + amount);
        return amount;
    }

    private void requirePlanning() {
        if (commitStarted || closed) throw new IllegalStateException("Material planning is closed");
    }

    private void close() {
        closed = true;
        sources.forEach(source -> {
            source.available.clear();
            source.reserved.clear();
            source.outstanding.clear();
        });
        reservations.clear();
    }

    /** Result of actual refund delivery, with diagnostic detail only on failure. */
    public record RefundOutcome(boolean completed, @Nullable String detail) {}

    private record Reservation(AEKey key, List<Debit> debits) {}

    private record Debit(SourceBudget source, long amount) {}

    private static final class SourceBudget {

        private final AutoBuildMaterialSource source;
        private final Object2LongMap<AEKey> available = new Object2LongLinkedOpenHashMap<>();
        private final Object2LongMap<AEKey> reserved = new Object2LongLinkedOpenHashMap<>();
        private final Object2LongMap<AEKey> outstanding = new Object2LongLinkedOpenHashMap<>();

        private SourceBudget(AutoBuildMaterialSource source) {
            this.source = source;
        }

        private long extractable(AEKey key, long additional) {
            if (!available.containsKey(key)) available.put(key, checked(source.available(key, Long.MAX_VALUE), Long.MAX_VALUE));
            long alreadyReserved = reserved.getLong(key);
            long requested = Math.min(additional, available.getLong(key) - alreadyReserved);
            if (requested == 0) return 0;
            long total = Math.addExact(alreadyReserved, requested);
            long simulated = checked(source.available(key, total), total);
            return Math.max(0, simulated - alreadyReserved);
        }
    }
}
