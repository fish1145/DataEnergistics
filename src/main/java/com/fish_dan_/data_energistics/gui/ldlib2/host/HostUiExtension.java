package com.fish_dan_.data_energistics.gui.ldlib2.host;

import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Owns independently draggable, non-modal child UIs for one LDLib2 host element tree.
 *
 * <p>
 * The extension creates a zero-size overlay layer so XEI exclusion handling sees only attached windows, not a
 * full-screen mounting rectangle.
 * </p>
 */
public interface HostUiExtension {

    /**
     * Stable identity of the root stacking layer that owns every hosted window.
     */
    String HOSTED_OVERLAY_ID = "datae-host-ui-overlay";

    /**
     * Root z-index above static host controls while remaining below transient popup content.
     */
    int HOSTED_OVERLAY_Z = 1;

    /**
     * Marker applied to root-mounted transient popup elements that consume Escape before a hosted window.
     */
    String TRANSIENT_POPUP_CLASS = "data_energistics_host_transient_popup";

    /**
     * Root z-index reserved for transient popup content above every hosted window.
     */
    int TRANSIENT_POPUP_Z = 1000;

    /**
     * Creates one extension and attaches its private overlay layer to the supplied host root.
     *
     * @param hostRoot root that owns the child UI lifetime
     * @return new empty extension
     */
    static HostUiExtension create(UIElement hostRoot) {
        return OverlayHostUiExtension.create(hostRoot);
    }

    /**
     * Releases an extension when its owning factory fails before creating a {@link HostModularUI}.
     *
     * <p>
     * This rollback entry rejects mounted extensions, so callers cannot bypass the coordinator for a live menu.
     * </p>
     *
     * @param hostUi unmounted extension whose construction failed
     */
    static void discardUnmounted(HostUiExtension hostUi) {
        OverlayHostUiExtension.discardUnmounted(hostUi);
    }

    /**
     * Creates the sole ModularUI bound to this extension's exact host root.
     *
     * @param ui     UI whose root must be the element supplied to {@link #create(UIElement)}
     * @param player owning player when the UI is mounted on a menu
     * @return ModularUI with hosted-window input and removal behavior
     */
    HostModularUI createModularUI(UI ui, @Nullable Player player);

    /**
     * Registers one provider identity before the coordinator seals the deterministic provider order.
     *
     * @param provider provider to register
     */
    void register(HostSubUiProvider provider);

    /**
     * Returns provider identities in their deterministic registration order.
     *
     * @return immutable registration-order snapshot
     */
    List<HostUiKey> registeredKeys();

    /**
     * Requests an authoritative open without changing client membership before the server response.
     *
     * @param key registered child UI identity
     * @return whether a request was emitted
     */
    boolean requestOpen(HostUiKey key);

    /**
     * Requests the explicit operation implied by the client's acknowledged membership.
     *
     * @param key registered child UI identity
     * @return whether a request was emitted
     */
    boolean requestToggle(HostUiKey key);

    /**
     * Requests an authoritative close without removing the client tree before the server response.
     *
     * @param key child UI identity
     * @return whether a request was emitted
     */
    boolean requestClose(HostUiKey key);

    /**
     * Requests authoritative closure of the most recently promoted window.
     *
     * @return whether a request was emitted
     */
    boolean requestCloseTopmost();

    /**
     * Promotes one attached window without detaching or recreating its element tree.
     *
     * @param key child UI identity
     * @return whether the identity is currently attached
     */
    boolean bringToFront(HostUiKey key);

    /**
     * Routes Screen keyboard input before AE2 or vanilla can close the host.
     *
     * @param keyCode   GLFW key code
     * @param scanCode  platform scan code
     * @param modifiers active modifier mask
     * @return whether Escape closed a transient popup or emitted a non-optimistic window close request
     */
    boolean handleKeyPressed(int keyCode, int scanCode, int modifiers);

    /**
     * Returns whether one identity currently owns an attached instance.
     *
     * @param key child UI identity
     * @return current open state
     */
    boolean isOpen(HostUiKey key);

    /**
     * Reports whether one fresh window generation is still the currently attached instance.
     *
     * @param key        registered child UI identity
     * @param generation accepted OPEN sequence carried by a window-specific business request
     * @return whether the exact generation remains open
     */
    boolean isOpen(HostUiKey key, long generation);

    /**
     * Returns an immutable bottom-to-top identity snapshot.
     *
     * @return current window order
     */
    List<HostUiKey> openKeys();

    /**
     * Returns whether this host lifetime has ended.
     *
     * @return terminal lifecycle state
     */
    boolean isDisposed();
}
