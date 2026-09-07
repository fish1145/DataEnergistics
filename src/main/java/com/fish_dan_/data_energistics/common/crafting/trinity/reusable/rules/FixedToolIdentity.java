package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;

import appeng.api.stacks.AEItemKey;

import net.minecraft.core.component.DataComponents;

/** Exact tool identity with only the explicitly declared fixed-wear coordinate removed. */
public final class FixedToolIdentity {

    private FixedToolIdentity() {}

    @SuppressWarnings("DataFlowIssue") // Copying a non-empty AEItemKey and changing Damage cannot empty its stack.
    public static AEItemKey key(AEItemKey state) {
        if (!state.isDamaged()) return state;
        var stack = state.toStack();
        stack.set(DataComponents.DAMAGE, 0);
        return AEItemKey.of(stack);
    }

    public static ReusableInputRule rule(ReusableInputRule source) {
        return ReusableInputRule.fixedDamage(source.id(), source.revision(), key(source.initialKey()),
                source.damagePerUse(), source.breakAtDamage(), source.exhaustionByproducts());
    }

    public static boolean matches(ReusableInputRule rule, AEItemKey state) {
        int damage = state.getReadOnlyStack().getDamageValue();
        return damage >= 0 && damage < rule.breakAtDamage() && rule.initialKey().getItem() == state.getItem() &&
                key(rule.initialKey()).equals(key(state));
    }
}
