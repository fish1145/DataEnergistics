package com.fish_dan_.data_energistics.orbital.console;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalControlConsoleGameTest {

    private static final BlockPos FIRST_CONSOLE = new BlockPos(1, 2, 1);
    private static final BlockPos SECOND_CONSOLE = new BlockPos(2, 2, 1);
    private static final BlockPos DELEGATED_CONSOLE = new BlockPos(3, 2, 1);
    private static final List<BlockPos> DIMENSION_LIMIT_CONSOLES = List.of(
            new BlockPos(1, 2, 1),
            new BlockPos(2, 2, 1),
            new BlockPos(3, 2, 1),
            new BlockPos(4, 2, 1),
            new BlockPos(1, 2, 2),
            new BlockPos(2, 2, 2),
            new BlockPos(3, 2, 2),
            new BlockPos(4, 2, 2));
    private static final BlockPos REJECTED_CONSOLE = new BlockPos(1, 2, 3);

    private OrbitalControlConsoleGameTest() {}

    @TestHolder("orbital_control_console_placement_binds_owned_weapon_and_removal_releases_endpoint")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void placementBindsOwnedWeaponAndRemovalReleasesEndpoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(level.getServer());
        ServerPlayer owner = createPlayer(level, "console-owner");
        ServerPlayer delegatedPlayer = createPlayer(level, "console-guest");

        placeConsole(helper, FIRST_CONSOLE, owner);
        OrbitalEndpointLocation firstLocation = location(helper, FIRST_CONSOLE);
        UUID ownerWeaponId = data.weaponAt(firstLocation).orElseThrow().weaponId();
        helper.assertValueEqual(
                data.ownedBy(owner.getUUID()).orElseThrow().weaponId(),
                ownerWeaponId,
                "The first placed console must bind the weapon owned by its placing player");
        helper.assertValueEqual(
                data.weaponAt(firstLocation).orElseThrow().weaponId(),
                ownerWeaponId,
                "The placed console location must route back to its bound weapon");

        placeConsole(helper, SECOND_CONSOLE, owner);
        UUID secondWeaponId = data.weaponAt(location(helper, SECOND_CONSOLE)).orElseThrow().weaponId();
        helper.assertValueEqual(
                secondWeaponId,
                ownerWeaponId,
                "A second console placed by the same player must reuse the stable weapon identity");

        UUID sharedOwnerId = UUID.randomUUID();
        OrbitalWeaponRecord sharedWeapon = data.createForOwner(level.getServer(), sharedOwnerId);
        data.authorize(
                level.getServer(),
                sharedWeapon.weaponId(),
                sharedOwnerId,
                delegatedPlayer.getUUID(),
                OrbitalAccessRole.OPERATOR);
        placeConsole(helper, DELEGATED_CONSOLE, delegatedPlayer);
        UUID delegatedOwnedWeaponId = data.weaponAt(location(helper, DELEGATED_CONSOLE)).orElseThrow().weaponId();
        helper.assertFalse(
                delegatedOwnedWeaponId.equals(sharedWeapon.weaponId()),
                "Delegated access must not cause a newly placed console to bind another player's weapon");
        helper.assertTrue(
                data.accessibleTo(delegatedPlayer.getUUID()).stream()
                        .map(OrbitalWeaponRecord::weaponId)
                        .toList()
                        .containsAll(List.of(sharedWeapon.weaponId(), delegatedOwnedWeaponId)),
                "After placement, the player must retain both delegated access and their independently owned weapon");

        BlockPos firstAbsolutePos = helper.absolutePos(FIRST_CONSOLE);
        helper.assertTrue(level.destroyBlock(firstAbsolutePos, false), "The first console must be removable from the world");
        helper.assertTrue(
                data.weaponAt(firstLocation).isEmpty(),
                "Destroying a console must release its endpoint from the weapon");
        helper.assertValueEqual(
                data.weaponAt(location(helper, SECOND_CONSOLE)).orElseThrow().weaponId(),
                ownerWeaponId,
                "Destroying one console must leave another console for the same weapon bound");
        helper.succeed();
    }

    @TestHolder("orbital_control_console_dimension_limit_rejects_then_releases_capacity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void dimensionLimitRejectsThenReleasesCapacity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(level.getServer());
        ServerPlayer owner = createPlayer(level, "console-limit");

        UUID weaponId = null;
        for (BlockPos consolePos : DIMENSION_LIMIT_CONSOLES) {
            placeConsole(helper, consolePos, owner);
            UUID boundWeaponId = data.weaponAt(location(helper, consolePos)).orElseThrow().weaponId();
            if (weaponId == null) {
                weaponId = boundWeaponId;
            } else {
                helper.assertValueEqual(
                        boundWeaponId,
                        weaponId,
                        "Every accepted console below the dimension limit must bind the same owned weapon");
            }
        }

        placeConsole(helper, REJECTED_CONSOLE, owner);
        helper.assertTrue(
                data.weaponAt(location(helper, REJECTED_CONSOLE)).isEmpty(),
                "The ninth endpoint in one dimension must be rejected before it mutates weapon state");

        helper.assertTrue(
                level.destroyBlock(helper.absolutePos(DIMENSION_LIMIT_CONSOLES.getFirst()), false),
                "An accepted endpoint must be removable to release capacity");
        helper.assertTrue(
                level.destroyBlock(helper.absolutePos(REJECTED_CONSOLE), false),
                "The rejected console block must remain removable");
        placeConsole(helper, REJECTED_CONSOLE, owner);
        helper.assertValueEqual(
                data.weaponAt(location(helper, REJECTED_CONSOLE)).orElseThrow().weaponId(),
                weaponId,
                "After one endpoint is removed, a replacement console must bind the same weapon");
        helper.succeed();
    }

    private static void placeConsole(GameTestHelper helper, BlockPos relativePos, ServerPlayer placer) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockState state = DEBlocks.ORBITAL_CONTROL_CONSOLE.get().defaultBlockState();
        if (!level.setBlock(absolutePos, state, Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place orbital control console at " + absolutePos);
        }
        state.getBlock().setPlacedBy(level, absolutePos, state, placer, ItemStack.EMPTY);
    }

    private static ServerPlayer createPlayer(ServerLevel level, String name) {
        return new TestServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
    }

    private static OrbitalEndpointLocation location(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        return new OrbitalEndpointLocation(level.dimension().location(), helper.absolutePos(relativePos));
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
