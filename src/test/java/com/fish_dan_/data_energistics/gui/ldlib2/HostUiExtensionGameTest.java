package com.fish_dan_.data_energistics.gui.ldlib2;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.menu.AEBaseMenu;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class HostUiExtensionGameTest {

    private static final AtomicLong NEXT_TEST_GENERATION = new AtomicLong(1L);

    private HostUiExtensionGameTest() {}

    @TestHolder("host_ui_supports_four_independent_windows")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void supportsFourIndependentWindows(GameTestHelper helper) {
        HostFixture fixture = createFixture(helper, 1);
        CountingProvider main = fixture.register("main");
        CountingProvider cpu = fixture.register("cpu");
        CountingProvider crafting = fixture.register("crafting");
        CountingProvider autoBuild = fixture.register("auto_build");

        assertTrue(openLocally(fixture.extension, main.key()), "main must open");
        assertTrue(openLocally(fixture.extension, cpu.key()), "cpu must open");
        assertTrue(openLocally(fixture.extension, crafting.key()), "crafting must open");
        assertTrue(openLocally(fixture.extension, autoBuild.key()), "auto-build must open");
        assertEquals(4, fixture.extension.openKeys().size());
        assertEquals(300, zIndex(main.latestRoot()));
        assertEquals(301, zIndex(cpu.latestRoot()));
        assertEquals(302, zIndex(crafting.latestRoot()));
        assertEquals(303, zIndex(autoBuild.latestRoot()));
        assertSame(Sprites.RECT_DARK, background(main.latestDragHandle()));
        assertSame(Sprites.RECT_DARK, background(cpu.latestDragHandle()));
        assertSame(Sprites.RECT_DARK, background(crafting.latestDragHandle()));
        assertSame(Sprites.RECT, background(autoBuild.latestDragHandle()));
        assertFalse(fixture.modularUI.shouldCloseOnEsc(), "host Escape close must pause while a child UI is open");
        assertDifferent(main.latestRoot(), cpu.latestRoot());
        assertDifferent(cpu.latestRoot(), crafting.latestRoot());
        assertDifferent(crafting.latestRoot(), autoBuild.latestRoot());

        UIElement transientPopup = new UIElement().addClass(HostUiExtension.TRANSIENT_POPUP_CLASS);
        fixture.root.addChild(transientPopup);
        assertTrue(
                fixture.extension.handleKeyPressed(256, 0, 0),
                "Escape must close a root transient popup before a hosted window");
        assertSame(null, transientPopup.getParent());
        assertEquals(4, fixture.extension.openKeys().size());
        assertEquals(0, autoBuild.closeCount);

        UIElement firstMainRoot = main.latestRoot();
        assertFalse(openLocally(fixture.extension, main.key()), "opening an existing key must only promote it");
        assertEquals(1, main.createCount);
        assertSame(main.key(), fixture.extension.openKeys().getLast());
        assertEquals(300, zIndex(cpu.latestRoot()));
        assertEquals(301, zIndex(crafting.latestRoot()));
        assertEquals(302, zIndex(autoBuild.latestRoot()));
        assertEquals(303, zIndex(main.latestRoot()));
        assertSame(Sprites.RECT_DARK, background(autoBuild.latestDragHandle()));
        assertSame(Sprites.RECT, background(main.latestDragHandle()));
        assertFalse(fixture.extension.handleKeyPressed(65, 0, 0), "non-Escape input must remain with the host");
        assertTrue(closeTopmostLocally(fixture.extension), "topmost local fixture close must remove the child UI");
        assertEquals(1, main.closeCount);
        assertSame(Sprites.RECT, background(autoBuild.latestDragHandle()));
        assertFalse(closeLocally(fixture.extension, main.key()), "a repeated close must be idempotent");

        assertTrue(openLocally(fixture.extension, main.key()), "a closed key must reopen");
        assertEquals(2, main.createCount);
        assertDifferent(firstMainRoot, main.latestRoot());
        assertSame(main.key(), fixture.extension.openKeys().getLast());

        closeAllLocally(fixture.extension);
        assertEquals(0, fixture.extension.openKeys().size());
        assertTrue(fixture.modularUI.shouldCloseOnEsc(), "closing the last child must restore the host Escape policy");
        assertEquals(1, cpu.closeCount);
        assertEquals(1, crafting.closeCount);
        assertEquals(1, autoBuild.closeCount);
        assertEquals(2, main.closeCount);

        HostUiKey invalidDragKey = key("invalid_drag_handle");
        fixture.extension.register(new HostSubUiProvider() {

            @Override
            public HostUiKey key() {
                return invalidDragKey;
            }

            @Override
            public HostSubUi create(HostSubUiContext context) {
                HostSubUiRoot root = context.createRoot();
                return new HostSubUi(root, root);
            }
        });
        assertIllegalArgument(() -> openLocally(fixture.extension, invalidDragKey));

        CountingProvider reused = fixture.register("reused");
        assertTrue(openLocally(fixture.extension, reused.key()), "fresh reusable fixture must open once");
        assertTrue(closeLocally(fixture.extension, reused.key()), "first reusable fixture instance must close");
        reused.reuseLatestTree = true;
        assertThrows(() -> openLocally(fixture.extension, reused.key()));
        assertEquals(2, reused.closeCount);
        assertFalse(fixture.extension.isOpen(reused.key()), "a reused tree must never be attached again");

        HostUiKey reusedChildKey = key("reused_child");
        UIElement reusedChild = new UIElement();
        int[] reusedChildOpenCount = { 0 };
        fixture.extension.register(new HostSubUiProvider() {

            @Override
            public HostUiKey key() {
                return reusedChildKey;
            }

            @Override
            public HostSubUi create(HostSubUiContext context) {
                HostSubUiRoot root = context.createRoot();
                reusedChildOpenCount[0]++;
                root.addChild(reusedChild);
                return new HostSubUi(root, reusedChild);
            }
        });
        assertTrue(openLocally(fixture.extension, reusedChildKey), "fresh child fixture must open once");
        assertTrue(closeLocally(fixture.extension, reusedChildKey), "fresh child fixture must close once");
        assertThrows(() -> openLocally(fixture.extension, reusedChildKey));
        assertEquals(2, reusedChildOpenCount[0]);

        assertThrows(() -> fixture.extension.createModularUI(UI.of(new UIElement()), fixture.player));
        assertThrows(() -> fixture.extension.createModularUI(UI.of(fixture.root), fixture.player));
        assertThrows(() -> HostUiExtension.create(fixture.root));

        UIElement duplicateHostRoot = new UIElement();
        HostUiExtension duplicateGuard = HostUiExtension.create(duplicateHostRoot);
        assertThrows(() -> HostUiExtension.create(duplicateHostRoot));
        HostUiExtension.discardUnmounted(duplicateGuard);

        UIElement parentedHostRoot = new UIElement();
        new UIElement().addChild(parentedHostRoot);
        assertThrows(() -> HostUiExtension.create(parentedHostRoot));

        HostUiKey boundKey = key("bound_tree");
        HostModularUI[] boundModularUI = { null };
        fixture.extension.register(new HostSubUiProvider() {

            @Override
            public HostUiKey key() {
                return boundKey;
            }

            @Override
            public HostSubUi create(HostSubUiContext context) {
                HostSubUiRoot boundRoot = context.createRoot();
                UIElement boundDragHandle = new UIElement();
                boundRoot.addChild(boundDragHandle);
                HostUiExtension boundExtension = HostUiExtension.create(boundRoot);
                boundModularUI[0] = boundExtension.createModularUI(UI.of(boundRoot), fixture.player);
                TestMenu boundMenu = new TestMenu(fixture.player, 5);
                AeMenuBridge.create(boundMenu).mount(boundModularUI[0]);
                return new HostSubUi(boundRoot, boundDragHandle);
            }
        });
        assertThrows(() -> openLocally(fixture.extension, boundKey));
        assertFalse(fixture.extension.isOpen(boundKey), "a tree owned by another ModularUI must stay detached");
        boundModularUI[0].onRemoved();
        fixture.modularUI.onRemoved();
        fixture.modularUI.onRemoved();
        helper.succeed();
    }

    @TestHolder("host_ui_cleans_failed_and_externally_removed_windows")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cleansFailedAndExternallyRemovedWindows(GameTestHelper helper) {
        HostFixture fixture = createFixture(helper, 2);
        CountingProvider stable = fixture.register("stable");
        assertThrows(() -> fixture.extension.register(new CountingProvider(stable.key())));

        HostUiKey creationFailureKey = key("creation_failure");
        int[] creationFailureCleanup = { 0 };
        int[] creationRollbackCleanup = { 0 };
        RemovalTrackingElement creationFailureChild = new RemovalTrackingElement();
        fixture.extension.register(new HostSubUiProvider() {

            @Override
            public HostUiKey key() {
                return creationFailureKey;
            }

            @Override
            public HostSubUi create(HostSubUiContext context) {
                context.createRoot().addChild(creationFailureChild);
                context.onCreationRollback(() -> creationRollbackCleanup[0]++);
                context.onClose(() -> creationFailureCleanup[0]++);
                throw new IllegalStateException("Test provider creation failure");
            }
        });
        assertThrows(() -> openLocally(fixture.extension, creationFailureKey));
        assertEquals(1, creationFailureCleanup[0]);
        assertEquals(1, creationRollbackCleanup[0]);
        assertEquals(1, creationFailureChild.removalCount);
        assertFalse(fixture.extension.isOpen(creationFailureKey), "failed creation must not leave an active entry");

        UIElement externalParent = new UIElement();
        CountingProvider parented = new CountingProvider(key("parented"));
        parented.rootDecorator = externalParent::addChild;
        fixture.extension.register(parented);
        assertThrows(() -> openLocally(fixture.extension, parented.key()));
        assertEquals(1, parented.closeCount);
        assertTrue(externalParent.hasChild(parented.latestRoot()), "host must not detach another owner's tree");

        CountingProvider mountFailure = new CountingProvider(key("mount_failure"));
        int[] mountFailureRollback = { 0 };
        mountFailure.creationRollbackAction = () -> mountFailureRollback[0]++;
        RemovalTrackingElement mountFailureChild = new RemovalTrackingElement();
        mountFailure.rootDecorator = root -> root.addEventListener(UIEvents.ADDED, event -> {
            throw new IllegalStateException("Test host child mount failure");
        });
        mountFailure.rootDecorator = mountFailure.rootDecorator.andThen(root -> root.addChild(mountFailureChild));
        fixture.extension.register(mountFailure);
        assertThrows(() -> openLocally(fixture.extension, mountFailure.key()));
        assertEquals(1, mountFailure.closeCount);
        assertEquals(1, mountFailureRollback[0]);
        assertEquals(1, mountFailureChild.removalCount);
        assertSame(null, mountFailure.latestRoot().getParent());

        assertTrue(openLocally(fixture.extension, stable.key()), "stable provider must still open after isolated failures");
        UIElement removedRoot = stable.latestRoot();
        assertTrue(removedRoot.removeSelf(), "external removal must detach the child tree");
        assertEquals(1, stable.closeCount);
        assertFalse(fixture.extension.isOpen(stable.key()), "external removal must clear host bookkeeping");
        assertFalse(closeLocally(fixture.extension, stable.key()), "external removal cleanup must remain idempotent");
        assertSame(null, removedRoot.getModularUI());

        assertTrue(fixture.extension.isDisposed(), "external removal must make a coordinated-capable host terminal");
        fixture.modularUI.onRemoved();

        HostFixture removalFixture = createFixture(helper, 12);

        CountingProvider removalFailure = removalFixture.register("removal_failure");
        RemovalTrackingElement removalAfterFailure = new RemovalTrackingElement();
        IllegalStateException childRemovalFailure = new IllegalStateException("Test host child removal failure");
        IllegalStateException rootRemovalFailure = new IllegalStateException("Test host root removal failure");
        removalFailure.rootDecorator = root -> {
            UIElement failingChild = new UIElement();
            failingChild.addEventListener(UIEvents.REMOVED, event -> {
                throw childRemovalFailure;
            });
            root.addChild(failingChild);
            root.addChild(removalAfterFailure);
            root.addEventListener(UIEvents.REMOVED, event -> {
                throw rootRemovalFailure;
            });
        };
        assertTrue(openLocally(removalFixture.extension, removalFailure.key()), "removal failure fixture must open");
        assertThrowsSame(childRemovalFailure, () -> removalFailure.latestRoot().removeSelf());
        assertSuppressed(rootRemovalFailure, childRemovalFailure);
        assertEquals(1, removalFailure.closeCount);
        assertEquals(1, removalAfterFailure.removalCount);
        assertSame(null, removalFailure.latestRoot().getParent());
        assertSame(null, removalFailure.latestRoot().getModularUI());
        assertTrue(removalFixture.extension.isDisposed(), "a failed LDLib2 removal callback must make its host terminal");

        removalFixture.modularUI.onRemoved();
        removalFixture.modularUI.onRemoved();
        helper.succeed();
    }

    @TestHolder("host_ui_detachment_failure_is_terminal")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void detachmentFailureIsTerminal(GameTestHelper helper) {
        HostFixture fixture = createFixture(helper, 7);
        CountingProvider provider = fixture.register("detach_failure");
        IllegalStateException detachmentFailure = new IllegalStateException("Test host detachment failure");
        provider.rootDecorator = root -> {
            root.addEventListener(UIEvents.REMOVED, event -> assertFalse(
                    root.removeSelf(), "a reentrant removal must observe that the parent already removed this root"));
            root.addEventListener(UIEvents.MUI_CHANGED, event -> {
                if (root.getModularUI() == null) {
                    throw detachmentFailure;
                }
            });
        };
        assertTrue(openLocally(fixture.extension, provider.key()), "detachment failure fixture must open");

        assertThrowsSame(detachmentFailure, () -> provider.latestRoot().removeSelf());
        assertTrue(fixture.extension.isDisposed(), "an incomplete external detach must make its host terminal");
        assertTrue(fixture.modularUI.shouldCloseOnEsc(), "an incomplete external detach must restore host Escape");
        assertEquals(1, provider.closeCount);

        fixture.modularUI.onRemoved();

        HostFixture chainedFixture = createFixture(helper, 9);
        CountingProvider chainedProvider = chainedFixture.register("chained_detach_failure");
        IllegalStateException removalFailure = new IllegalStateException("Test primary host removal failure");
        IllegalStateException chainedDetachmentFailure = new IllegalStateException("Test secondary detachment failure");
        chainedProvider.rootDecorator = root -> {
            root.addEventListener(UIEvents.REMOVED, event -> {
                throw removalFailure;
            });
            root.addEventListener(UIEvents.MUI_CHANGED, event -> {
                if (root.getModularUI() == null) {
                    throw chainedDetachmentFailure;
                }
            });
        };
        assertTrue(openLocally(chainedFixture.extension, chainedProvider.key()), "chained detachment fixture must open");

        assertThrowsSame(removalFailure, () -> chainedProvider.latestRoot().removeSelf());
        assertSuppressed(chainedDetachmentFailure, removalFailure);
        assertTrue(chainedFixture.extension.isDisposed(), "a chained detachment failure must remain terminal");

        chainedFixture.modularUI.onRemoved();
        helper.succeed();
    }

    @TestHolder("host_ui_releases_context_after_reorder_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void releasesContextAfterReorderFailure(GameTestHelper helper) {
        HostFixture fixture = createFixture(helper, 8);
        CountingProvider removed = fixture.register("reorder_removed");
        CountingProvider remaining = fixture.register("reorder_remaining");
        assertTrue(openLocally(fixture.extension, removed.key()), "bottom reorder fixture must open");
        assertTrue(openLocally(fixture.extension, remaining.key()), "top reorder fixture must open");
        IllegalStateException reorderFailure = new IllegalStateException("Test host reorder failure");
        remaining.latestRoot().addEventListener(UIEvents.STYLE_CHANGED, event -> {
            throw reorderFailure;
        });

        assertThrowsSame(reorderFailure, () -> removed.latestRoot().removeSelf());
        assertEquals(1, removed.closeCount);
        assertTrue(fixture.extension.isDisposed(), "a failed removal reorder must make its host terminal");
        assertTrue(fixture.modularUI.shouldCloseOnEsc(), "a failed removal reorder must restore host Escape");

        fixture.modularUI.onRemoved();
        assertEquals(1, remaining.closeCount);
        helper.succeed();
    }

    @TestHolder("host_ui_terminal_rollback_restores_host_escape")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void terminalRollbackRestoresHostEscape(GameTestHelper helper) {
        HostFixture fixture = createFixture(helper, 6);
        CountingProvider stable = fixture.register("terminal_stable");
        CountingProvider rollbackFailure = fixture.register("terminal_rollback_failure");
        IllegalStateException terminalFailure = new IllegalStateException("Test terminal rollback failure");
        rollbackFailure.rootDecorator = root -> {
            root.addEventListener(UIEvents.ADDED, event -> {
                throw terminalFailure;
            });
            root.addEventListener(UIEvents.REMOVED, event -> {
                throw terminalFailure;
            });
        };
        assertTrue(openLocally(fixture.extension, stable.key()), "stable window must open before terminal rollback");

        assertThrows(() -> openLocally(fixture.extension, rollbackFailure.key()));
        assertTrue(fixture.extension.isDisposed(), "failed rollback must make the host terminal");
        assertTrue(fixture.modularUI.shouldCloseOnEsc(), "terminal rollback must restore host Escape closing");
        assertFalse(
                fixture.extension.handleKeyPressed(256, 0, 0),
                "a terminal host must leave Escape to its Screen");

        fixture.modularUI.onRemoved();
        assertEquals(1, stable.closeCount);
        assertEquals(1, rollbackFailure.closeCount);
        helper.succeed();
    }

    @TestHolder("host_ui_releases_root_after_cleanup_error")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void releasesRootAfterCleanupError(GameTestHelper helper) {
        RemovalTrackingElement hostRoot = new RemovalTrackingElement();
        HostFixture fixture = createFixture(helper, 4, hostRoot);
        HostUiKey key = key("cleanup_error");
        int[] cleanupCount = { 0 };
        AssertionError cleanupFailure = new AssertionError("Test cleanup error");
        hostRoot.addEventListener(UIEvents.REMOVED, event -> {
            throw cleanupFailure;
        });
        fixture.extension.register(new HostSubUiProvider() {

            @Override
            public HostUiKey key() {
                return key;
            }

            @Override
            public HostSubUi create(HostSubUiContext context) {
                context.onClose(() -> {
                    cleanupCount[0]++;
                    throw cleanupFailure;
                });
                HostSubUiRoot root = context.createRoot();
                UIElement dragHandle = new UIElement();
                root.addChild(dragHandle);
                return new HostSubUi(root, dragHandle);
            }
        });
        assertTrue(openLocally(fixture.extension, key), "cleanup error fixture must open");

        assertAssertionError(fixture.modularUI::onRemoved);
        assertEquals(1, cleanupCount[0]);
        assertEquals(1, hostRoot.removalCount);
        fixture.modularUI.onRemoved();
        assertEquals(1, hostRoot.removalCount);
        helper.succeed();
    }

    @TestHolder("host_ui_disposal_is_terminal_and_exactly_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void disposalIsTerminalAndExactlyOnce(GameTestHelper helper) {
        HostFixture fixture = createFixture(helper, 3);
        CountingProvider main = fixture.register("main");
        CountingProvider cpu = fixture.register("cpu");
        CountingProvider crafting = fixture.register("crafting");
        CountingProvider autoBuild = fixture.register("auto_build");
        openLocally(fixture.extension, main.key());
        openLocally(fixture.extension, cpu.key());
        openLocally(fixture.extension, crafting.key());
        openLocally(fixture.extension, autoBuild.key());

        assertTrue(fixture.extension.bringToFront(main.key()), "an open window must be promotable");
        assertSame(main.key(), fixture.extension.openKeys().getLast());
        assertTrue(closeTopmostLocally(fixture.extension), "topmost close must remove the promoted window");
        assertEquals(1, main.closeCount);
        assertTrue(openLocally(fixture.extension, main.key()), "main must reopen as a fresh instance");

        fixture.modularUI.onRemoved();
        fixture.modularUI.onRemoved();
        assertTrue(fixture.extension.isDisposed(), "host removal must terminate its extension");
        assertEquals(2, main.closeCount);
        assertEquals(1, cpu.closeCount);
        assertEquals(1, crafting.closeCount);
        assertEquals(1, autoBuild.closeCount);
        assertThrows(() -> openLocally(fixture.extension, main.key()));
        assertEquals(2, main.createCount);
        helper.succeed();
    }

    /** Creates and mounts a host tree on the real AE/LDLib2 menu bridge. */
    private static HostFixture createFixture(GameTestHelper helper, int containerId) {
        return createFixture(helper, containerId, new UIElement());
    }

    /** Creates and mounts a host tree using a caller-supplied root for direct removal verification. */
    private static HostFixture createFixture(GameTestHelper helper, int containerId, UIElement root) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestMenu menu = new TestMenu(player, containerId);
        HostUiExtension extension = HostUiExtension.create(root);
        HostModularUI modularUI = extension.createModularUI(UI.of(root), player);
        AeMenuBridge.create(menu).mount(modularUI);
        return new HostFixture(extension, modularUI, root, player);
    }

    /** Drives the package-private authoritative target directly for host-internal fault-injection tests. */
    private static boolean openLocally(HostUiExtension extension, HostUiKey key) {
        return localTarget(extension).openFresh(key, NEXT_TEST_GENERATION.getAndIncrement());
    }

    /** Drives one package-private close without exposing a client bypass in the public host API. */
    private static boolean closeLocally(HostUiExtension extension, HostUiKey key) {
        return localTarget(extension).closeAuthoritatively(key);
    }

    /** Closes the current local topmost test fixture window. */
    private static boolean closeTopmostLocally(HostUiExtension extension) {
        List<HostUiKey> openKeys = extension.openKeys();
        return !openKeys.isEmpty() && closeLocally(extension, openKeys.getLast());
    }

    /** Releases all local test fixture windows in reverse z-order. */
    private static void closeAllLocally(HostUiExtension extension) {
        List<HostUiKey> openKeys = extension.openKeys();
        for (int index = openKeys.size() - 1; index >= 0; index--) {
            closeLocally(extension, openKeys.get(index));
        }
    }

    /** Narrows the production factory result without reflection. */
    private static HostUiExtensionImpl localTarget(HostUiExtension extension) {
        if (!(extension instanceof HostUiExtensionImpl implementation)) {
            throw new GameTestAssertException("Expected HostUiExtensionImpl test target");
        }
        return implementation;
    }

    /** Creates a namespaced test identity. */
    private static HostUiKey key(String path) {
        return new HostUiKey(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "test/" + path));
    }

    /** Requires the supplied action to reject an invalid host operation. */
    private static void assertThrows(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    /** Requires the supplied action to preserve one exact unchecked failure instance. */
    private static void assertThrowsSame(Throwable expected, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error actual) {
            assertSame(expected, actual);
            return;
        }
        throw new GameTestAssertException("Expected unchecked failure");
    }

    /** Requires one exact cleanup failure to remain attached to the primary failure. */
    private static void assertSuppressed(Throwable expected, Throwable primary) {
        for (Throwable suppressed : primary.getSuppressed()) {
            if (suppressed == expected) {
                return;
            }
        }
        throw new GameTestAssertException("Expected suppressed failure");
    }

    /** Requires the supplied action to reject an invalid value object. */
    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalArgumentException");
    }

    /** Requires cleanup to surface its Error only after the surrounding element tree has been released. */
    private static void assertAssertionError(Runnable action) {
        try {
            action.run();
        } catch (AssertionError expected) {
            return;
        }
        throw new GameTestAssertException("Expected AssertionError");
    }

    /** Requires exact object identity. */
    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected identical objects");
        }
    }

    /** Requires distinct object identities. */
    private static void assertDifferent(Object first, Object second) {
        if (first == second) {
            throw new GameTestAssertException("Expected distinct objects");
        }
    }

    /** Requires equal integer values. */
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    /** Reads the important z-index candidate written by the host ordering implementation. */
    private static int zIndex(UIElement element) {
        Integer zIndex = element.getStyle().getImportant(PropertyRegistry.Z_INDEX);
        if (zIndex == null) {
            throw new GameTestAssertException("Expected an important z-index candidate");
        }
        return zIndex;
    }

    /** Reads the explicit active/inactive title background written by host ordering. */
    private static IGuiTexture background(UIElement element) {
        IGuiTexture background = element.getStyle().getImportant(PropertyRegistry.BACKGROUND);
        if (background == null) {
            throw new GameTestAssertException("Expected an important title background candidate");
        }
        return background;
    }

    /** Requires a true condition. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Requires a false condition. */
    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Unmounted AE menu used to isolate host lifecycle behavior from production UI construction. */
    private static final class TestMenu extends AEBaseMenu {

        private TestMenu(Player player, int containerId) {
            super(null, containerId, player.getInventory(), null);
        }
    }

    /** Mounted host state used by each direct lifecycle test. */
    private record HostFixture(HostUiExtension extension, HostModularUI modularUI, UIElement root, Player player) {

        /** Registers a counting provider for one short test path. */
        private CountingProvider register(String path) {
            CountingProvider provider = new CountingProvider(key(path));
            this.extension.register(provider);
            return provider;
        }
    }

    /** Provider whose fresh roots and cleanup counts expose every host lifecycle transition. */
    private static final class CountingProvider implements HostSubUiProvider {

        private final HostUiKey key;
        private final List<UIElement> roots = new ArrayList<>();
        @Nullable
        private Consumer<UIElement> rootDecorator;
        @Nullable
        private Runnable creationRollbackAction;
        private boolean reuseLatestTree;
        private int createCount;
        private int closeCount;
        @Nullable
        private HostSubUi latestSubUi;

        private CountingProvider(HostUiKey key) {
            this.key = key;
        }

        @Override
        public HostUiKey key() {
            return this.key;
        }

        @Override
        public HostSubUi create(HostSubUiContext context) {
            this.createCount++;
            context.onClose(() -> this.closeCount++);
            if (this.creationRollbackAction != null) {
                context.onCreationRollback(this.creationRollbackAction);
            }
            if (this.reuseLatestTree && this.latestSubUi != null) {
                return this.latestSubUi;
            }
            HostSubUiRoot root = context.createRoot();
            UIElement dragHandle = new UIElement();
            root.addChild(dragHandle);
            this.roots.add(root);
            if (this.rootDecorator != null) {
                this.rootDecorator.accept(root);
            }
            this.latestSubUi = new HostSubUi(root, dragHandle);
            return this.latestSubUi;
        }

        /** Returns the root created by the latest provider invocation. */
        private UIElement latestRoot() {
            return this.roots.getLast();
        }

        /** Returns the drag handle belonging to the latest fresh hosted tree. */
        private UIElement latestDragHandle() {
            if (this.latestSubUi == null) {
                throw new GameTestAssertException("Provider has not created a hosted tree");
            }
            return this.latestSubUi.dragHandle();
        }
    }

    /** Element whose real onRemoved callback proves that cleanup continued after another callback failed. */
    private static final class RemovalTrackingElement extends UIElement {

        private int removalCount;

        @Override
        protected void onRemoved() {
            this.removalCount++;
            super.onRemoved();
        }
    }
}
