package com.fish_dan_.data_energistics.gui.ldlib2.host.window;

import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;

/**
 * Host-owned lifecycle and window actions exposed while a provider builds one fresh child UI.
 */
public interface HostSubUiContext {

    /**
     * Returns the stable identity being opened.
     *
     * @return current child UI identity
     */
    HostUiKey key();

    /**
     * Returns the accepted OPEN sequence that uniquely identifies this fresh instance.
     *
     * <p>
     * Window-specific custom payloads must carry this generation and validate it through
     * {@link HostUiExtension#isOpen(HostUiKey, long)} before executing business actions.
     * </p>
     *
     * @return positive fresh-window generation
     */
    long generation();

    /**
     * Creates and tracks the sole resource-owning root for this provider invocation.
     *
     * <p>
     * Providers must call this before constructing {@code Scene} or other resource-owning children. If creation
     * fails before mount, the context releases the complete root tree; after mount, normal LDLib2 removal owns it.
     * </p>
     *
     * @return fresh root that must be returned through {@link HostSubUi}
     */
    HostSubUiRoot createRoot();

    /**
     * Registers cleanup for resources outside the element tree before that tree becomes owned by LDLib2.
     *
     * <p>
     * The host runs this action when creation, configuration, or mounting fails before the complete open operation
     * commits. It disarms the action only after all host state has been published successfully. {@link #createRoot()}
     * already covers {@code Scene}, visual layer, and other element-owned resources; they must not also be registered
     * here.
     * </p>
     *
     * @param rollbackAction cleanup needed only while the new tree remains unattached
     */
    void onCreationRollback(Runnable rollbackAction);

    /**
     * Registers non-element cleanup that must run exactly once when creation fails or the window closes.
     *
     * <p>
     * LDLib2 {@code Scene} elements release themselves through {@code removeChild}; they must not be registered
     * here for a second manual release. Use {@link #onCreationRollback(Runnable)} for the pre-mount failure window.
     * </p>
     *
     * @param closeAction cleanup for subscriptions or resources outside the element tree
     */
    void onClose(Runnable closeAction);

    /**
     * Requests normal removal of this child UI from its host.
     *
     * @return whether an attached child UI was closed
     */
    boolean requestClose();

    /**
     * Requests promotion of this child UI above the other hosted windows.
     *
     * @return whether the child UI is currently attached
     */
    boolean requestFront();

    /**
     * Reports whether a dynamic control may emit a custom C2S action for this still-current generation.
     *
     * <p>
     * The host also capture-blocks LDLib2 interaction events while lifecycle work is pending. Custom payload
     * buttons must additionally check this method before sending because they do not use LDLib2's RPC dispatcher.
     * </p>
     *
     * @return whether no lifecycle request is pending and this exact instance remains attached
     */
    boolean canSendServerAction();
}
