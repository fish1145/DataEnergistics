package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.network.orbital.control.OrbitalControlHudSnapshotPayload;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlIntent;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalHudSnapshot;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalControlTerminalGameTest {

    private OrbitalControlTerminalGameTest() {}

    @TestHolder("orbital_control_terminal_cycles_persisted_server_selection")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cyclesPersistedServerSelection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(server);
        ServerPlayer player = createPlayer(level, "selection-player");
        OrbitalWeaponRecord owned = data.createForOwner(server, player.getUUID());
        OrbitalWeaponRecord delegated = data.createForOwner(server, UUID.randomUUID());
        data.authorize(server, delegated.weaponId(), delegated.ownerId(), player.getUUID(), OrbitalAccessRole.OPERATOR);

        OrbitalControlTerminalSnapshot initial = OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
        List<UUID> accessible = initial.weapons().stream().map(OrbitalControlTerminalSnapshot.WeaponEntry::weaponId).toList();
        helper.assertValueEqual(accessible.size(), 2, "The selector must expose both owned and delegated weapons");
        helper.assertValueEqual(initial.selectedWeaponId(), accessible.getFirst(), "Selection must start at stable first UUID");

        helper.assertTrue(
                OrbitalControlActionDispatcher.cycleWeapon(player, true).isPresent(),
                "The server-side next-weapon action must select an accessible UUID");
        OrbitalControlTerminalSnapshot next = OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
        helper.assertValueEqual(next.selectedWeaponId(), accessible.get(1), "Next must select the second accessible weapon");

        data.revoke(server, delegated.weaponId(), delegated.ownerId(), player.getUUID());
        OrbitalControlTerminalSnapshot afterRevoke = OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
        helper.assertValueEqual(afterRevoke.weapons().size(), 1, "Revoking access must remove the selected weapon");
        helper.assertValueEqual(afterRevoke.selectedWeaponId(), owned.weaponId(), "Selection must fall back to the owned weapon");
        helper.succeed();
    }

    @TestHolder("orbital_control_terminal_direct_selection_rechecks_access")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void directSelectionRejectsUnknownAndRevokedWeapons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(server);
        ServerPlayer player = createPlayer(level, "direct-selection");
        OrbitalWeaponRecord owned = data.createForOwner(server, player.getUUID());
        OrbitalWeaponRecord delegated = data.createForOwner(server, UUID.randomUUID());
        data.authorize(server, delegated.weaponId(), delegated.ownerId(), player.getUUID(), OrbitalAccessRole.OBSERVER);
        helper.assertTrue(OrbitalControlActionDispatcher.selectWeapon(player, delegated.weaponId()),
                "Accessible observer weapons must be directly selectable for inspection");
        helper.assertValueEqual(data.preferredWeaponId(player.getUUID()).orElseThrow(), delegated.weaponId(),
                "Direct selection must remember the requested UUID");
        helper.assertTrue(!OrbitalControlActionDispatcher.selectWeapon(player, UUID.randomUUID()),
                "An arbitrary client UUID must not become a selection");
        helper.assertValueEqual(data.preferredWeaponId(player.getUUID()).orElseThrow(), delegated.weaponId(),
                "Rejected IDs must leave the previous selection intact");
        data.revoke(server, delegated.weaponId(), delegated.ownerId(), player.getUUID());
        helper.assertTrue(!OrbitalControlActionDispatcher.selectWeapon(player, delegated.weaponId()),
                "Access revoked after a list snapshot must be checked again");
        helper.assertValueEqual(data.preferredWeaponId(player.getUUID()).orElseThrow(), owned.weaponId(),
                "The remembered selection must resolve to a remaining accessible weapon");
        helper.succeed();
    }

    @TestHolder("orbital_control_terminal_typed_hud_and_selection_round_trip")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void typedHudAndSelectionRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = createPlayer(level, "hud-packet");
        OrbitalWeaponSavedData.get(level.getServer()).createForOwner(level.getServer(), player.getUUID());
        var weapon = OrbitalControlTerminalSnapshot.capture(level.getServer(), player.getUUID()).selectedWeapon().orElseThrow();
        var hud = new OrbitalControlHudSnapshotPayload(42L, true, new OrbitalHudSnapshot(weapon));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            OrbitalControlHudSnapshotPayload.STREAM_CODEC.encode(buffer, hud);
            helper.assertValueEqual(OrbitalControlHudSnapshotPayload.STREAM_CODEC.decode(buffer), hud,
                    "HUD fields must survive serialization without presentation strings");
            OrbitalHudSnapshot.STREAM_CODEC.encode(buffer, OrbitalHudSnapshot.EMPTY);
            helper.assertValueEqual(OrbitalHudSnapshot.STREAM_CODEC.decode(buffer), OrbitalHudSnapshot.EMPTY,
                    "Hidden HUD snapshots must round-trip without a weapon");
            OrbitalControlIntent select = new OrbitalControlIntent.SelectWeapon(weapon.weaponId());
            OrbitalControlIntent.STREAM_CODEC.encode(buffer, select);
            helper.assertValueEqual(OrbitalControlIntent.STREAM_CODEC.decode(buffer), select,
                    "Direct weapon selection must preserve its requested UUID");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static ServerPlayer createPlayer(ServerLevel level, String name) {
        return new TestServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
    }

    private static final class TestServerPlayer extends ServerPlayer {

        private TestServerPlayer(
                                 MinecraftServer server,
                                 ServerLevel level,
                                 GameProfile profile,
                                 ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }

        @Override
        public void displayClientMessage(Component chatComponent, boolean actionBar) {}
    }
}
