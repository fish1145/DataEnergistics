package com.fish_dan_.data_energistics.gui.ldlib2;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Resource-owning root whose guarded removal lets LDLib2 finish structural detachment before failures propagate.
 *
 * <p>
 * Providers obtain this root from {@link HostSubUiContext#createRoot()} before creating resource-owning children.
 * The host can therefore release the complete detached tree when provider creation fails, while normal close still
 * follows LDLib2's recursive {@code removeChild} lifecycle exactly once.
 * </p>
 */
public final class HostSubUiRoot extends UIElement {

    @Nullable
    private Runnable removalCallback;
    @Nullable
    private Consumer<Throwable> detachmentFailureCallback;
    @Nullable
    private Throwable removalFailure;
    private boolean removed;
    private int selfRemovalDepth;

    HostSubUiRoot() {}

    /** Installs host bookkeeping callbacks before this root enters the overlay tree. */
    void setRemovalCallbacks(Runnable removalCallback, Consumer<Throwable> detachmentFailureCallback) {
        if (removalCallback == null) {
            throw new IllegalArgumentException("Host sub UI removal callback must not be null");
        }
        if (detachmentFailureCallback == null) {
            throw new IllegalArgumentException("Host sub UI detachment failure callback must not be null");
        }
        if (this.removed || this.removalCallback != null || this.detachmentFailureCallback != null) {
            throw new IllegalStateException("Host sub UI removal callbacks can only be installed once");
        }
        this.removalCallback = removalCallback;
        this.detachmentFailureCallback = detachmentFailureCallback;
    }

    /** Releases a tree that never transferred to LDLib2 ownership. */
    void disposeUnattached() {
        if (hasParent() || getModularUI() != null) {
            throw new IllegalStateException("Cannot release a HostSubUiRoot owned by another UI tree");
        }
        onRemoved();
        rethrow(this.removalFailure);
    }

    /** Returns a deferred callback failure after LDLib2 has completed structural removal. */
    @Nullable
    Throwable removalFailure() {
        return this.removalFailure;
    }

    /** Propagates deferred removal failures only after the parent has completed structural detachment. */
    @Override
    public boolean removeSelf() {
        boolean removedFromParent;
        this.selfRemovalDepth++;
        try {
            removedFromParent = super.removeSelf();
        } catch (RuntimeException | Error exception) {
            Throwable failure = mergeFailures(this.removalFailure, exception);
            notifyDetachmentFailure(failure);
            rethrow(failure);
            return false;
        } finally {
            this.selfRemovalDepth--;
            if (this.selfRemovalDepth == 0) {
                this.detachmentFailureCallback = null;
            }
        }
        if (removedFromParent) {
            rethrow(this.removalFailure);
        }
        return removedFromParent;
    }

    /** Captures callback failures so UIElement.removeChild can still clear ModularUI, parent, and caches. */
    @Override
    protected void onRemoved() {
        if (this.removed) {
            return;
        }
        this.removed = true;
        Throwable failure = null;
        for (UIElement child : getSafeChildren()) {
            failure = releaseTree(this, child, failure);
        }
        try {
            super.onRemoved();
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to release an LDLib2 host child element tree", exception);
            failure = mergeFailures(failure, exception);
        }
        this.removalFailure = failure;
        if (this.removalCallback != null) {
            try {
                this.removalCallback.run();
            } catch (RuntimeException | Error exception) {
                Data_Energistics.LOGGER.error("Failed to reconcile a removed LDLib2 host child UI", exception);
                failure = mergeFailures(failure, exception);
            }
            this.removalCallback = null;
        }
        if (this.selfRemovalDepth == 0) {
            this.detachmentFailureCallback = null;
        }
        this.removalFailure = failure;
    }

    /** Makes an externally initiated, structurally incomplete detach terminal before preserving its root failure. */
    private void notifyDetachmentFailure(Throwable failure) {
        if (this.detachmentFailureCallback == null) {
            return;
        }
        try {
            this.detachmentFailureCallback.accept(failure);
        } catch (RuntimeException | Error callbackFailure) {
            Data_Energistics.LOGGER.error("Failed to report an incomplete LDLib2 host child detachment", callbackFailure);
            if (failure != callbackFailure) {
                failure.addSuppressed(callbackFailure);
            }
        }
    }

    /** Releases descendants in post-order so one failing sibling cannot strand later resource-owning elements. */
    private static @Nullable Throwable releaseTree(UIElement parent,
                                                   UIElement element,
                                                   @Nullable Throwable failure) {
        for (UIElement child : element.getSafeChildren()) {
            failure = releaseTree(element, child, failure);
        }
        try {
            if (!parent.removeChild(element)) {
                throw new IllegalStateException("LDLib2 child disappeared during guarded tree removal");
            }
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to release an LDLib2 host child tree element", exception);
            failure = mergeFailures(failure, exception);
        }
        return failure;
    }

    /** Preserves the first removal failure and appends later callback failures as context. */
    private static Throwable mergeFailures(@Nullable Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    /** Rethrows only unchecked failures captured by {@link #onRemoved()}. */
    private static void rethrow(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
