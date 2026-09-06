package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules.ReusableInputRuleNbtCodec;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Exact contextual bindings survive save/reload without reinterpreting expanded ordinals against old templates. */
public final class TrinityBoundInputSnapshotCodec {

    private TrinityBoundInputSnapshotCodec() {}

    public static ListTag write(List<TrinityBoundPatternInput> bindings, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (TrinityBoundPatternInput binding : bindings) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", binding.slotIndex());
            entry.putInt("alternative", binding.alternativeIndex());
            entry.put("template", GenericStack.writeTag(registries, binding.template()));
            entry.putLong("multiplier", binding.multiplier());
            entry.putBoolean("has_remainder", binding.remainingKey() != null);
            if (binding.remainingKey() != null) {
                entry.put("remainder", binding.remainingKey().toTagGeneric(registries));
            }
            entry.putBoolean("reusable", binding.reusableRule() != null);
            if (binding.reusableRule() != null) {
                entry.put("rule", ReusableInputRuleNbtCodec.encode(binding.reusableRule(), registries));
            }
            ListTag byproducts = new ListTag();
            binding.byproducts().forEach(stack -> byproducts.add(GenericStack.writeTag(registries, stack)));
            entry.put("byproducts", byproducts);
            result.add(entry);
        }
        return result;
    }

    public static List<TrinityBoundPatternInput> read(ListTag encoded, HolderLookup.Provider registries) {
        if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Exact input bindings must be a compound list");
        }
        ObjectArrayList<TrinityBoundPatternInput> result = new ObjectArrayList<>(encoded.size());
        for (Tag value : encoded) {
            CompoundTag entry = (CompoundTag) value;
            require(entry, "slot", Tag.TAG_INT);
            require(entry, "alternative", Tag.TAG_INT);
            require(entry, "template", Tag.TAG_COMPOUND);
            require(entry, "multiplier", Tag.TAG_LONG);
            require(entry, "has_remainder", Tag.TAG_BYTE);
            require(entry, "reusable", Tag.TAG_BYTE);
            if (entry.getInt("slot") != result.size()) {
                throw new IllegalArgumentException("Exact input binding slots must be contiguous and ordered");
            }
            GenericStack template = GenericStack.readTag(registries, entry.getCompound("template"));
            if (template == null || template.amount() <= 0L) {
                throw new IllegalArgumentException("Invalid exact input template");
            }
            AEKey remaining = null;
            if (entry.getBoolean("has_remainder")) {
                require(entry, "remainder", Tag.TAG_COMPOUND);
                remaining = AEKey.fromTagGeneric(registries, entry.getCompound("remainder"));
                if (remaining == null) {
                    throw new IllegalArgumentException("Unknown exact input remainder");
                }
            } else if (entry.contains("remainder")) {
                throw new IllegalArgumentException("Contradictory exact input remainder");
            }
            ReusableInputRule rule = null;
            if (entry.getBoolean("reusable")) {
                require(entry, "rule", Tag.TAG_COMPOUND);
                rule = ReusableInputRuleNbtCodec.decode(entry.getCompound("rule"), registries);
            } else if (entry.contains("rule")) {
                throw new IllegalArgumentException("Unexpected reusable input rule");
            }
            if (!(entry.get("byproducts") instanceof ListTag products) ||
                    !products.isEmpty() && products.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Input byproducts must be a compound list");
            }
            ObjectArrayList<GenericStack> byproducts = new ObjectArrayList<>(products.size());
            for (Tag product : products) {
                GenericStack stack = GenericStack.readTag(registries, (CompoundTag) product);
                if (stack == null || stack.amount() <= 0L) {
                    throw new IllegalArgumentException("Invalid exact input byproduct");
                }
                byproducts.add(stack);
            }
            result.add(new TrinityBoundPatternInput(entry.getInt("slot"), entry.getInt("alternative"), template,
                    entry.getLong("multiplier"), remaining, rule, byproducts));
        }
        return List.copyOf(result);
    }

    private static void require(CompoundTag tag, String name, int type) {
        if (!tag.contains(name, type)) {
            throw new IllegalArgumentException("Missing or invalid exact input binding field: " + name);
        }
    }
}
