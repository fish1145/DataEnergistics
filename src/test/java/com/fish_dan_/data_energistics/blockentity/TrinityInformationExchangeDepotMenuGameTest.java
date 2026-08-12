package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternTerminalPartition;
import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenu;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.mojang.authlib.GameProfile;

import java.util.List;
import java.util.UUID;

/**
 * Exercises the real access-hatch menu route and its identity-scoped terminal partition boundary.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityInformationExchangeDepotMenuGameTest {

    private TrinityInformationExchangeDepotMenuGameTest() {}

    @TestHolder("trinity_information_exchange_depot_menu_opens_with_identity_scoped_partitions")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void opensWithIdentityScopedPartitions(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> verifyMenuRoute(helper, fixture))
                .thenSucceed();
    }

    private static void verifyMenuRoute(GameTestHelper helper, TrinityDataCoreGameTestFixture fixture) {
        TrinityInformationExchangeDepotBlockEntity hatch = fixture.accessHatches().stream()
                .filter(fixture.host()::isLeaseOwner)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Trinity fixture has no lease-owning access hatch"));
        hatch.refreshTrinityTerminalLayout();
        MenuTestServerPlayer player = new MenuTestServerPlayer(helper);
        moveNear(player, hatch.getBlockPos());

        boolean opened = MenuOpener.open(
                DEMenus.TRINITY_INFORMATION_EXCHANGE_DEPOT.get(),
                player,
                MenuLocators.forBlockEntity(hatch));
        helper.assertTrue(opened, "AE2 MenuOpener should resolve the placed Trinity access hatch");
        if (!(player.containerMenu instanceof TrinityInformationExchangeDepotMenu menu)) {
            throw new GameTestAssertException("Trinity access hatch did not open its registered menu");
        }
        helper.assertTrue(menu.stillValid(player), "A nearby player should retain the access-hatch menu route");

        List<TrinityPatternTerminalPartition> mounted = hatch.terminalPartitions();
        helper.assertFalse(mounted.isEmpty(), "An online fixture should mount terminal partitions on its access hatch");
        for (TrinityPatternTerminalPartition partition : mounted) {
            helper.assertTrue(
                    menu.isManagedPatternContainer(partition),
                    "The menu should accept every partition mounted by its exact access hatch");
        }

        List<TrinityPatternTerminalPartition> detached = TrinityPatternTerminalPartition.createLayout(
                fixture.host().getPatternCatalog(),
                mounted.getFirst().getTerminalGroup());
        helper.assertValueEqual(
                detached.size(),
                mounted.size(),
                "Recreating the same catalog layout should preserve its partition count");
        for (int index = 0; index < mounted.size(); index++) {
            TrinityPatternTerminalPartition mountedPartition = mounted.get(index);
            TrinityPatternTerminalPartition detachedPartition = detached.get(index);
            helper.assertTrue(
                    mountedPartition != detachedPartition && mountedPartition.hasSameLayout(detachedPartition),
                    "Recreated partitions should retain metadata while having distinct object identity");
            helper.assertFalse(
                    menu.isManagedPatternContainer(detachedPartition),
                    "The menu must reject an equal-layout partition not mounted by its exact hatch");
        }

        verifyRefundSequences(helper, menu);
        moveOutOfRange(player, hatch.getBlockPos());
        helper.assertFalse(menu.stillValid(player), "Moving beyond eight blocks should invalidate the access-hatch menu");
        long rejectedRevision = menu.refundPatternsRevision;
        menu.receiveClientAction("dataEnergistics$refundPatterns", "2");
        helper.assertValueEqual(
                menu.refundPatternsRevision,
                rejectedRevision + 1L,
                "A fresh request on an invalid menu should publish one rejection");
        helper.assertValueEqual(
                menu.refundPatternsStatus,
                TrinityHostedActionStatus.REJECTED.networkId(),
                "An out-of-range refund must be rejected before reaching the host");
        player.closeContainer();
    }

    private static void verifyRefundSequences(GameTestHelper helper, TrinityInformationExchangeDepotMenu menu) {
        long patternsRevision = menu.refundPatternsRevision;
        menu.receiveClientAction("dataEnergistics$refundPatterns", "1");
        helper.assertValueEqual(
                menu.refundPatternsRevision,
                patternsRevision + 1L,
                "The first installed-pattern refund sequence should execute exactly once");

        menu.receiveClientAction("dataEnergistics$refundPatterns", "1");
        menu.receiveClientAction("dataEnergistics$refundPatterns", "0");
        menu.receiveClientAction(
                "dataEnergistics$refundPatterns",
                Long.toString(TrinityHostedActionTicket.MAX_SEQUENCE + 1L));
        helper.assertValueEqual(
                menu.refundPatternsRevision,
                patternsRevision + 1L,
                "Duplicate and out-of-range installed-pattern sequences must not execute");

        long retainedRevision = menu.refundRetainedItemsRevision;
        menu.receiveClientAction("dataEnergistics$refundRetainedItems", "1");
        helper.assertValueEqual(
                menu.refundRetainedItemsRevision,
                retainedRevision + 1L,
                "Retained-item refunds should maintain an independent sequence");
        helper.assertValueEqual(
                menu.refundPatternsRevision,
                patternsRevision + 1L,
                "A retained-item refund must not advance the installed-pattern result");
    }

    private static void moveNear(ServerPlayer player, BlockPos hatchPos) {
        player.setPos(hatchPos.getX() + 1.5D, hatchPos.getY() + 0.5D, hatchPos.getZ() + 0.5D);
    }

    private static void moveOutOfRange(ServerPlayer player, BlockPos hatchPos) {
        player.setPos(hatchPos.getX() + 9.5D, hatchPos.getY() + 0.5D, hatchPos.getZ() + 0.5D);
    }

    /**
     * Server-side menu player with a packet sink so the real open-menu path can initialize normally in GameTest.
     */
    private static final class MenuTestServerPlayer extends ServerPlayer {

        private MenuTestServerPlayer(GameTestHelper helper) {
            super(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "trinity-access-menu"),
                    ClientInformation.createDefault());
            new DiscardingPacketListener(
                    helper.getLevel().getServer(),
                    this,
                    getGameProfile());
        }
    }

    /**
     * Discards client synchronization while retaining the normal {@link ServerPlayer#openMenu} lifecycle.
     */
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
            // The server-side menu route is under test; outbound client synchronization is intentionally discarded.
        }
    }
}
