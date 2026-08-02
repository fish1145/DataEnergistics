package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection.Target;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection.TargetedMenu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import appeng.me.service.CraftingService;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.mojang.authlib.GameProfile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityCraftingStatusMenuGameTest {

    private TrinityCraftingStatusMenuGameTest() {}

    @TestHolder("trinity_cpu_status_cancel_falls_back_to_coordinator_until_route_is_stale")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void cancelFallsBackToCoordinatorUntilRouteIsStale(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> verifyCancellationAndStaleRoute(helper, fixture))
                .thenSucceed();
    }

    private static void verifyCancellationAndStaleRoute(GameTestHelper helper,
                                                        TrinityDataCoreGameTestFixture fixture) {
        TrinityDataCoreBlockEntity host = fixture.host();
        TrinityDataCoreCraftingRuntime runtime = host.getCraftingRuntime();
        IGrid grid = fixture.grid();
        TrinityDataCoreVirtualCpu coordinator = requireCoordinator(runtime);

        ICraftingSubmitResult result = coordinator.submitJob(
                grid,
                emptyJobPlan(),
                host.accessActionSource(),
                null);
        helper.assertTrue(result.successful(), "CPU status test job should be accepted: " + result.errorCode());
        TrinityDataCoreVirtualCpu worker = requireSingleBusyWorker(runtime);
        helper.assertTrue(
                grid.getCraftingService().getCpus().contains(worker),
                "AE2 should publish the busy worker before opening its status menu");

        TrinityAccessHatchBlockEntity hatch = fixture.accessHatches().stream()
                .filter(host::isLeaseOwner)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Trinity fixture has no lease-owning access hatch"));
        TrackingServerPlayer player = new TrackingServerPlayer(helper);
        Target target = new Target(host.getHostId(), runtime, worker, grid);
        CraftingStatusMenu menu = openSelectedMenu(player, hatch, target);
        TargetedMenu targetedMenu = requireTargetedMenu(menu);

        int workerSerial = menu.getSelectedCpuSerial();
        helper.assertTrue(workerSerial >= 0, "Opened CPU status menu should select the requested worker");
        helper.assertTrue(
                targetedMenu.dataEnergistics$getTrinityTarget() == target,
                "Opened CPU status menu should retain the exact validated target");
        menu.toggleScheduling();
        helper.assertTrue(worker.isJobSuspended(), "AE2 CPU scheduling control should suspend the Trinity worker");
        menu.broadcastChanges();
        menu.toggleScheduling();
        helper.assertFalse(worker.isJobSuspended(), "AE2 CPU scheduling control should resume the Trinity worker");
        CraftingStatusMenu.CraftingCpuListEntry coordinatorEntry = requireCpuEntry(menu, coordinator);
        helper.assertTrue(
                coordinatorEntry.serial() != workerSerial,
                "Requested worker and coordinator should have distinct AE menu serials");

        menu.cancelCrafting();
        helper.assertFalse(worker.isBusy(), "Cancel should stop the requested worker job");
        helper.assertFalse(
                runtime.publishedCpus().contains(worker),
                "Cancelled worker should leave the runtime publication immediately");
        helper.assertFalse(
                grid.getCraftingService().getCpus().contains(worker),
                "Cancelled worker should leave the AE2 CPU publication immediately");

        CraftingService craftingService = requireCraftingService(grid);
        runtime.tick(grid.getEnergyService(), craftingService, CraftingDispatchWindow.create());
        helper.assertFalse(
                runtime.hasCpu(worker),
                "The runtime tick should retire the cancelled worker object");

        menu.broadcastChanges();

        helper.assertTrue(player.containerMenu == menu, "Cancelled worker should not close the CPU status menu");
        helper.assertValueEqual(player.closeCalls(), 0, "Cancelled worker should not request a menu close");
        helper.assertTrue(
                targetedMenu.dataEnergistics$getTrinityTarget() == target,
                "Coordinator fallback should retain the original route target");
        helper.assertValueEqual(
                menu.getSelectedCpuSerial(),
                coordinatorEntry.serial(),
                "Cancelled worker should explicitly select CPU #0 from the same runtime");
        helper.assertTrue(
                menu.cpuList.cpus().stream().anyMatch(entry -> entry.serial() == coordinatorEntry.serial()),
                "CPU list should retain the selected CPU #0 entry");
        helper.assertTrue(
                menu.cpuList.cpus().stream().noneMatch(entry -> entry.serial() == workerSerial),
                "CPU list should not retain the retired worker entry");
        CraftingStatusMenu.CraftingCpuListEntry selectedCoordinator = requireCpuEntry(menu, coordinator);
        helper.assertTrue(
                selectedCoordinator.currentJob() == null,
                "Selected CPU #0 should remain idle after the worker cancellation");

        hatch.onChunkUnloaded();
        helper.assertFalse(
                hatch.isCurrentCpuStatusRoute(target),
                "Unloaded access hatch should invalidate the original Host/runtime/lease/Grid route");
        menu.broadcastChanges();

        helper.assertValueEqual(player.closeCalls(), 1, "Stale CPU status route should close exactly once");
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "Stale route should return to the inventory menu");
        helper.assertTrue(
                targetedMenu.dataEnergistics$getTrinityTarget() == null,
                "Closed stale menu should release its retained target");
        helper.assertFalse(menu.stillValid(player), "Closed stale CPU status menu should be invalid");
    }

    private static CraftingStatusMenu openSelectedMenu(TrackingServerPlayer player,
                                                       TrinityAccessHatchBlockEntity hatch,
                                                       Target target) {
        CraftingStatusMenu[] openedMenu = new CraftingStatusMenu[1];
        boolean opened = TrinityCraftingStatusSelection.open(player, hatch, target, () -> {
            CraftingStatusMenu menu = new CraftingStatusMenu(91, player.getInventory(), hatch);
            openedMenu[0] = menu;
            player.containerMenu = menu;
            return true;
        });
        if (!opened || openedMenu[0] == null) {
            throw new GameTestAssertException("Trinity CPU status menu did not open synchronously");
        }
        return openedMenu[0];
    }

    private static TargetedMenu requireTargetedMenu(CraftingStatusMenu menu) {
        if (menu instanceof TargetedMenu targetedMenu) {
            return targetedMenu;
        }
        throw new GameTestAssertException("CraftingStatusMenu is missing the Trinity target mixin");
    }

    private static TrinityDataCoreVirtualCpu requireCoordinator(TrinityDataCoreCraftingRuntime runtime) {
        return runtime.publishedCpus().stream()
                .filter(cpu -> cpu.number() == 0)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Trinity runtime has no published CPU #0"));
    }

    private static TrinityDataCoreVirtualCpu requireSingleBusyWorker(TrinityDataCoreCraftingRuntime runtime) {
        List<TrinityDataCoreVirtualCpu> workers = runtime.publishedCpus().stream()
                .filter(cpu -> cpu.number() != 0 && cpu.isBusy())
                .toList();
        if (workers.size() != 1) {
            throw new GameTestAssertException("Expected one busy Trinity worker, found " + workers.size());
        }
        return workers.getFirst();
    }

    private static CraftingStatusMenu.CraftingCpuListEntry requireCpuEntry(CraftingStatusMenu menu,
                                                                           TrinityDataCoreVirtualCpu cpu) {
        String expectedName = cpu.getName().getString();
        List<CraftingStatusMenu.CraftingCpuListEntry> entries = menu.cpuList.cpus().stream()
                .filter(entry -> entry.name() != null && entry.name().getString().equals(expectedName))
                .toList();
        if (entries.size() != 1) {
            throw new GameTestAssertException(
                    "Expected one CPU status entry named " + expectedName + ", found " + entries.size());
        }
        return entries.getFirst();
    }

    private static CraftingService requireCraftingService(IGrid grid) {
        if (grid.getCraftingService() instanceof CraftingService craftingService) {
            return craftingService;
        }
        throw new GameTestAssertException("Trinity CPU status test requires AE2 CraftingService");
    }

    private static CraftingPlan emptyJobPlan() {
        return new CraftingPlan(
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1L),
                1L,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
    }

    private static final class TrackingServerPlayer extends ServerPlayer {

        private int closeCalls;

        private TrackingServerPlayer(GameTestHelper helper) {
            super(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "trinity-cpu-status"),
                    ClientInformation.createDefault());
            new DiscardingPacketListener(
                    helper.getLevel().getServer(),
                    this,
                    getGameProfile());
        }

        @Override
        public void closeContainer() {
            this.closeCalls++;
            AbstractContainerMenu closingMenu = this.containerMenu;
            if (closingMenu != this.inventoryMenu) {
                closingMenu.removed(this);
                this.containerMenu = this.inventoryMenu;
            }
        }

        private int closeCalls() {
            return this.closeCalls;
        }
    }

    private static final class DiscardingPacketListener extends ServerGamePacketListenerImpl {

        private DiscardingPacketListener(MinecraftServer server,
                                         ServerPlayer player,
                                         GameProfile profile) {
            super(
                    server,
                    new Connection(PacketFlow.SERVERBOUND),
                    player,
                    new CommonListenerCookie(
                            profile,
                            0,
                            ClientInformation.createDefault(),
                            false,
                            ConnectionType.NEOFORGE));
        }

        @Override
        public void send(Packet<?> packet) {
            // The server-side menu behavior is under test; outbound client synchronization is intentionally discarded.
        }
    }
}
