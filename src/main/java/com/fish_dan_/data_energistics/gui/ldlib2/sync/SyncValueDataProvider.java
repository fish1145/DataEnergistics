package com.fish_dan_.data_energistics.gui.ldlib2.sync;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;

import java.util.function.Consumer;

/**
 * Exposes one LDLib2 synchronized value through the standard component data-binding contract.
 */
public record SyncValueDataProvider<T>(SyncValue<T> syncValue) implements IDataProvider<T> {

    public SyncValueDataProvider {
        if (syncValue == null) {
            throw new NullPointerException("Sync value must not be null");
        }
    }

    @Override
    public ISubscription registerListener(Consumer<T> listener) {
        return this.syncValue.addListener(listener);
    }

    @Override
    public T getValue() {
        return this.syncValue.getValue();
    }
}
