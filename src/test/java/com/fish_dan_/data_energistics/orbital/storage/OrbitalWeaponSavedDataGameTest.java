package com.fish_dan_.data_energistics.orbital.storage;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycle;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalWeaponSavedDataGameTest {

    private OrbitalWeaponSavedDataGameTest() {}

    @TestHolder("orbital_weapon_ownership_routes_owned_and_delegated_access")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void routesOwnedAndDelegatedAccess(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(server);
        UUID ownerId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID observerId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();

        OrbitalWeaponRecord sharedWeapon = data.createForOwner(server, ownerId);
        OrbitalWeaponRecord repeatedCreation = data.createForOwner(server, ownerId);
        helper.assertValueEqual(
                repeatedCreation.weaponId(),
                sharedWeapon.weaponId(),
                "Repeated placement by the same owner must reuse their weapon");

        data.authorize(
                server,
                sharedWeapon.weaponId(),
                ownerId,
                operatorId,
                OrbitalAccessRole.OPERATOR);
        data.authorize(
                server,
                sharedWeapon.weaponId(),
                ownerId,
                observerId,
                OrbitalAccessRole.OBSERVER);
        OrbitalWeaponRecord sharedWeaponWithAccess = data.find(sharedWeapon.weaponId()).orElseThrow();
        helper.assertValueEqual(
                data.accessibleTo(operatorId),
                List.of(sharedWeaponWithAccess),
                "An authorized player must be routed to the shared weapon");

        OrbitalWeaponRecord operatorOwnedWeapon = data.createForOwner(server, operatorId);
        helper.assertFalse(
                operatorOwnedWeapon.weaponId().equals(sharedWeapon.weaponId()),
                "Delegated access must not prevent a player from creating an independent owned weapon");
        helper.assertTrue(
                data.accessibleTo(operatorId).containsAll(List.of(sharedWeaponWithAccess, operatorOwnedWeapon)),
                "The control terminal must list both owned and delegated weapons");

        assertUnauthorizedRoleChangeRejected(
                helper,
                data,
                server,
                sharedWeapon.weaponId(),
                outsiderId,
                observerId);

        data.revoke(server, sharedWeapon.weaponId(), ownerId, operatorId);
        helper.assertValueEqual(
                data.accessibleTo(operatorId),
                List.of(operatorOwnedWeapon),
                "Revoking delegated access must leave the player's own weapon accessible");
        helper.assertValueEqual(
                data.ownedBy(ownerId).orElseThrow().weaponId(),
                sharedWeapon.weaponId(),
                "Authorization changes must not alter the owner index");
        helper.succeed();
    }

    @TestHolder("orbital_weapon_lifecycle_round_trips_and_migrates_legacy_records")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void roundTripsLifecycleAndMigratesLegacyRecords(GameTestHelper helper) {
        OrbitalWeaponLifecycle grace = OrbitalWeaponLifecycle.reserveGrace(37);
        OrbitalWeaponRecord source = new OrbitalWeaponRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Map.of(),
                Map.of(),
                new OrbitalEnergyReserve(12_345L, 67_890L),
                grace);

        CompoundTag saved = OrbitalWeaponNbtCodec.save(new CompoundTag(), List.of(source));
        OrbitalWeaponRecord restored = OrbitalWeaponNbtCodec.load(saved).getFirst();
        helper.assertValueEqual(
                restored.lifecycle(),
                grace,
                "Saving and loading must preserve the active reserve-grace countdown");
        helper.assertValueEqual(
                restored.reserve(),
                source.reserve(),
                "Adding lifecycle persistence must not alter the independent reserve values");

        saved.putInt("schema_version", 2);
        OrbitalWeaponRecord migrated = OrbitalWeaponNbtCodec.load(saved).getFirst();
        helper.assertValueEqual(
                migrated.lifecycle().state(),
                OrbitalWeaponLifecycleState.DORMANT,
                "A pre-lifecycle weapon record must migrate to the safe dormant state");
        helper.succeed();
    }

    private static void assertUnauthorizedRoleChangeRejected(
                                                             GameTestHelper helper,
                                                             OrbitalWeaponSavedData data,
                                                             MinecraftServer server,
                                                             UUID weaponId,
                                                             UUID actorId,
                                                             UUID playerId) {
        try {
            data.authorize(server, weaponId, actorId, playerId, OrbitalAccessRole.OPERATOR);
            helper.fail("An unauthorized player changed an orbital weapon role");
        } catch (SecurityException expected) {
            helper.assertValueEqual(
                    data.find(weaponId).orElseThrow().delegatedRoles().get(playerId),
                    OrbitalAccessRole.OBSERVER,
                    "A rejected role change must leave the authorization state intact");
        }
    }
}
