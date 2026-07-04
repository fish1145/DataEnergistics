package com.fish_dan_.data_energistics.common.compartment;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

/**
 * ME storage facade for a bound ME output compartment buffer.
 */
public final class CompartmentOutputStorage implements MEStorage {

    private final CompartmentPart part;
    private final CompartmentStorage storage;
    private final Component description;

    public CompartmentOutputStorage(CompartmentPart part, CompartmentStorage storage, Component description) {
        this.part = part;
        this.storage = storage;
        this.description = description;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!isAvailable()) {
            return 0L;
        }
        return this.storage.insert(what, amount, mode == Actionable.SIMULATE);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!isAvailable()) {
            return 0L;
        }
        return this.storage.extract(what, amount, mode == Actionable.SIMULATE);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!isAvailable()) {
            return;
        }
        for (var entry : this.storage.entries().object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0L) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    @Override
    public Component getDescription() {
        return this.description;
    }

    private boolean isAvailable() {
        return this.part.compartmentType() == CompartmentType.ME_OUTPUT && this.part.isCompartmentBound();
    }
}
