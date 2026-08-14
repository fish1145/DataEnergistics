package com.fish_dan_.data_energistics.gui.ldlib2.host.window;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiOperation;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiRequest;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiResponse;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiResponseStatus;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.menu.AEBaseMenu;
import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class HostUiCoordinatorGameTest {

    private static final HostUiKey MAIN = key("main");
    private static final HostUiKey CPU = key("cpu");
    private static final HostUiKey CRAFTING = key("crafting");
    private static final HostUiKey AUTO_BUILD = key("auto_build");
    private static final List<HostUiKey> REGISTRATION_ORDER = List.of(MAIN, CPU, CRAFTING, AUTO_BUILD);

    private HostUiCoordinatorGameTest() {}

    @TestHolder("host_ui_coordinator_mirrors_four_authoritative_windows")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mirrorsFourAuthoritativeWindows(GameTestHelper helper) {
        CoordinatorPair pair = createPair(helper, 20, 21);
        assertEquals(REGISTRATION_ORDER, pair.client.endpoint.extension.registeredKeys());
        assertEquals(REGISTRATION_ORDER, pair.server.endpoint.extension.registeredKeys());
        assertThrows(() -> pair.client.endpoint.extension.register(new CountingProvider(key("late"))));

        assertTrue(pair.client.coordinator.requestOpen(MAIN), "first open request must be emitted");
        assertFalse(pair.client.endpoint.extension.isOpen(MAIN), "client must not open before an acknowledgement");
        assertFalse(pair.server.endpoint.extension.isOpen(MAIN), "server must not open before handling the request");
        assertFalse(pair.client.coordinator.requestOpen(CPU), "one global pending request must block every key");
        assertEquals(1, pair.requests.size());

        HostUiRequest openMain = pair.requests.removeFirst();
        HostUiResponse openMainResponse = pair.server.coordinator.handleRequest(openMain, true);
        assertTrue(openMainResponse.accepted(), "server must accept the first ordered open");
        assertTrue(pair.server.endpoint.extension.isOpen(MAIN), "server must mutate before sending its response");
        assertFalse(pair.client.endpoint.extension.isOpen(MAIN), "client must still wait for the matching response");

        HostUiResponse unrelatedResponse = HostUiResponse.accepted(
                new HostUiRequest(HostUiOperation.OPEN, CPU, openMain.sequence()));
        assertFalse(pair.client.coordinator.handleResponse(unrelatedResponse), "unrelated response must be ignored");
        assertEquals(openMain, pair.client.coordinator.pendingRequest());
        assertFalse(pair.client.endpoint.extension.isOpen(MAIN), "ignored response must not change membership");

        assertTrue(pair.client.coordinator.handleResponse(openMainResponse), "matching response must mirror the open");
        assertTrue(pair.client.endpoint.extension.isOpen(MAIN), "client must open only after the accepted response");
        assertFalse(pair.client.coordinator.handleResponse(openMainResponse), "duplicate response must not reopen");
        assertEquals(1, pair.client.endpoint.provider(MAIN).createCount);

        roundTripOpen(pair, CPU);
        roundTripOpen(pair, CRAFTING);
        roundTripOpen(pair, AUTO_BUILD);
        assertEquals(REGISTRATION_ORDER, pair.client.endpoint.extension.openKeys());
        assertEquals(REGISTRATION_ORDER, pair.server.endpoint.extension.openKeys());

        HostSubUiRoot firstClientMainRoot = pair.client.endpoint.provider(MAIN).latestRoot();
        HostSubUiRoot firstServerMainRoot = pair.server.endpoint.provider(MAIN).latestRoot();
        HostSubUiContext firstClientMainContext = pair.client.endpoint.provider(MAIN).latestContext;
        HostSubUiContext firstServerMainContext = pair.server.endpoint.provider(MAIN).latestContext;
        assertEquals(1L, firstClientMainContext.generation());
        assertEquals(1L, firstServerMainContext.generation());
        assertTrue(pair.client.endpoint.extension.bringToFront(MAIN), "client z-order promotion must remain local");
        assertEquals(MAIN, pair.client.endpoint.extension.openKeys().getLast());
        assertEquals(AUTO_BUILD, pair.server.endpoint.extension.openKeys().getLast());

        CountingProvider clientCpu = pair.client.endpoint.provider(CPU);
        clientCpu.dispatchAction();
        assertEquals(1, clientCpu.actionCount);
        clientCpu.dispatchRootAction();
        assertEquals(1, clientCpu.rootCaptureActionCount);
        assertEquals(1, clientCpu.rootBubbleActionCount);
        assertTrue(clientCpu.latestContext.canSendServerAction(), "settled window may emit a custom C2S action");
        assertTrue(pair.client.endpoint.extension.bringToFront(MAIN), "main must be topmost before the Escape check");

        assertTrue(
                pair.client.endpoint.extension.handleKeyPressed(256, 0, 0),
                "Escape must emit a coordinator close request");
        assertTrue(pair.client.endpoint.extension.isOpen(MAIN), "Escape must not remove the client tree optimistically");
        assertTrue(pair.server.endpoint.extension.isOpen(MAIN), "server must wait for the explicit close request");
        assertFalse(clientCpu.latestContext.canSendServerAction(), "pending lifecycle must block custom C2S actions");
        clientCpu.dispatchAction();
        assertEquals(1, clientCpu.actionCount);
        clientCpu.dispatchRootAction();
        assertEquals(1, clientCpu.rootCaptureActionCount);
        assertEquals(1, clientCpu.rootBubbleActionCount);
        assertTrue(
                pair.client.endpoint.extension.handleKeyPressed(256, 0, 0),
                "a repeated Escape must be consumed without emitting a second request");
        assertEquals(1, pair.requests.size());
        roundTripPending(pair);
        assertFalse(pair.client.endpoint.extension.isOpen(MAIN), "accepted Escape close must remove the client tree");
        assertFalse(pair.server.endpoint.extension.isOpen(MAIN), "accepted Escape close must remove the server tree");
        assertTrue(clientCpu.latestContext.canSendServerAction(), "matching response must release the interaction guard");

        roundTripOpen(pair, MAIN);
        assertDifferent(firstClientMainRoot, pair.client.endpoint.provider(MAIN).latestRoot());
        assertDifferent(firstServerMainRoot, pair.server.endpoint.provider(MAIN).latestRoot());
        assertFalse(
                pair.client.endpoint.extension.isOpen(MAIN, firstClientMainContext.generation()),
                "closed generation must never validate after reopen");
        assertTrue(pair.client.endpoint.extension.isOpen(MAIN, 6L), "reopen sequence must become the client generation");
        assertTrue(pair.server.endpoint.extension.isOpen(MAIN, 6L), "reopen sequence must become the server generation");
        assertEquals(7L, pair.client.coordinator.nextSequence());
        assertEquals(7L, pair.server.coordinator.nextSequence());

        pair.close();
        helper.succeed();
    }

    @TestHolder("host_ui_coordinator_rejects_desynchronized_sequences_and_membership")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsDesynchronizedSequencesAndMembership(GameTestHelper helper) {
        CoordinatorPair outOfOrder = createPair(helper, 30, 31);
        HostUiRequest skipped = new HostUiRequest(HostUiOperation.OPEN, MAIN, 2L);
        HostUiResponse skippedResponse = outOfOrder.server.coordinator.handleRequest(skipped, true);
        assertEquals(HostUiResponseStatus.OUT_OF_ORDER_SEQUENCE, skippedResponse.status());
        assertFalse(outOfOrder.server.endpoint.extension.isOpen(MAIN), "out-of-order request must not mutate the tree");
        assertTrue(outOfOrder.server.coordinator.isTerminal(), "out-of-order request must terminate the endpoint");
        outOfOrder.close();

        CoordinatorPair stale = createPair(helper, 32, 33);
        HostUiRequest first = new HostUiRequest(HostUiOperation.OPEN, MAIN, 1L);
        assertTrue(stale.server.coordinator.handleRequest(first, true).accepted(), "first request must establish sequence");
        HostUiResponse replay = stale.server.coordinator.handleRequest(first, true);
        assertEquals(HostUiResponseStatus.STALE_SEQUENCE, replay.status());
        assertEquals(1, stale.server.endpoint.provider(MAIN).createCount);
        assertTrue(stale.server.endpoint.extension.isOpen(MAIN), "replay must not apply a second mutation");
        stale.close();

        CoordinatorPair unknown = createPair(helper, 34, 35);
        HostUiKey unknownKey = key("unknown");
        HostUiResponse unknownResponse = unknown.server.coordinator.handleRequest(
                new HostUiRequest(HostUiOperation.OPEN, unknownKey, 1L),
                true);
        assertEquals(HostUiResponseStatus.UNKNOWN_KEY, unknownResponse.status());
        assertEquals(List.of(), unknown.server.endpoint.extension.openKeys());
        unknown.close();

        CoordinatorPair unavailable = createPair(helper, 36, 37);
        HostUiRequest unavailableRequest = new HostUiRequest(HostUiOperation.OPEN, MAIN, 1L);
        HostUiResponse unavailableResponse = unavailable.server.coordinator.handleRequest(unavailableRequest, false);
        assertEquals(HostUiResponseStatus.HOST_UNAVAILABLE, unavailableResponse.status());
        assertFalse(unavailable.server.endpoint.extension.isOpen(MAIN), "invalid host must remain unchanged");
        unavailable.close();

        CoordinatorPair membership = createPair(helper, 38, 39);
        HostUiResponse membershipResponse = membership.server.coordinator.handleRequest(
                new HostUiRequest(HostUiOperation.CLOSE, MAIN, 1L),
                true);
        assertEquals(HostUiResponseStatus.MEMBERSHIP_MISMATCH, membershipResponse.status());
        assertFalse(membership.server.endpoint.extension.isOpen(MAIN), "invalid close must not create or remove a tree");
        membership.close();

        CoordinatorPair clientRejection = createPair(helper, 40, 41);
        assertTrue(clientRejection.client.coordinator.requestOpen(MAIN), "client rejection fixture must emit a request");
        HostUiRequest pending = clientRejection.requests.removeFirst();
        assertFalse(
                clientRejection.client.coordinator.handleResponse(
                        HostUiResponse.rejected(pending, HostUiResponseStatus.HOST_UNAVAILABLE)),
                "rejection must not apply client membership");
        assertFalse(clientRejection.client.endpoint.extension.isOpen(MAIN), "rejected client tree must remain unchanged");
        assertTrue(clientRejection.client.coordinator.isTerminal(), "matching rejection must terminate the client endpoint");
        clientRejection.close();
        helper.succeed();
    }

    @TestHolder("host_ui_coordinator_external_removal_is_terminal")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void externalRemovalIsTerminal(GameTestHelper helper) {
        CoordinatorPair pair = createPair(helper, 50, 51);
        roundTripOpen(pair, MAIN);
        HostSubUiRoot clientRoot = pair.client.endpoint.provider(MAIN).latestRoot();

        assertTrue(clientRoot.removeSelf(), "fixture must remove the client root through the real LDLib2 lifecycle");
        assertTrue(pair.client.endpoint.extension.isDisposed(), "uncoordinated removal must make the client host terminal");
        assertTrue(pair.client.coordinator.isTerminal(), "coordinator must expose the terminal host state");
        assertEquals(1, pair.client.terminalTracker.invocationCount);
        assertTrue(pair.server.endpoint.extension.isOpen(MAIN), "external client removal must not pretend the server closed");
        assertFalse(
                pair.client.endpoint.extension.handleKeyPressed(256, 0, 0),
                "terminal host must leave Escape to the containing Screen");

        pair.close();

        CoordinatorPair directParentRemoval = createPair(helper, 52, 53);
        roundTripOpen(directParentRemoval, MAIN);
        HostSubUiRoot directlyRemovedRoot = directParentRemoval.client.endpoint.provider(MAIN).latestRoot();
        UIElement parent = directlyRemovedRoot.getParent();
        if (parent == null) {
            throw new GameTestAssertException("Expected mounted host child parent");
        }
        assertTrue(parent.removeChild(directlyRemovedRoot), "direct parent removal must complete structurally");
        assertTrue(directParentRemoval.client.coordinator.isTerminal(), "direct parent removal must be terminal");
        assertEquals(1, directParentRemoval.client.terminalTracker.invocationCount);
        directParentRemoval.close();

        CoordinatorPair failedDetachment = createPair(helper, 54, 55);
        roundTripOpen(failedDetachment, MAIN);
        HostSubUiRoot failedRoot = failedDetachment.client.endpoint.provider(MAIN).latestRoot();
        IllegalStateException detachmentFailure = new IllegalStateException("Test coordinator detachment failure");
        failedRoot.addEventListener(UIEvents.MUI_CHANGED, event -> {
            if (failedRoot.getModularUI() == null) {
                throw detachmentFailure;
            }
        });
        assertThrowsSame(detachmentFailure, failedRoot::removeSelf);
        assertTrue(failedDetachment.client.coordinator.isTerminal(), "failed external detach must be terminal");
        assertEquals(1, failedDetachment.client.terminalTracker.invocationCount);
        failedDetachment.close();
        helper.succeed();
    }

    @TestHolder("host_ui_coordinator_preserves_dynamic_sync_and_rpc_ids")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesDynamicSyncAndRpcIds(GameTestHelper helper) {
        CoordinatorPair pair = createPair(helper, 56, 57);
        roundTripOpen(pair, MAIN);
        assertEquals(0, soleSyncId(pair.client.endpoint.modularUI));
        assertEquals(0, soleSyncId(pair.server.endpoint.modularUI));
        invokeRpc(pair.client.endpoint.modularUI, 0);
        invokeRpc(pair.server.endpoint.modularUI, 0);
        assertEquals(1, pair.client.endpoint.provider(MAIN).rpcActionCount);
        assertEquals(1, pair.server.endpoint.provider(MAIN).rpcActionCount);

        roundTripClose(pair, MAIN);
        roundTripOpen(pair, MAIN);
        assertEquals(1, soleSyncId(pair.client.endpoint.modularUI));
        assertEquals(1, soleSyncId(pair.server.endpoint.modularUI));
        invokeRpc(pair.client.endpoint.modularUI, 1);
        invokeRpc(pair.server.endpoint.modularUI, 1);
        assertEquals(2, pair.client.endpoint.provider(MAIN).rpcActionCount);
        assertEquals(2, pair.server.endpoint.provider(MAIN).rpcActionCount);

        pair.close();
        helper.succeed();
    }

    @TestHolder("host_ui_coordinator_apply_failures_are_terminal")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void applyFailuresAreTerminal(GameTestHelper helper) {
        CoordinatorPair serverFailure = createPair(helper, 58, 59);
        serverFailure.server.endpoint.provider(MAIN).failCreation = true;
        assertTrue(serverFailure.client.coordinator.requestOpen(MAIN), "server failure fixture must emit an open");
        HostUiRequest failedServerRequest = serverFailure.requests.removeFirst();
        HostUiResponse failedServerResponse = serverFailure.server.coordinator.handleRequest(failedServerRequest, true);
        assertEquals(HostUiResponseStatus.APPLY_FAILED, failedServerResponse.status());
        assertTrue(serverFailure.server.coordinator.isTerminal(), "server apply failure must terminate its endpoint");
        assertEquals(1L, serverFailure.server.coordinator.nextSequence());
        assertFalse(serverFailure.server.endpoint.extension.isOpen(MAIN), "failed server open must leave no membership");
        assertFalse(
                serverFailure.client.coordinator.handleResponse(failedServerResponse),
                "rejected server apply must not mutate the client");
        assertTrue(serverFailure.client.coordinator.isTerminal(), "matching apply rejection must terminate the client");
        assertNull(serverFailure.client.coordinator.pendingRequest());
        assertEquals(1L, serverFailure.client.coordinator.nextSequence());
        assertEquals(0, serverFailure.client.terminalTracker.invocationCount);
        serverFailure.close();

        CoordinatorPair clientFailure = createPair(helper, 63, 64);
        clientFailure.client.endpoint.provider(MAIN).failCreation = true;
        assertTrue(clientFailure.client.coordinator.requestOpen(MAIN), "client failure fixture must emit an open");
        HostUiRequest failedClientRequest = clientFailure.requests.removeFirst();
        HostUiResponse acceptedServerResponse = clientFailure.server.coordinator.handleRequest(failedClientRequest, true);
        assertTrue(acceptedServerResponse.accepted(), "server must apply before the client mirror fails");
        assertFalse(
                clientFailure.client.coordinator.handleResponse(acceptedServerResponse),
                "failed client mirror must not report membership success");
        assertTrue(clientFailure.client.coordinator.isTerminal(), "client mirror failure must terminate the endpoint");
        assertFalse(clientFailure.client.endpoint.extension.isOpen(MAIN), "failed client mirror must leave no membership");
        assertTrue(clientFailure.server.endpoint.extension.isOpen(MAIN), "server membership exposes the terminal divergence");
        assertNull(clientFailure.client.coordinator.pendingRequest());
        assertEquals(1L, clientFailure.client.coordinator.nextSequence());
        assertEquals(2L, clientFailure.server.coordinator.nextSequence());
        assertEquals(1, clientFailure.client.terminalTracker.invocationCount);
        clientFailure.close();
        helper.succeed();
    }

    @TestHolder("host_ui_coordinator_handles_synchronous_transport_and_send_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void handlesSynchronousTransportAndSendFailure(GameTestHelper helper) {
        Endpoint synchronousClientEndpoint = createEndpoint(helper, 60);
        Endpoint synchronousServerEndpoint = createEndpoint(helper, 61);
        TerminalTracker synchronousClientTerminal = new TerminalTracker();
        TerminalTracker synchronousServerTerminal = new TerminalTracker();
        HostUiCoordinator synchronousServer = HostUiCoordinator.createServer(
                synchronousServerEndpoint.extension,
                synchronousServerTerminal::invoke);
        HostUiCoordinator[] synchronousClientReference = { null };
        HostUiCoordinator synchronousClient = HostUiCoordinator.createClient(
                synchronousClientEndpoint.extension,
                request -> synchronousClientReference[0].handleResponse(
                        synchronousServer.handleRequest(request, true)),
                synchronousClientTerminal::invoke);
        synchronousClientReference[0] = synchronousClient;

        assertTrue(synchronousClient.requestOpen(MAIN), "synchronous transport must accept one open request");
        assertTrue(synchronousClientEndpoint.extension.isOpen(MAIN), "synchronous response must open the client once");
        assertTrue(synchronousServerEndpoint.extension.isOpen(MAIN), "synchronous request must open the server once");
        assertEquals(2L, synchronousClient.nextSequence());
        assertEquals(2L, synchronousServer.nextSequence());
        assertNull(synchronousClient.pendingRequest());
        synchronousClientEndpoint.modularUI.onRemoved();
        synchronousServerEndpoint.modularUI.onRemoved();

        Endpoint failedTransportEndpoint = createEndpoint(helper, 62);
        TerminalTracker failedTransportTerminal = new TerminalTracker();
        HostUiCoordinator failedTransport = HostUiCoordinator.createClient(
                failedTransportEndpoint.extension,
                request -> {
                    throw new IllegalStateException("Test host UI transport failure");
                },
                failedTransportTerminal::invoke);
        assertFalse(failedTransport.requestOpen(MAIN), "failed transport must not report an emitted request");
        assertFalse(failedTransportEndpoint.extension.isOpen(MAIN), "failed transport must not mutate membership");
        assertTrue(failedTransport.isTerminal(), "ambiguous transport failure must terminate the endpoint");
        assertEquals(1, failedTransportTerminal.invocationCount);
        assertNull(failedTransport.pendingRequest());
        assertEquals(1L, failedTransport.nextSequence());
        failedTransportEndpoint.modularUI.onRemoved();
        helper.succeed();
    }

    /**
     * Creates two independently mounted roots with equal provider registration order.
     */
    private static CoordinatorPair createPair(GameTestHelper helper, int clientContainerId, int serverContainerId) {
        Endpoint clientEndpoint = createEndpoint(helper, clientContainerId);
        Endpoint serverEndpoint = createEndpoint(helper, serverContainerId);
        List<HostUiRequest> requests = new ArrayList<>();
        TerminalTracker clientTerminalTracker = new TerminalTracker();
        TerminalTracker serverTerminalTracker = new TerminalTracker();
        HostUiCoordinator clientCoordinator = HostUiCoordinator.createClient(
                clientEndpoint.extension,
                requests::add,
                clientTerminalTracker::invoke);
        HostUiCoordinator serverCoordinator = HostUiCoordinator.createServer(
                serverEndpoint.extension,
                serverTerminalTracker::invoke);
        return new CoordinatorPair(
                new CoordinatedEndpoint(clientEndpoint, clientCoordinator, clientTerminalTracker),
                new CoordinatedEndpoint(serverEndpoint, serverCoordinator, serverTerminalTracker),
                requests);
    }

    /**
     * Creates one real AE menu bridge and registers the four protocol identities in fixed order.
     */
    private static Endpoint createEndpoint(GameTestHelper helper, int containerId) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestMenu menu = new TestMenu(player, containerId);
        UIElement root = new UIElement();
        HostUiExtension extension = HostUiExtension.create(root);
        LinkedHashMap<HostUiKey, CountingProvider> providers = new LinkedHashMap<>();
        for (HostUiKey key : REGISTRATION_ORDER) {
            CountingProvider provider = new CountingProvider(key);
            providers.put(key, provider);
            extension.register(provider);
        }
        HostModularUI modularUI = extension.createModularUI(UI.of(root), player);
        AeMenuBridge.create(menu).mount(modularUI);
        return new Endpoint(extension, modularUI, Map.copyOf(providers));
    }

    /**
     * Completes one explicit open request and matching response on both endpoints.
     */
    private static void roundTripOpen(CoordinatorPair pair, HostUiKey key) {
        assertTrue(pair.client.coordinator.requestOpen(key), "open request must be emitted for " + key.id());
        roundTripPending(pair);
    }

    /**
     * Completes one explicit close request and matching response on both endpoints.
     */
    private static void roundTripClose(CoordinatorPair pair, HostUiKey key) {
        assertTrue(pair.client.coordinator.requestClose(key), "close request must be emitted for " + key.id());
        roundTripPending(pair);
    }

    /**
     * Delivers the sole pending request to the server and its resulting response back to the client.
     */
    private static void roundTripPending(CoordinatorPair pair) {
        assertEquals(1, pair.requests.size());
        HostUiRequest request = pair.requests.removeFirst();
        HostUiResponse response = pair.server.coordinator.handleRequest(request, true);
        assertTrue(response.accepted(), "ordered request must be accepted: " + request);
        assertTrue(pair.client.coordinator.handleResponse(response), "matching response must change client membership");
    }

    /**
     * Creates a namespaced identity reserved for coordinator GameTests.
     */
    private static HostUiKey key(String path) {
        return new HostUiKey(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "coordinator_test/" + path));
    }

    /**
     * Returns the sole active dynamic SyncValue id without accessing LDLib2 internals reflectively.
     */
    private static int soleSyncId(HostModularUI modularUI) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            modularUI.syncManager.writeInitialData(buffer);
            assertEquals(1, buffer.readVarInt());
            return buffer.readVarInt();
        } finally {
            buffer.release();
        }
    }

    /**
     * Delivers a parameterless RPC packet by its expected dynamic id.
     */
    private static void invokeRpc(HostModularUI modularUI, int rpcId) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(rpcId);
            buffer.writeBoolean(false);
            buffer.writeVarInt(0);
            modularUI.syncManager.handEvent(buffer);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    /**
     * Creates a registry-aware buffer for direct UISyncManager protocol verification.
     */
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    /**
     * Requires a true condition.
     */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Requires a false condition.
     */
    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Requires equal values.
     */
    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    /**
     * Requires equal long values.
     */
    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    /**
     * Requires distinct object identities.
     */
    private static void assertDifferent(Object first, Object second) {
        if (first == second) {
            throw new GameTestAssertException("Expected distinct objects");
        }
    }

    /**
     * Requires a nullable protocol field to be absent.
     */
    private static void assertNull(Object value) {
        if (value != null) {
            throw new GameTestAssertException("Expected null, got " + value);
        }
    }

    /**
     * Requires provider sealing or another coordinator invariant to fail fast.
     */
    private static void assertThrows(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    /**
     * Requires one exact unchecked failure to survive guarded structural cleanup.
     */
    private static void assertThrowsSame(Throwable expected, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error actual) {
            if (actual == expected) {
                return;
            }
            throw new GameTestAssertException("Expected exact failure " + expected + ", got " + actual);
        }
        throw new GameTestAssertException("Expected unchecked failure");
    }

    /**
     * Unmounted AE menu used to exercise the real LDLib2 bridge without production host state.
     */
    private static final class TestMenu extends AEBaseMenu {

        private TestMenu(Player player, int containerId) {
            super(null, containerId, player.getInventory(), null);
        }
    }

    /**
     * Provider that exposes fresh root identities and exactly-once cleanup counts.
     */
    private static final class CountingProvider implements HostSubUiProvider {

        private final HostUiKey key;
        private final List<HostSubUiRoot> roots = new ArrayList<>();
        private int createCount;
        private int closeCount;
        private int actionCount;
        private int rootCaptureActionCount;
        private int rootBubbleActionCount;
        private int rpcActionCount;
        private boolean failCreation;
        private HostSubUiContext latestContext;
        private UIElement latestAction;

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
            this.latestContext = context;
            context.onClose(() -> this.closeCount++);
            HostSubUiRoot root = context.createRoot();
            if (this.failCreation) {
                throw new IllegalStateException("Test provider creation failure for " + this.key.id());
            }
            root.addSyncValue(new SyncValue<>("coordinator_test_generation", Integer.class, this.createCount));
            root.addRPCEvent(RPCEventBuilder.simple(() -> this.rpcActionCount++));
            root.addEventListener(UIEvents.KEY_DOWN, event -> this.rootCaptureActionCount++, true);
            root.addEventListener(UIEvents.KEY_DOWN, event -> this.rootBubbleActionCount++);
            UIElement dragHandle = new UIElement();
            UIElement action = new UIElement();
            action.addEventListener(UIEvents.MOUSE_DOWN, event -> this.actionCount++);
            root.addChild(dragHandle);
            root.addChild(action);
            this.roots.add(root);
            this.latestAction = action;
            return new HostSubUi(root, dragHandle);
        }

        /**
         * Returns the fresh root created by the latest accepted open.
         */
        private HostSubUiRoot latestRoot() {
            return this.roots.getLast();
        }

        /**
         * Dispatches a real descendant interaction through LDLib2 capture and target phases.
         */
        private void dispatchAction() {
            UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
            event.target = this.latestAction;
            event.button = 0;
            UIEventDispatcher.dispatchEvent(event);
        }

        /**
         * Dispatches an event whose target is the dynamic root itself.
         */
        private void dispatchRootAction() {
            UIEvent event = UIEvent.create(UIEvents.KEY_DOWN);
            event.target = latestRoot();
            UIEventDispatcher.dispatchEvent(event);
        }
    }

    /**
     * One mounted extension and its deterministic provider set.
     */
    private record Endpoint(HostUiExtension extension,
                            HostModularUI modularUI,
                            Map<HostUiKey, CountingProvider> providers) {

        /**
         * Returns the counting provider for one known protocol identity.
         */
        private CountingProvider provider(HostUiKey key) {
            CountingProvider provider = this.providers.get(key);
            if (provider == null) {
                throw new GameTestAssertException("Missing provider " + key.id());
            }
            return provider;
        }
    }

    /**
     * Endpoint plus the role-specific coordinator attached to it.
     */
    private record CoordinatedEndpoint(Endpoint endpoint,
                                       HostUiCoordinator coordinator,
                                       TerminalTracker terminalTracker) {}

    /**
     * Counts owner-close notifications without changing the mock player's unrelated current menu.
     */
    private static final class TerminalTracker {

        private int invocationCount;

        private void invoke() {
            this.invocationCount++;
        }
    }

    /**
     * In-memory transport connecting two mounted endpoints one request and response at a time.
     */
    private record CoordinatorPair(CoordinatedEndpoint client,
                                   CoordinatedEndpoint server,
                                   List<HostUiRequest> requests) {

        /**
         * Releases both complete ModularUI trees after each protocol scenario.
         */
        private void close() {
            this.client.endpoint.modularUI.onRemoved();
            this.server.endpoint.modularUI.onRemoved();
        }
    }
}
