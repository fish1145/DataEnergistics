package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCanonicalNbt;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import com.google.common.hash.Hashing;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Exact-state conservation and replay identity at the directed CPU return boundary. */
public final class ReusableCpuSettlement {

    private ReusableCpuSettlement() {}

    /**
     * A failed executor retains its real outbox for diagnosis. Only fully explained transitions are accepted here;
     * an exhaustion claim cannot hide missing tools or a successor with different data components.
     * Invoke only from the ledger settlement callback, after receipt identity, counts and uniqueness are checked.
     */
    public static void verify(ReusableCpuSessionLedger.Session session, Settlement settlement) {
        if (settlement.failure().isPresent()) {
            throw new IllegalStateException("Reusable executor requires recovery: " + settlement.failure().orElseThrow());
        }
        if (!settlement.releasedMachineTools().isEmpty()) {
            throw new IllegalStateException("CPU-supplied session cannot release machine-owned tools");
        }
        Map<AEKey, BigInteger> expected = new Object2ObjectOpenHashMap<>();
        BigInteger exhausted = BigInteger.ZERO;
        for (var receipt : settlement.receipts()) {
            var submission = session.submission(receipt.sequence());
            var bindings = submission.work().exactBindings();
            for (var delivered : submission.physicalInputs()) {
                if (bindings.get(delivered.slot()).reusableRule() != null) {
                    add(expected, delivered.stack().what(), BigInteger.valueOf(delivered.stack().amount()));
                }
            }
            for (var binding : bindings) {
                BigInteger units = binding.consumedAmount();
                if (binding.reusableRule() == null) {
                    add(expected, binding.template().what(), units.multiply(BigInteger.valueOf(receipt.cancelled())));
                    continue;
                }
                BigInteger used = units.multiply(BigInteger.valueOf(receipt.completed()));
                var state = (AEItemKey) binding.template().what();
                var successor = binding.reusableRule().advance(state, 1L).successor();
                add(expected, state, used.negate());
                if (successor == null) {
                    exhausted = exhausted.add(used);
                } else {
                    add(expected, successor, used);
                }
            }
        }
        if (!exhausted.equals(BigInteger.valueOf(settlement.exhaustedTools())) || !expected.equals(amounts(settlement.returnedAssets()))) {
            throw new IllegalStateException("Reusable return does not conserve delivered materials and exact tool transitions");
        }
    }

    /** Stable across stack splitting, list ordering and NBT compound ordering; not a source of physical assets. */
    public static String fingerprint(Settlement settlement, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.putUUID("session", settlement.sessionId());
        root.putUUID("job", settlement.jobId());
        root.putString("owner", settlement.cpuOwner());
        root.putString("target", settlement.targetIdentity());
        root.putLong("sequence", settlement.sequence());
        root.putLong("exhausted", settlement.exhaustedTools());
        root.put("assets", assets(amounts(settlement.returnedAssets()), registries));
        CompoundTag released = new CompoundTag();
        for (var asset : settlement.releasedMachineTools()) {
            String slot = Integer.toString(asset.slot());
            CompoundTag quantities = released.getCompound(slot);
            String key = TrinityCanonicalNbt.encode(asset.stack().what().toTagGeneric(registries));
            BigInteger amount = quantities.contains(key) ? new BigInteger(quantities.getByteArray(key)) : BigInteger.ZERO;
            quantities.putByteArray(key, amount.add(BigInteger.valueOf(asset.stack().amount())).toByteArray());
            released.put(slot, quantities);
        }
        root.put("released", released);
        CompoundTag receipts = new CompoundTag();
        for (var receipt : settlement.receipts()) {
            String sequence = Long.toString(receipt.sequence());
            if (receipts.contains(sequence)) {
                throw new IllegalArgumentException("Duplicate reusable settlement receipt");
            }
            receipts.putLongArray(sequence, new long[] { receipt.accepted(), receipt.completed(), receipt.cancelled() });
        }
        root.put("receipts", receipts);
        settlement.failure().ifPresent(value -> root.putString("failure", value));
        return Hashing.sha256().hashString(TrinityCanonicalNbt.encode(root), StandardCharsets.UTF_8).toString();
    }

    private static CompoundTag assets(Map<AEKey, BigInteger> amounts, HolderLookup.Provider registries) {
        CompoundTag result = new CompoundTag();
        amounts.forEach((key, amount) -> result.putByteArray(TrinityCanonicalNbt.encode(key.toTagGeneric(registries)), amount.toByteArray()));
        return result;
    }

    private static Map<AEKey, BigInteger> amounts(List<GenericStack> stacks) {
        Map<AEKey, BigInteger> result = new Object2ObjectOpenHashMap<>();
        for (var stack : stacks) add(result, stack.what(), BigInteger.valueOf(stack.amount()));
        return result;
    }

    private static void add(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger delta) {
        BigInteger updated = amounts.getOrDefault(key, BigInteger.ZERO).add(delta);
        if (updated.signum() == 0) amounts.remove(key);
        else amounts.put(key, updated);
    }
}
