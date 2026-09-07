package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule.Transition;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Set;

/**
 * Versioned, complete value encoding for frozen rules. Decoding never re-runs an adapter, so an
 * in-flight session retains the precise rule it accepted even after plugin or recipe changes.
 */
public final class ReusableInputRuleNbtCodec {

    private static final int SCHEMA = 1;
    private static final Set<String> FIELDS = Set.of("schema", "id", "revision", "kind", "initial",
            "damage_per_use", "break_at_damage", "exhaustion_outputs", "transitions");

    private ReusableInputRuleNbtCodec() {}

    /** @return new mutable NBT tree containing every semantic value, including exact components */
    public static CompoundTag encode(ReusableInputRule rule, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("id", rule.id().toString());
        tag.putLong("revision", rule.revision());
        tag.putString("kind", rule.kind().name());
        tag.put("initial", rule.initialKey().toTagGeneric(registries));
        tag.putInt("damage_per_use", rule.damagePerUse());
        tag.putInt("break_at_damage", rule.breakAtDamage());
        tag.put("exhaustion_outputs", encodeOutputs(rule.exhaustionByproducts(), registries));
        ListTag transitions = new ListTag();
        for (Transition transition : rule.transitions()) {
            CompoundTag entry = new CompoundTag();
            entry.put("input", transition.input().toTagGeneric(registries));
            entry.putBoolean("exhausted", transition.successor() == null);
            if (transition.successor() != null) {
                entry.put("successor", transition.successor().toTagGeneric(registries));
            }
            entry.put("outputs", encodeOutputs(transition.byproducts(), registries));
            transitions.add(entry);
        }
        tag.put("transitions", transitions);
        return tag;
    }

    /**
     * @return fully validated immutable rule
     * @throws IllegalArgumentException on unknown schema, missing content, incomplete transitions or malformed data
     */
    public static ReusableInputRule decode(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.getAllKeys().equals(FIELDS)) {
            throw new IllegalArgumentException("Reusable rule NBT has unexpected or missing fields");
        }
        requireType(tag, "schema", Tag.TAG_INT);
        if (tag.getInt("schema") != SCHEMA) {
            throw new IllegalArgumentException("Unsupported reusable rule schema");
        }
        requireType(tag, "id", Tag.TAG_STRING);
        requireType(tag, "revision", Tag.TAG_LONG);
        requireType(tag, "kind", Tag.TAG_STRING);
        requireType(tag, "damage_per_use", Tag.TAG_INT);
        requireType(tag, "break_at_damage", Tag.TAG_INT);
        List<Transition> transitions = new ObjectArrayList<>();
        for (Tag encoded : compoundList(tag, "transitions")) {
            CompoundTag entry = (CompoundTag) encoded;
            requireType(entry, "exhausted", Tag.TAG_BYTE);
            boolean exhausted = entry.getBoolean("exhausted");
            Set<String> expected = exhausted ? Set.of("input", "exhausted", "outputs") :
                    Set.of("input", "exhausted", "outputs", "successor");
            if (!entry.getAllKeys().equals(expected)) {
                throw new IllegalArgumentException("Reusable transition has contradictory or missing fields");
            }
            transitions.add(new Transition(decodeItem(entry, "input", registries),
                    exhausted ? null : decodeItem(entry, "successor", registries),
                    decodeOutputs(entry, "outputs", registries)));
        }
        return new ReusableInputRule(ResourceLocation.parse(tag.getString("id")), tag.getLong("revision"),
                ReusableInputRule.Kind.valueOf(tag.getString("kind")), decodeItem(tag, "initial", registries),
                tag.getInt("damage_per_use"), tag.getInt("break_at_damage"),
                decodeOutputs(tag, "exhaustion_outputs", registries), transitions);
    }

    private static ListTag encodeOutputs(List<GenericStack> outputs, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        outputs.forEach(output -> result.add(GenericStack.writeTag(registries, output)));
        return result;
    }

    private static List<GenericStack> decodeOutputs(CompoundTag tag, String field, HolderLookup.Provider registries) {
        List<GenericStack> result = new ObjectArrayList<>();
        for (Tag encoded : compoundList(tag, field)) {
            GenericStack output = GenericStack.readTag(registries, (CompoundTag) encoded);
            if (output == null || output.amount() <= 0L) {
                throw new IllegalArgumentException("Reusable rule contains an invalid byproduct");
            }
            result.add(output);
        }
        return result;
    }

    private static AEItemKey decodeItem(CompoundTag tag, String field, HolderLookup.Provider registries) {
        requireType(tag, field, Tag.TAG_COMPOUND);
        if (!(AEKey.fromTagGeneric(registries, tag.getCompound(field)) instanceof AEItemKey item)) {
            throw new IllegalArgumentException("Reusable rule contains an unknown item key: " + field);
        }
        return item;
    }

    private static ListTag compoundList(CompoundTag tag, String field) {
        requireType(tag, field, Tag.TAG_LIST);
        ListTag list = (ListTag) tag.get(field);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Reusable rule list must contain compounds: " + field);
        }
        return list;
    }

    private static void requireType(CompoundTag tag, String field, int type) {
        if (!tag.contains(field, type)) {
            throw new IllegalArgumentException("Reusable rule field has the wrong type: " + field);
        }
    }
}
