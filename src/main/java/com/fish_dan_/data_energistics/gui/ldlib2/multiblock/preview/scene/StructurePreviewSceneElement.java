package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import org.jspecify.annotations.Nullable;

/**
 * Physical-side-neutral host for a structure preview scene.
 *
 * <p>
 * The common tree always contains this plain element. A client binder may attach one id-less internal child that
 * owns the actual LDLib2 {@code Scene}; the dedicated server therefore never loads or removes that client class.
 * </p>
 */
public final class StructurePreviewSceneElement extends UIElement {

    @Nullable
    private UIElement clientScene;

    public StructurePreviewSceneElement() {
        setOverflowVisible(false);
    }

    /**
     * Attaches the client-only visual scene without adding an addressable sync or RPC peer to the common tree.
     *
     * @param clientScene id-less internal element created by the physical-client binder
     */
    public void attachClientScene(UIElement clientScene) {
        if (clientScene == null) {
            throw new IllegalArgumentException("Structure preview client scene cannot be null");
        }
        if (this.clientScene != null) {
            throw new IllegalStateException("Structure preview shell already owns a client scene");
        }
        if (clientScene.hasParent()) {
            throw new IllegalArgumentException("Structure preview client scene must not already have a parent");
        }
        if (!clientScene.isInternalUI() || !clientScene.getId().isEmpty()) {
            throw new IllegalArgumentException("Structure preview client scene must be internal and id-less");
        }
        this.clientScene = clientScene;
        try {
            addChild(clientScene);
        } catch (RuntimeException | Error failure) {
            if (!hasChild(clientScene) && clientScene.getParent() != this) {
                this.clientScene = null;
            }
            throw failure;
        }
    }

    /**
     * Removes the exact client-only visual scene and triggers its normal client renderer lifecycle.
     *
     * @param clientScene exact element returned to this shell by the physical-client binder
     */
    public void detachClientScene(UIElement clientScene) {
        if (clientScene == null || this.clientScene != clientScene) {
            throw new IllegalArgumentException("Structure preview shell does not own the supplied client scene");
        }
        if (!removeChild(clientScene)) {
            throw new IllegalStateException("Structure preview client scene could not be removed from its shell");
        }
    }

    /**
     * Clears ownership only after complete detachment. LDLib2 may remove a child from its list before a lifecycle
     * listener fails, while leaving the child's parent or ModularUI behind; retaining ownership then prevents reuse
     * of that structurally incomplete shell.
     */
    @Override
    public boolean removeChild(@Nullable UIElement child) {
        boolean removingClientScene = child != null && this.clientScene == child;
        boolean removed = false;
        try {
            removed = super.removeChild(child);
            return removed;
        } finally {
            if (removingClientScene &&
                    (removed || (!hasChild(child) && child.getParent() != this))) {
                this.clientScene = null;
            }
        }
    }
}
