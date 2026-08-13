package com.fish_dan_.data_energistics.gui.ldlib2.host.window;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;

import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal host lifecycle, ordering, drag, viewport, and cleanup implementation.
 */
final class OverlayHostUiExtension implements HostUiExtension {

    private static final String FAILURE_PREFIX = "LDLib2 host UI invariant failed: ";
    private static final int ESCAPE_KEY = 256;
    private static final int BASE_WINDOW_Z = 300;
    private static final List<String> GUARDED_INTERACTION_EVENTS = List.of(
            UIEvents.MOUSE_DOWN,
            UIEvents.MOUSE_UP,
            UIEvents.CLICK,
            UIEvents.DOUBLE_CLICK,
            UIEvents.MOUSE_MOVE,
            UIEvents.MOUSE_ENTER,
            UIEvents.MOUSE_LEAVE,
            UIEvents.MOUSE_WHEEL,
            UIEvents.DRAG_ENTER,
            UIEvents.DRAG_LEAVE,
            UIEvents.DRAG_UPDATE,
            UIEvents.DRAG_SOURCE_UPDATE,
            UIEvents.DRAG_PERFORM,
            UIEvents.DRAG_END,
            UIEvents.FOCUS,
            UIEvents.BLUR,
            UIEvents.FOCUS_IN,
            UIEvents.FOCUS_OUT,
            UIEvents.KEY_DOWN,
            UIEvents.KEY_UP,
            UIEvents.CHAR_TYPED,
            UIEvents.VALIDATE_COMMAND,
            UIEvents.EXECUTE_COMMAND);

    private final UIElement hostRoot;
    private final HostOverlayLayer overlayLayer;
    private final Map<HostUiKey, HostSubUiProvider> providers = new LinkedHashMap<>();
    private final Map<HostUiKey, WindowEntry> openWindows = new LinkedHashMap<>();
    private final List<WindowEntry> bottomToTop = new ArrayList<>();
    private final Map<HostUiKey, WindowPosition> savedPositions = new LinkedHashMap<>();
    private final ReferenceQueue<UIElement> collectedElements = new ReferenceQueue<>();
    private final Set<WeakElementReference> seenElements = new HashSet<>();
    @Nullable
    private HostUiCoordinator coordinator;
    private ModularUI escapePolicyUi;
    private HostModularUI modularUI;
    private boolean previousShouldCloseOnEsc;
    private boolean providersSealed;
    private boolean disposed;
    private boolean overlayRemoved;

    private OverlayHostUiExtension(UIElement hostRoot) {
        this.hostRoot = hostRoot;
        this.overlayLayer = createOverlayLayer();
        this.overlayLayer.addEventListener(UIEvents.MUI_CHANGED, event -> synchronizeEscapePolicy());
        this.overlayLayer.addEventListener(UIEvents.REMOVED, event -> handleOverlayRemoval());
        Style.importantPipeline(this.hostRoot.getStyle(), style -> style.overflowVisible(true));
        this.hostRoot.addChild(this.overlayLayer);
    }

    /**
     * Validates the host root before constructing its private overlay.
     */
    static HostUiExtension create(UIElement hostRoot) {
        if (hostRoot == null) {
            throw violation("host root must not be null");
        }
        if (hostRoot.hasParent()) {
            throw violation("host root must not already have a parent");
        }
        if (hostRoot.getModularUI() != null) {
            throw violation("host root must not already belong to a ModularUI");
        }
        for (UIElement child : hostRoot.getChildren()) {
            if (child instanceof HostOverlayLayer) {
                throw violation("host root already owns a host UI extension");
            }
        }
        return new OverlayHostUiExtension(hostRoot);
    }

    /**
     * Releases only a factory result that never reached its mounted ModularUI lifetime.
     */
    static void discardUnmounted(HostUiExtension hostUi) {
        if (!(hostUi instanceof OverlayHostUiExtension implementation)) {
            throw violation("unmounted rollback requires the host extension created by HostUiExtension.create");
        }
        if (implementation.modularUI != null) {
            throw violation("a mounted host extension can only be released by its owning ModularUI");
        }
        implementation.disposeFromOwner();
    }

    @Override
    public HostModularUI createModularUI(UI ui, @Nullable Player player) {
        ensureUsable();
        if (ui == null) {
            throw violation("UI must not be null");
        }
        if (ui.rootElement != this.hostRoot) {
            throw violation("ModularUI root does not match the host extension root");
        }
        if (this.modularUI != null) {
            throw violation("host extension already owns a ModularUI");
        }
        this.modularUI = new HostModularUI(ui, player, this);
        return this.modularUI;
    }

    /**
     * Seals provider registration and attaches the sole coordinator before dynamic membership begins.
     */
    void attachCoordinator(HostUiCoordinator coordinator) {
        ensureUsable();
        if (coordinator == null) {
            throw violation("coordinator must not be null");
        }
        if (coordinator.hostUi() != this) {
            throw violation("coordinator belongs to a different host extension");
        }
        if (this.coordinator != null) {
            throw violation("host extension already owns a coordinator");
        }
        if (!this.openWindows.isEmpty()) {
            throw violation("coordinator must attach before the first dynamic window opens");
        }
        this.providersSealed = true;
        this.coordinator = coordinator;
        synchronizeEscapePolicy();
    }

    @Override
    public void register(HostSubUiProvider provider) {
        ensureUsable();
        if (this.providersSealed) {
            throw violation("provider registration is sealed after coordinator attachment");
        }
        if (provider == null) {
            throw violation("provider must not be null");
        }
        HostUiKey key = provider.key();
        if (key == null) {
            throw violation("provider key must not be null");
        }
        if (this.providers.putIfAbsent(key, provider) != null) {
            throw violation("duplicate provider key " + key.id());
        }
    }

    @Override
    public List<HostUiKey> registeredKeys() {
        return List.copyOf(this.providers.keySet());
    }

    @Override
    public boolean requestOpen(HostUiKey key) {
        return requiredCoordinator().requestOpen(key);
    }

    @Override
    public boolean requestToggle(HostUiKey key) {
        return requiredCoordinator().requestToggle(key);
    }

    @Override
    public boolean requestClose(HostUiKey key) {
        return requiredCoordinator().requestClose(key);
    }

    @Override
    public boolean requestCloseTopmost() {
        return requiredCoordinator().requestCloseTopmost();
    }

    /**
     * Applies one accepted OPEN sequence and records it as the fresh window generation.
     */
    boolean openFresh(HostUiKey key, long generation) {
        ensureUsable();
        if (generation <= 0L) {
            throw violation("window generation must be positive: " + generation);
        }
        HostSubUiProvider provider = registeredProvider(key);
        if (this.openWindows.containsKey(key)) {
            bringToFront(key);
            return false;
        }

        OpeningContext context = new OpeningContext(key, generation);
        WindowEntry entry = null;
        try {
            HostSubUi subUi = provider.create(context);
            validateFreshSubUi(key, subUi, context);
            entry = new WindowEntry(key, subUi, context);
            configureWindow(entry);
            context.transferElementOwnership();
            this.overlayLayer.addChild(subUi.root());
            if (subUi.root().getParent() != this.overlayLayer || !this.overlayLayer.hasChild(subUi.root())) {
                throw violation("provider " + key.id() + " detached its root while mounting");
            }
            this.openWindows.put(key, entry);
            this.bottomToTop.add(entry);
            context.markAttached();
            applyWindowOrder();
            clampAndRemember(entry);
            synchronizeEscapePolicy();
            context.commitCreation();
            return true;
        } catch (RuntimeException | Error failure) {
            rollbackFailedOpen(key, entry, context, failure);
            Data_Energistics.LOGGER.error("Failed to open LDLib2 host child UI {}", key.id(), failure);
            throw failure;
        }
    }

    /**
     * Applies one accepted CLOSE sequence through LDLib2's real element removal lifecycle.
     */
    boolean closeAuthoritatively(HostUiKey key) {
        if (this.disposed) {
            return false;
        }
        return closeWindow(key);
    }

    @Override
    public boolean bringToFront(HostUiKey key) {
        if (this.disposed) {
            return false;
        }
        WindowEntry entry = this.openWindows.get(key);
        if (entry == null) {
            return false;
        }
        if (this.bottomToTop.getLast() != entry) {
            this.bottomToTop.remove(entry);
            this.bottomToTop.add(entry);
            applyWindowOrder();
        }
        return true;
    }

    @Override
    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.disposed || keyCode != ESCAPE_KEY) {
            return false;
        }
        if (closeTopmostTransientPopup()) {
            return true;
        }
        return requiredCoordinator().handleEscape();
    }

    /**
     * Removes the most recently mounted root popup before Escape reaches hosted-window membership.
     */
    private boolean closeTopmostTransientPopup() {
        List<UIElement> rootChildren = List.copyOf(this.hostRoot.getChildren());
        for (int index = rootChildren.size() - 1; index >= 0; index--) {
            UIElement popup = rootChildren.get(index);
            if (!popup.hasClass(TRANSIENT_POPUP_CLASS)) {
                continue;
            }
            ModularUI popupUi = popup.getModularUI();
            if (!this.hostRoot.removeChild(popup)) {
                throw violation("transient popup disappeared before Escape removal");
            }
            if (popupUi != null && Data_Energistics.isClientSide()) {
                popupUi.clearFocus();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isOpen(HostUiKey key) {
        return !this.disposed && key != null && this.openWindows.containsKey(key);
    }

    @Override
    public boolean isOpen(HostUiKey key, long generation) {
        WindowEntry entry = key == null ? null : this.openWindows.get(key);
        return !this.disposed && generation > 0L && entry != null && entry.context.generation() == generation;
    }

    @Override
    public List<HostUiKey> openKeys() {
        if (this.disposed) {
            return List.of();
        }
        return this.bottomToTop.stream().map(entry -> entry.key).toList();
    }

    /**
     * Removes every authoritative local instance in reverse z-order during host teardown.
     */
    private void closeAllAuthoritatively() {
        List<WindowEntry> windows = List.copyOf(this.bottomToTop);
        Throwable failure = null;
        for (int index = windows.size() - 1; index >= 0; index--) {
            try {
                closeWindow(windows.get(index).key);
            } catch (RuntimeException | Error exception) {
                failure = mergeFailures(failure, exception);
            }
        }
        rethrow(failure);
    }

    /**
     * Permanently releases every child tree when the owning ModularUI ends or an unmounted factory rolls back.
     */
    void disposeFromOwner() {
        if (this.overlayRemoved && this.bottomToTop.isEmpty()) {
            return;
        }
        this.disposed = true;
        Throwable failure = null;
        try {
            closeAllAuthoritatively();
        } catch (RuntimeException | Error exception) {
            failure = exception;
        }
        restoreEscapePolicy();
        try {
            if (this.hostRoot.hasChild(this.overlayLayer)) {
                if (!this.hostRoot.removeChild(this.overlayLayer)) {
                    throw violation("host overlay layer could not be removed");
                }
            } else if (!this.overlayRemoved) {
                throw violation("host overlay layer is missing without completing removal");
            }
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to remove the LDLib2 host overlay layer", exception);
            failure = mergeFailures(failure, exception);
        }
        rethrow(failure);
    }

    @Override
    public boolean isDisposed() {
        return this.disposed;
    }

    /**
     * Creates a zero-size, overflow-visible layer so only its window children contribute XEI areas.
     */
    private HostOverlayLayer createOverlayLayer() {
        HostOverlayLayer layer = new HostOverlayLayer();
        layer.setId(HOSTED_OVERLAY_ID);
        layer.setAllowHitTest(false);
        for (String eventType : GUARDED_INTERACTION_EVENTS) {
            layer.addEventListener(eventType, event -> {
                if (isInteractionBlocked()) {
                    event.stopPropagation();
                }
            }, true);
        }
        layer.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(0);
            layout.height(0);
        });
        Style.importantPipeline(layer.getStyle(), style -> style
                .overflowVisible(true)
                .zIndex(HOSTED_OVERLAY_Z));
        return layer;
    }

    /**
     * Finds a provider or rejects an unknown identity before any UI mutation.
     */
    private HostSubUiProvider registeredProvider(HostUiKey key) {
        if (key == null) {
            throw violation("child UI key must not be null");
        }
        HostSubUiProvider provider = this.providers.get(key);
        if (provider == null) {
            throw violation("unregistered child UI key " + key.id());
        }
        return provider;
    }

    /**
     * Rejects element reuse before LDLib2 can detach it from another tree and release its resources.
     */
    private void validateFreshSubUi(HostUiKey key, HostSubUi subUi, OpeningContext context) {
        if (subUi == null) {
            throw violation("provider " + key.id() + " returned null");
        }
        if (subUi.root() != context.createdRoot()) {
            throw violation("provider " + key.id() + " did not return the root created by its context");
        }
        if (subUi.root() == this.hostRoot) {
            throw violation("provider " + key.id() + " reused the host root");
        }
        List<UIElement> newElements = new ArrayList<>();
        newElements.add(subUi.root());
        newElements.addAll(subUi.root().getFlattenChildren());
        purgeCollectedElements();
        for (UIElement element : newElements) {
            if (this.seenElements.contains(new WeakElementReference(element))) {
                throw violation("provider " + key.id() + " reused an element from an earlier opening");
            }
        }
        for (UIElement element : newElements) {
            this.seenElements.add(new WeakElementReference(element, this.collectedElements));
        }
        for (UIElement element : newElements) {
            if (element.getModularUI() != null) {
                throw violation("provider " + key.id() + " returned an element already bound to a ModularUI");
            }
        }
        if (subUi.root().hasParent()) {
            throw violation("provider " + key.id() + " returned a root that already has a parent");
        }
    }

    /**
     * Removes identity records as soon as closed trees become otherwise unreachable.
     */
    private void purgeCollectedElements() {
        WeakElementReference reference;
        while ((reference = (WeakElementReference) this.collectedElements.poll()) != null) {
            this.seenElements.remove(reference);
        }
    }

    /**
     * Installs one instance's absolute positioning, promotion, drag, clamp, and removal hooks.
     */
    private void configureWindow(WindowEntry entry) {
        HostSubUiRoot root = entry.subUi.root();
        root.setFocusable(true);
        root.stopInteractionEventsPropagation();
        root.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE));
        WindowPosition savedPosition = this.savedPositions.get(entry.key);
        if (savedPosition != null) {
            root.layout(layout -> layout.left(savedPosition.left).top(savedPosition.top));
        }
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0) {
                bringToFront(entry.key);
            }
        }, true);
        root.addEventListener(UIEvents.LAYOUT_CHANGED, event -> clampAndRemember(entry));
        root.setRemovalCallbacks(
                () -> handleWindowRemoval(entry),
                failure -> terminateAfterRemovalFailure(entry, failure),
                () -> handleCompletedSelfRemoval(entry));
        WindowDragHelper.setDragMove(
                entry.subUi.dragSurface(),
                root,
                event -> event.button == 0 && event.target == root,
                event -> clampAndRemember(entry));
    }

    /**
     * Blocks every descendant interaction while its matching server-side dynamic RPC tree may be absent.
     */
    private boolean isInteractionBlocked() {
        return this.coordinator != null &&
                (this.coordinator.pendingRequest() != null || this.coordinator.isTerminal());
    }

    /**
     * Closes the owner only after an uncoordinated removeSelf has fully cleared LDLib2 ownership.
     */
    private void handleCompletedSelfRemoval(WindowEntry entry) {
        if (entry.removalRequestedByHost || this.coordinator == null) {
            return;
        }
        if (this.coordinator instanceof SequencedHostUiCoordinator coordinatorImpl) {
            coordinatorImpl.hostBecameTerminal(
                    "LDLib2 host UI became terminal after external removal of " + entry.key.id(),
                    entry.subUi.root().removalFailure());
        }
    }

    /**
     * Removes one window through LDLib2 before reporting any deferred focus or cleanup failure.
     */
    private boolean closeWindow(HostUiKey key) {
        WindowEntry entry = this.openWindows.get(key);
        if (entry == null) {
            return false;
        }
        entry.removalRequestedByHost = true;
        rememberPosition(entry);
        Throwable failure = null;
        try {
            moveFocusAfterClose(entry);
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to move focus before closing LDLib2 host child UI {}", key.id(), exception);
            failure = exception;
        }
        try {
            if (!this.overlayLayer.removeChild(entry.subUi.root())) {
                throw violation("tracked child UI " + key.id() + " is missing from its overlay layer");
            }
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to remove LDLib2 host child UI {}", key.id(), exception);
            failure = mergeFailures(failure, exception);
        }
        failure = mergeFailures(failure, entry.subUi.root().removalFailure());
        failure = mergeFailures(failure, entry.context.releaseFailure());
        if (failure != null) {
            terminateAfterRemovalFailure(entry, failure);
            rethrow(failure);
        }
        return true;
    }

    /**
     * Prevents keyboard input from targeting a detached tree by focusing the next window or the host root.
     */
    private void moveFocusAfterClose(WindowEntry closingEntry) {
        ModularUI modularUI = closingEntry.subUi.root().getModularUI();
        if (modularUI == null || !closingEntry.subUi.root().isAncestorOf(modularUI.getFocusedElement())) {
            return;
        }
        UIElement focusTarget = this.hostRoot;
        for (int index = this.bottomToTop.size() - 1; index >= 0; index--) {
            WindowEntry candidate = this.bottomToTop.get(index);
            if (candidate != closingEntry) {
                focusTarget = candidate.subUi.root();
                break;
            }
        }
        modularUI.requestFocus(focusTarget);
    }

    /**
     * Reconciles host state when LDLib2 or an external owner removes a window directly.
     */
    private void handleWindowRemoval(WindowEntry entry) {
        boolean externalRemoval = !entry.removalRequestedByHost;
        Throwable failure = entry.subUi.root().removalFailure();
        try {
            moveFocusAfterClose(entry);
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to move focus after external removal of LDLib2 host child UI {}",
                    entry.key.id(),
                    exception);
            failure = mergeFailures(failure, exception);
        }
        if (this.openWindows.remove(entry.key, entry)) {
            this.bottomToTop.remove(entry);
            try {
                applyWindowOrder();
            } catch (RuntimeException | Error exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to reorder LDLib2 host child UIs after removing {}", entry.key.id(), exception);
                failure = mergeFailures(failure, exception);
            }
            try {
                synchronizeEscapePolicy();
            } catch (RuntimeException | Error exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to synchronize Escape after removing LDLib2 host child UI {}",
                        entry.key.id(),
                        exception);
                failure = mergeFailures(failure, exception);
            }
        }
        entry.context.release(failure);
        failure = mergeFailures(failure, entry.context.releaseFailure());
        if (externalRemoval) {
            this.disposed = true;
            restoreEscapePolicy();
            Data_Energistics.LOGGER.error(
                    "LDLib2 host UI became terminal after external removal of {}",
                    entry.key.id());
        }
        if (failure != null) {
            this.disposed = true;
            restoreEscapePolicy();
            Data_Energistics.LOGGER.error("LDLib2 host UI became terminal after removing {}", entry.key.id(), failure);
            rethrow(failure);
        }
    }

    /**
     * Makes a failed detach terminal after the guarded root has allowed LDLib2 to clear structural ownership.
     */
    private void terminateAfterRemovalFailure(WindowEntry entry, Throwable failure) {
        this.disposed = true;
        this.openWindows.remove(entry.key, entry);
        this.bottomToTop.remove(entry);
        entry.context.release(failure);
        restoreEscapePolicy();
        Data_Energistics.LOGGER.error("LDLib2 host UI became terminal while removing {}", entry.key.id(), failure);
        notifyCoordinatorAfterExternalRemoval(entry, failure);
    }

    /**
     * Closes the owner after an external detach, while authoritative failures remain ordered by their payload handler.
     */
    private void notifyCoordinatorAfterExternalRemoval(WindowEntry entry, @Nullable Throwable failure) {
        if (entry.removalRequestedByHost || this.coordinator == null) {
            return;
        }
        if (this.coordinator instanceof SequencedHostUiCoordinator coordinatorImpl) {
            coordinatorImpl.hostBecameTerminal(
                    "LDLib2 host UI became terminal after external removal of " + entry.key.id(),
                    failure);
        }
    }

    /**
     * Rolls back all state and resources acquired before a failed open returned to its caller.
     */
    private void rollbackFailedOpen(HostUiKey key,
                                    WindowEntry entry,
                                    OpeningContext context,
                                    Throwable failure) {
        if (entry != null) {
            this.openWindows.remove(key, entry);
            this.bottomToTop.remove(entry);
            if (this.overlayLayer.hasChild(entry.subUi.root())) {
                try {
                    entry.removalRequestedByHost = true;
                    this.overlayLayer.removeChild(entry.subUi.root());
                } catch (RuntimeException | Error cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                    this.disposed = true;
                    restoreEscapePolicy();
                    Data_Energistics.LOGGER.error(
                            "LDLib2 host UI became terminal while rolling back {}",
                            key.id(),
                            cleanupFailure);
                }
                Throwable removalFailure = entry.subUi.root().removalFailure();
                if (removalFailure != null && removalFailure != failure) {
                    failure.addSuppressed(removalFailure);
                    this.disposed = true;
                    restoreEscapePolicy();
                }
            }
        }
        context.release(failure);
        if (this.disposed) {
            restoreEscapePolicy();
            return;
        }
        try {
            applyWindowOrder();
            synchronizeEscapePolicy();
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
            this.disposed = true;
            restoreEscapePolicy();
            Data_Energistics.LOGGER.error("LDLib2 host UI became terminal after failed open rollback", cleanupFailure);
        }
    }

    /**
     * Reassigns bounded, consecutive z-index values without detaching any resource-owning tree.
     */
    private void applyWindowOrder() {
        for (int index = 0; index < this.bottomToTop.size(); index++) {
            WindowEntry entry = this.bottomToTop.get(index);
            int zIndex = BASE_WINDOW_Z + index;
            Style.importantPipeline(
                    entry.subUi.root().getStyle(),
                    style -> style.zIndex(zIndex));
        }
    }

    /**
     * Clamps after layout or drag completion, retaining only per-host screen position.
     */
    private void clampAndRemember(WindowEntry entry) {
        if (entry.clamping || this.openWindows.get(entry.key) != entry) {
            return;
        }
        ModularUI modularUI = entry.subUi.root().getModularUI();
        if (modularUI == null || modularUI.getScreenWidth() <= 0 || modularUI.getScreenHeight() <= 0) {
            return;
        }
        if (entry.subUi.root().getSizeWidth() <= 0 || entry.subUi.root().getSizeHeight() <= 0) {
            return;
        }
        entry.clamping = true;
        try {
            int screenWidth = modularUI.getScreenWidth();
            int screenHeight = modularUI.getScreenHeight();
            HostWindowPlacement placement = HostWindowPlacement.clamp(
                    screenWidth,
                    screenHeight,
                    entry.subUi.root().getPositionX(),
                    entry.subUi.root().getPositionY(),
                    entry.subUi.root().getLayoutX(),
                    entry.subUi.root().getLayoutY(),
                    entry.subUi.root().getSizeWidth(),
                    entry.subUi.root().getSizeHeight());
            HostWindowPlacement finalPlacement = placement;
            if (entry.viewportWidth != screenWidth || entry.viewportHeight != screenHeight) {
                entry.viewportWidth = screenWidth;
                entry.viewportHeight = screenHeight;
                entry.subUi.root().layout(layout -> layout
                        .maxWidth(finalPlacement.maximumWidth())
                        .maxHeight(finalPlacement.maximumHeight()));
            }
            if (finalPlacement.left() != entry.subUi.root().getLayoutX() ||
                    finalPlacement.top() != entry.subUi.root().getLayoutY()) {
                entry.subUi.root().layout(layout -> layout
                        .left(finalPlacement.left())
                        .top(finalPlacement.top()));
            }
            rememberPosition(entry);
        } finally {
            entry.clamping = false;
        }
    }

    /**
     * Saves a computed client position only after a real viewport has initialized.
     */
    private void rememberPosition(WindowEntry entry) {
        ModularUI modularUI = entry.subUi.root().getModularUI();
        if (modularUI == null || modularUI.getScreenWidth() <= 0 || modularUI.getScreenHeight() <= 0) {
            return;
        }
        this.savedPositions.put(
                entry.key,
                new WindowPosition(entry.subUi.root().getLayoutX(), entry.subUi.root().getLayoutY()));
    }

    /**
     * Suspends host-level Escape closing only while at least one child UI remains open.
     */
    private void synchronizeEscapePolicy() {
        boolean coordinatorTerminal = this.coordinator != null && this.coordinator.isTerminal();
        boolean requestPending = this.coordinator != null && this.coordinator.pendingRequest() != null;
        if (coordinatorTerminal || this.openWindows.isEmpty() && !requestPending) {
            restoreEscapePolicy();
            return;
        }
        ModularUI currentUi = this.overlayLayer.getModularUI();
        if (currentUi == this.escapePolicyUi) {
            return;
        }
        restoreEscapePolicy();
        if (currentUi != null) {
            this.escapePolicyUi = currentUi;
            this.previousShouldCloseOnEsc = currentUi.shouldCloseOnEsc();
            currentUi.shouldCloseOnEsc(false);
        }
    }

    /**
     * Restores the exact host policy observed before the first child UI opened.
     */
    private void restoreEscapePolicy() {
        if (this.escapePolicyUi != null) {
            this.escapePolicyUi.shouldCloseOnEsc(this.previousShouldCloseOnEsc);
            this.escapePolicyUi = null;
        }
    }

    /**
     * Makes external removal of the owned layer a terminal, exactly-once cleanup boundary.
     */
    private void handleOverlayRemoval() {
        if (this.overlayRemoved) {
            return;
        }
        this.overlayRemoved = true;
        this.disposed = true;
        for (WindowEntry entry : List.copyOf(this.bottomToTop)) {
            this.openWindows.remove(entry.key, entry);
            entry.context.release(null);
        }
        this.bottomToTop.clear();
        restoreEscapePolicy();
    }

    /**
     * Prevents registration or opening after the host tree has ended.
     */
    private void ensureUsable() {
        if (this.disposed) {
            throw violation("host UI extension is disposed");
        }
    }

    /**
     * Returns the static lifecycle endpoint required by every client-originated membership request.
     */
    private HostUiCoordinator requiredCoordinator() {
        if (this.coordinator == null) {
            throw violation("host UI coordinator is not attached");
        }
        return this.coordinator;
    }

    /**
     * Re-evaluates Escape and interaction policy whenever the client pending state changes.
     */
    void coordinatorStateChanged() {
        if (!this.overlayRemoved) {
            synchronizeEscapePolicy();
        }
    }

    /**
     * Logs each rejected invariant before returning its fail-fast exception.
     */
    private static IllegalStateException violation(String message) {
        Data_Energistics.LOGGER.error("{}{}", FAILURE_PREFIX, message);
        return new IllegalStateException(message);
    }

    /**
     * Preserves the earliest lifecycle failure and appends later cleanup failures as suppressed context.
     */
    private static @Nullable Throwable mergeFailures(@Nullable Throwable first, @Nullable Throwable next) {
        if (next == null) {
            return first;
        }
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    /**
     * Rethrows only unchecked failures collected from UI callbacks and cleanup actions.
     */
    private static void rethrow(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    /**
     * Per-window mutable host bookkeeping; provider UI state remains inside its fresh element tree.
     */
    private static final class WindowEntry {

        private final HostUiKey key;
        private final HostSubUi subUi;
        private final OpeningContext context;
        private boolean removalRequestedByHost;
        private boolean clamping;
        private int viewportWidth = -1;
        private int viewportHeight = -1;

        private WindowEntry(HostUiKey key, HostSubUi subUi, OpeningContext context) {
            this.key = key;
            this.subUi = subUi;
            this.context = context;
        }
    }

    /**
     * Private ancestor that blocks pending events before provider targets and observes every direct window detach.
     */
    private final class HostOverlayLayer extends UIElement {

        @Override
        public boolean removeChild(@Nullable UIElement child) {
            try {
                boolean removed = super.removeChild(child);
                if (removed && child instanceof HostSubUiRoot root) {
                    root.reportDetachmentComplete();
                }
                return removed;
            } catch (RuntimeException | Error failure) {
                if (child instanceof HostSubUiRoot root) {
                    root.reportDetachmentFailure(failure);
                }
                throw failure;
            }
        }
    }

    /**
     * Weak identity key that detects provider reuse without retaining closed Scene trees for the whole menu.
     */
    private static final class WeakElementReference extends WeakReference<UIElement> {

        private final int identityHash;

        private WeakElementReference(UIElement element) {
            super(element);
            this.identityHash = System.identityHashCode(element);
        }

        private WeakElementReference(UIElement element, ReferenceQueue<UIElement> queue) {
            super(element, queue);
            this.identityHash = System.identityHashCode(element);
        }

        @Override
        public int hashCode() {
            return this.identityHash;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof WeakElementReference other)) {
                return false;
            }
            UIElement element = get();
            return element != null && element == other.get();
        }
    }

    /**
     * Screen-local position retained across fresh instances of one provider identity.
     */
    private record WindowPosition(float left, float top) {}

    /**
     * Collects failure-safe cleanup before a provider instance becomes attached.
     */
    private final class OpeningContext implements HostSubUiContext {

        private final HostUiKey key;
        private final long generation;
        private final List<Runnable> creationRollbackActions = new ArrayList<>();
        private final List<Runnable> closeActions = new ArrayList<>();
        private HostSubUiRoot root;
        private Throwable releaseFailure;
        private boolean elementOwnershipTransferred;
        private boolean creationCommitted;
        private boolean attached;
        private boolean released;

        private OpeningContext(HostUiKey key, long generation) {
            this.key = key;
            this.generation = generation;
        }

        @Override
        public HostUiKey key() {
            return this.key;
        }

        @Override
        public long generation() {
            return this.generation;
        }

        @Override
        public HostSubUiRoot createRoot() {
            if (this.root != null || this.elementOwnershipTransferred || this.attached || this.released) {
                throw violation("provider " + this.key.id() + " can create exactly one root before attachment");
            }
            this.root = new HostSubUiRoot();
            return this.root;
        }

        @Override
        public void onCreationRollback(Runnable rollbackAction) {
            if (rollbackAction == null) {
                throw violation("creation rollback action for " + this.key.id() + " must not be null");
            }
            if (this.elementOwnershipTransferred || this.attached || this.released) {
                throw violation("cannot register creation rollback after transferring " + this.key.id());
            }
            this.creationRollbackActions.add(rollbackAction);
        }

        @Override
        public void onClose(Runnable closeAction) {
            if (closeAction == null) {
                throw violation("close action for " + this.key.id() + " must not be null");
            }
            if (this.attached || this.released) {
                throw violation("cannot register close action after attaching " + this.key.id());
            }
            this.closeActions.add(closeAction);
        }

        @Override
        public boolean requestClose() {
            ensureAttached();
            return requiredCoordinator().requestClose(this.key);
        }

        @Override
        public boolean requestFront() {
            ensureAttached();
            return OverlayHostUiExtension.this.bringToFront(this.key);
        }

        @Override
        public boolean canSendServerAction() {
            return this.attached && !this.released &&
                    OverlayHostUiExtension.this.isOpen(this.key, this.generation) &&
                    OverlayHostUiExtension.this.coordinator != null &&
                    OverlayHostUiExtension.this.coordinator.pendingRequest() == null &&
                    !OverlayHostUiExtension.this.coordinator.isTerminal();
        }

        /**
         * Marks the point after which callbacks may mutate the attached host entry.
         */
        private void markAttached() {
            this.attached = true;
        }

        /**
         * Disarms non-element creation rollback only after every host state mutation succeeds.
         */
        private void commitCreation() {
            this.creationCommitted = true;
            this.creationRollbackActions.clear();
        }

        /**
         * Returns the context-owned root used to reject untracked or reused provider trees.
         */
        private @Nullable HostSubUiRoot createdRoot() {
            return this.root;
        }

        /**
         * Marks the point where LDLib2 removal replaces detached-tree rollback.
         */
        private void transferElementOwnership() {
            if (this.elementOwnershipTransferred) {
                throw violation("element ownership for " + this.key.id() + " was transferred more than once");
            }
            this.elementOwnershipTransferred = true;
        }

        /**
         * Runs all non-element cleanup once, preserving a primary creation failure when supplied.
         */
        private void release(@Nullable Throwable primaryFailure) {
            if (this.released) {
                return;
            }
            this.released = true;
            if (!this.creationCommitted) {
                runCleanupActions(this.creationRollbackActions, "creation rollback", primaryFailure);
            }
            if (!this.elementOwnershipTransferred && this.root != null) {
                runCleanupAction(this.root::disposeUnattached, "element tree rollback", primaryFailure);
            }
            runCleanupActions(this.closeActions, "resource", primaryFailure);
            this.creationRollbackActions.clear();
            this.closeActions.clear();
        }

        /**
         * Runs one cleanup category in reverse acquisition order without interrupting element-tree removal.
         */
        private void runCleanupActions(List<Runnable> actions, String category, @Nullable Throwable primaryFailure) {
            for (int index = actions.size() - 1; index >= 0; index--) {
                runCleanupAction(actions.get(index), category, primaryFailure);
            }
        }

        /**
         * Runs one action while preserving the primary provider or removal failure.
         */
        private void runCleanupAction(Runnable action, String category, @Nullable Throwable primaryFailure) {
            try {
                action.run();
            } catch (RuntimeException | Error cleanupFailure) {
                Data_Energistics.LOGGER.error(
                        "Failed to run {} cleanup for LDLib2 host child UI {}",
                        category,
                        this.key.id(),
                        cleanupFailure);
                if (primaryFailure != null && primaryFailure != cleanupFailure) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    this.releaseFailure = mergeFailures(this.releaseFailure, cleanupFailure);
                }
            }
        }

        /**
         * Returns a deferred cleanup failure after structural removal has safely completed.
         */
        private @Nullable Throwable releaseFailure() {
            return this.releaseFailure;
        }

        /**
         * Rejects provider callbacks fired synchronously while their element tree is still mounting.
         */
        private void ensureAttached() {
            if (!this.attached || this.released) {
                throw violation("child UI " + this.key.id() + " is not attached");
            }
        }
    }
}
