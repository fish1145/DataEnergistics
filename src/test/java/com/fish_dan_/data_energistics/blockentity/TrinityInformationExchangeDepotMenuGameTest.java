package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityInformationExchangeDepotBlockEntity.StorageMode;
import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenu;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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

import java.util.UUID;

/** Exercises the real information-exchange-depot menu route and its three persisted storage modes. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityInformationExchangeDepotMenuGameTest {

    private TrinityInformationExchangeDepotMenuGameTest() {}

    @TestHolder("trinity_information_exchange_depot_mode_menu_is_authoritative_and_persisted")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void modeMenuIsAuthoritativeAndPersisted(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> verifyModes(helper, fixture))
                .thenSucceed();
    }

    private static void verifyModes(GameTestHelper helper, TrinityDataCoreGameTestFixture fixture) {
        TrinityInformationExchangeDepotBlockEntity depot = fixture.accessHatches().stream()
                .filter(fixture.host()::isLeaseOwner)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Trinity fixture has no lease-owning exchange depot"));
        MenuTestServerPlayer player = new MenuTestServerPlayer(helper);
        moveNear(player, depot.getBlockPos());

        boolean opened = MenuOpener.open(
                DEMenus.TRINITY_INFORMATION_EXCHANGE_DEPOT.get(),
                player,
                MenuLocators.forBlockEntity(depot));
        helper.assertTrue(opened, "AE2 MenuOpener should resolve the placed information exchange depot");
        if (!(player.containerMenu instanceof TrinityInformationExchangeDepotMenu menu)) {
            throw new GameTestAssertException("Information exchange depot did not open its dedicated mode menu");
        }
        helper.assertValueEqual(menu.mode(), StorageMode.STORAGE, "New depots should begin in bidirectional mode");

        menu.receiveClientAction("set_information_exchange_mode", Integer.toString(StorageMode.INPUT.networkId()));
        helper.assertValueEqual(depot.informationExchangeMode(), StorageMode.INPUT,
                "A valid menu action should select input-only mode");
        helper.assertTrue(StorageMode.INPUT.allowsInsert(), "Input mode must permit network insertion");
        helper.assertFalse(StorageMode.INPUT.allowsExtract(), "Input mode must reject network extraction");
        helper.assertFalse(StorageMode.INPUT.exposesContents(), "Input mode must hide stored content from AE2");

        CompoundTag saved = depot.saveWithFullMetadata(helper.getLevel().registryAccess());
        TrinityInformationExchangeDepotBlockEntity loaded = new TrinityInformationExchangeDepotBlockEntity(
                depot.getBlockPos(),
                depot.getBlockState());
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.informationExchangeMode(), StorageMode.INPUT,
                "The selected information exchange mode must survive NBT reload");

        menu.receiveClientAction("set_information_exchange_mode", Integer.toString(StorageMode.OUTPUT.networkId()));
        helper.assertFalse(StorageMode.OUTPUT.allowsInsert(), "Output mode must reject network insertion");
        helper.assertTrue(StorageMode.OUTPUT.allowsExtract(), "Output mode must permit network extraction");
        helper.assertTrue(StorageMode.OUTPUT.exposesContents(), "Output mode must expose stored content to AE2");

        moveOutOfRange(player, depot.getBlockPos());
        menu.receiveClientAction("set_information_exchange_mode", Integer.toString(StorageMode.STORAGE.networkId()));
        helper.assertValueEqual(depot.informationExchangeMode(), StorageMode.OUTPUT,
                "An invalid physical route must not change the authoritative mode");
        player.closeContainer();
    }

    private static void moveNear(ServerPlayer player, BlockPos position) {
        player.setPos(position.getX() + 1.5D, position.getY() + 0.5D, position.getZ() + 0.5D);
    }

    private static void moveOutOfRange(ServerPlayer player, BlockPos position) {
        player.setPos(position.getX() + 9.5D, position.getY() + 0.5D, position.getZ() + 0.5D);
    }

    private static final class MenuTestServerPlayer extends ServerPlayer {

        private MenuTestServerPlayer(GameTestHelper helper) {
            super(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "trinity-exchange-menu"),
                    ClientInformation.createDefault());
            new DiscardingPacketListener(helper.getLevel().getServer(), this, getGameProfile());
        }
    }

    private static final class DiscardingPacketListener extends ServerGamePacketListenerImpl {

        private DiscardingPacketListener(MinecraftServer server, ServerPlayer player, GameProfile profile) {
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
