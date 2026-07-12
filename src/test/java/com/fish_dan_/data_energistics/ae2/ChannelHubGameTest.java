package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.pathing.ControllerState;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Exercises channel-hub Mixins against AE2's real server-side grid and pathing services.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ChannelHubGameTest {

    private ChannelHubGameTest() {}

    @TestHolder("channel_hub_aggregates_controller_faces")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 200)
    public static void aggregatesControllerFaces(GameTestHelper helper) {
        ChannelGridFixture fixture = ChannelGridFixture.create(helper);
        HubBranch hub = fixture.addHub(192);

        helper.startSequence()
                .thenWaitUntil(() -> fixture.awaitChannelCount(hub.devices(), 191))
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.usedChannels(hub.hub()), 192,
                            "One controller must supply 192 total channels through the hub");
                    helper.assertValueEqual(hub.upstream().getUsedChannels(), 1,
                            "The controller-facing hub connection must propagate one compressed channel");
                })
                .thenSucceed();
    }

    @TestHolder("channel_hubs_and_ordinary_branches_share_capacity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 200)
    public static void hubsAndOrdinaryBranchesShareCapacity(GameTestHelper helper) {
        ChannelGridFixture fixture = ChannelGridFixture.create(helper);
        HubBranch firstHub = fixture.addHub(80);
        HubBranch secondHub = fixture.addHub(80);
        List<IManagedGridNode> ordinaryDevices = fixture.addOrdinaryDenseBranch(40);
        List<IManagedGridNode> allDevices = new ArrayList<>();
        allDevices.addAll(firstHub.devices());
        allDevices.addAll(secondHub.devices());
        allDevices.addAll(ordinaryDevices);

        helper.startSequence()
                .thenWaitUntil(() -> fixture.awaitChannelCount(allDevices, 190))
                .thenExecute(() -> {
                    helper.assertValueEqual(firstHub.upstream().getUsedChannels(), 1,
                            "First hub must consume one controller-facing channel");
                    helper.assertValueEqual(secondHub.upstream().getUsedChannels(), 1,
                            "Second hub must consume one controller-facing channel");
                    helper.assertValueEqual(fixture.grid().getPathingService().getUsedChannels(), 192,
                            "Both hubs and the ordinary branch must share one 192-channel pool");
                })
                .thenSucceed();
    }

    @TestHolder("channel_hub_repaths_after_controller_changes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 300)
    public static void repathsAfterControllerChanges(GameTestHelper helper) {
        ChannelGridFixture fixture = ChannelGridFixture.create(helper);
        HubBranch hub = fixture.addHub(250);
        ControllerBlockEntity[] addedController = new ControllerBlockEntity[1];

        helper.startSequence()
                .thenWaitUntil(() -> fixture.awaitChannelCount(hub.devices(), 191))
                .thenExecute(() -> addedController[0] = fixture.addAdjacentController())
                .thenWaitUntil(() -> fixture.awaitChannelCount(hub.devices(), 250))
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.usedChannels(hub.hub()), 251,
                            "Two adjacent controllers must expose enough capacity for every target and the hub");
                    fixture.removeController(addedController[0]);
                })
                .thenWaitUntil(() -> fixture.awaitChannelCount(hub.devices(), 191))
                .thenExecute(() -> helper.assertValueEqual(hub.upstream().getUsedChannels(), 1,
                        "Dynamic repathing must preserve upstream channel compression"))
                .thenSucceed();
    }

    private static final class ChannelGridFixture implements AutoCloseable {

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<IManagedGridNode> managedNodes = new ArrayList<>();
        private final List<ControllerBlockEntity> controllers = new ArrayList<>();
        private final ControllerBlockEntity primaryController;
        private final GridPower power;
        private boolean closed;

        private ChannelGridFixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.primaryController = createController(helper.absolutePos(new BlockPos(2, 2, 2)));
            this.power = new GridPower(this.level);
            this.power.connect(requireNode(this.primaryController.getMainNode()));
            registerCleanup();
        }

        private static ChannelGridFixture create(GameTestHelper helper) {
            return new ChannelGridFixture(helper);
        }

        private HubBranch addHub(int deviceCount) {
            IManagedGridNode hub = createManagedNode(new TestHubHost(), GridFlags.REQUIRE_CHANNEL,
                    GridFlags.DENSE_CAPACITY);
            IGridConnection upstream = GridHelper.createConnection(
                    requireNode(this.primaryController.getMainNode()), requireNode(hub));
            List<IManagedGridNode> devices = addDevices(hub, deviceCount);
            return new HubBranch(hub, upstream, devices);
        }

        private List<IManagedGridNode> addOrdinaryDenseBranch(int deviceCount) {
            IManagedGridNode carrier = createManagedNode(new Object(), GridFlags.DENSE_CAPACITY);
            GridHelper.createConnection(requireNode(this.primaryController.getMainNode()), requireNode(carrier));
            return addDevices(carrier, deviceCount);
        }

        private List<IManagedGridNode> addDevices(IManagedGridNode parent, int count) {
            ArrayList<IManagedGridNode> devices = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                IManagedGridNode device = createManagedNode(new TestDevice(index), GridFlags.REQUIRE_CHANNEL);
                GridHelper.createConnection(requireNode(parent), requireNode(device));
                devices.add(device);
            }
            return List.copyOf(devices);
        }

        private IManagedGridNode createManagedNode(Object owner, GridFlags... flags) {
            IManagedGridNode managedNode = GridHelper.createManagedNode(owner, (ignoredOwner, node) -> {})
                    .setInWorldNode(false)
                    .setIdlePowerUsage(0.0D)
                    .setFlags(flags);
            managedNode.create(this.level, null);
            this.managedNodes.add(managedNode);
            return managedNode;
        }

        private ControllerBlockEntity addAdjacentController() {
            ControllerBlockEntity controller = createController(this.primaryController.getBlockPos().east());
            GridHelper.createConnection(
                    requireNode(this.primaryController.getMainNode()), requireNode(controller.getMainNode()));
            return controller;
        }

        private ControllerBlockEntity createController(BlockPos pos) {
            ControllerBlockEntity controller = new ControllerBlockEntity(
                    AEBlockEntities.CONTROLLER.get(), pos, AEBlocks.CONTROLLER.block().defaultBlockState());
            controller.setLevel(this.level);
            controller.getMainNode().create(this.level, pos);
            this.controllers.add(controller);
            return controller;
        }

        private void removeController(ControllerBlockEntity controller) {
            if (controller == null || !this.controllers.remove(controller)) {
                throw new IllegalArgumentException("Controller is not active in this fixture");
            }
            controller.getMainNode().destroy();
        }

        private void awaitChannelCount(List<IManagedGridNode> devices, int expected) {
            IGrid grid = grid();
            if (grid.getPathingService().getControllerState() != ControllerState.CONTROLLER_ONLINE) {
                throw new GameTestAssertException("Controller grid is not online yet");
            }
            if (grid.getPathingService().isNetworkBooting()) {
                throw new GameTestAssertException("Controller grid is still calculating channels");
            }
            int actual = countDevicesWithChannels(devices);
            if (actual != expected) {
                throw new GameTestAssertException("Expected " + expected + " channeled devices, found " + actual);
            }
        }

        private int countDevicesWithChannels(List<IManagedGridNode> devices) {
            int count = 0;
            for (IManagedGridNode device : devices) {
                if (usedChannels(device) > 0) {
                    count++;
                }
            }
            return count;
        }

        private int usedChannels(IManagedGridNode managedNode) {
            return requireNode(managedNode).getUsedChannels();
        }

        private IGrid grid() {
            IGrid grid = this.primaryController.getMainNode().getGrid();
            if (grid == null) {
                throw new GameTestAssertException("Controller grid has not been created");
            }
            return grid;
        }

        private void registerCleanup() {
            this.helper.testInfo.addListener(new GameTestListener() {

                @Override
                public void testStructureLoaded(GameTestInfo testInfo) {}

                @Override
                public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                    close();
                }

                @Override
                public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                    close();
                }

                @Override
                public void testAddedForRerun(GameTestInfo testInfo, GameTestInfo rerunTestInfo,
                                              GameTestRunner runner) {
                    close();
                }
            });
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            for (int index = this.managedNodes.size() - 1; index >= 0; index--) {
                this.managedNodes.get(index).destroy();
            }
            this.power.destroy();
            for (int index = this.controllers.size() - 1; index >= 0; index--) {
                this.controllers.get(index).getMainNode().destroy();
            }
        }
    }

    private static IGridNode requireNode(IManagedGridNode managedNode) {
        IGridNode node = managedNode.getNode();
        if (node == null) {
            throw new IllegalStateException("Managed test node was not created");
        }
        return node;
    }

    private record HubBranch(IManagedGridNode hub, IGridConnection upstream, List<IManagedGridNode> devices) {}

    private record TestDevice(int index) {}

    private static final class TestHubHost implements ChannelHubHost {}

    private static final class GridPower implements IAEPowerStorage {

        private static final IGridNodeListener<GridPower> NODE_LISTENER = (owner, node) -> {};

        private final IManagedGridNode managedNode;

        private GridPower(ServerLevel level) {
            this.managedNode = GridHelper.createManagedNode(this, NODE_LISTENER)
                    .setInWorldNode(false)
                    .setIdlePowerUsage(0.0D)
                    .addService(IAEPowerStorage.class, this);
            this.managedNode.create(level, null);
        }

        private void connect(IGridNode target) {
            GridHelper.createConnection(requireNode(this.managedNode), target);
        }

        private void destroy() {
            this.managedNode.destroy();
        }

        @Override
        public double injectAEPower(double amount, Actionable mode) {
            return 0.0D;
        }

        @Override
        public double getAEMaxPower() {
            return Long.MAX_VALUE / 10_000.0D;
        }

        @Override
        public double getAECurrentPower() {
            return Long.MAX_VALUE / 10_000.0D;
        }

        @Override
        public boolean isAEPublicPowerStorage() {
            return true;
        }

        @Override
        public AccessRestriction getPowerFlow() {
            return AccessRestriction.READ_WRITE;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            return amount;
        }
    }
}
