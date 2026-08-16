package com.fish_dan_.data_energistics.orbital.model;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalAccessPolicyGameTest {

    private OrbitalAccessPolicyGameTest() {}

    @TestHolder("orbital_access_policy_enforces_roles_and_snapshots_exemptions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void enforcesRolesAndSnapshotsExemptions(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID observerId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        Map<UUID, OrbitalAccessRole> delegatedRoles = new HashMap<>();
        delegatedRoles.put(operatorId, OrbitalAccessRole.OPERATOR);
        delegatedRoles.put(observerId, OrbitalAccessRole.OBSERVER);

        helper.assertTrue(
                OrbitalAccessPolicy.canPerform(
                        ownerId,
                        delegatedRoles,
                        ownerId,
                        OrbitalWeaponAction.TRANSFER_OWNERSHIP),
                "The owner must retain ownership-management actions");
        helper.assertTrue(
                OrbitalAccessPolicy.canPerform(
                        ownerId,
                        delegatedRoles,
                        operatorId,
                        OrbitalWeaponAction.FIRE),
                "An operator must be able to fire the weapon");
        helper.assertFalse(
                OrbitalAccessPolicy.canPerform(
                        ownerId,
                        delegatedRoles,
                        operatorId,
                        OrbitalWeaponAction.MANAGE_AUTHORIZATIONS),
                "An operator must not change the authorization list");
        helper.assertTrue(
                OrbitalAccessPolicy.canPerform(
                        ownerId,
                        delegatedRoles,
                        observerId,
                        OrbitalWeaponAction.VIEW_STATUS),
                "An observer must retain read-only access");
        helper.assertFalse(
                OrbitalAccessPolicy.canPerform(
                        ownerId,
                        delegatedRoles,
                        observerId,
                        OrbitalWeaponAction.AIM),
                "An observer must not aim the weapon");
        helper.assertFalse(
                OrbitalAccessPolicy.canPerform(
                        ownerId,
                        delegatedRoles,
                        outsiderId,
                        OrbitalWeaponAction.VIEW_STATUS),
                "An unauthorized player must not open weapon status");

        Set<UUID> exemptionSnapshot = OrbitalAccessPolicy.damageExemptionSnapshot(ownerId, delegatedRoles);
        delegatedRoles.clear();
        helper.assertValueEqual(
                exemptionSnapshot,
                Set.of(ownerId, operatorId, observerId),
                "Attack confirmation must freeze all authorized players into one damage-exemption snapshot");
        helper.succeed();
    }
}
