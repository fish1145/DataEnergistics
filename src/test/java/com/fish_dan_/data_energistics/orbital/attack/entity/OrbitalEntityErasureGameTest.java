package com.fish_dan_.data_energistics.orbital.attack.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalKineticStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalEntityErasureGameTest {

    private static final BlockPos TARGET = new BlockPos(10, 10, 10);

    private OrbitalEntityErasureGameTest() {}

    @TestHolder("orbital_kinetic_impact_erases_immune_entities_and_items_without_harming_exemptions")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void kineticImpactErasesImmuneEntitiesAndItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        Zombie victim = spawnDamageImmuneZombie(helper, TARGET.offset(2, 0, 0));
        Zombie exempt = spawnDamageImmuneZombie(helper, TARGET.offset(0, 0, 2));
        Zombie outside = spawnDamageImmuneZombie(helper, TARGET.offset(8, 0, 0));
        ItemEntity item = new ItemEntity(level, target.getX() + 3.5, target.getY(), target.getZ() + 0.5,
                new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(item), "The non-living target must enter the server world");

        OrbitalKineticStrike.eraseImpactEntities(level, target,
                new OrbitalAttackGeometry.Kinetic(1, 1, 1, 1, 6), Set.of(exempt.getUUID()));
        helper.assertTrue(victim.isRemoved(), "Impact must immediately erase an invulnerable high-health target with a totem");
        helper.assertTrue(item.isRemoved(), "Impact must erase non-living targets in the same volume");
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(level.getEntity(victim.getUUID()) == null, "The erased mob must leave the world's entity index");
            helper.assertTrue(level.getEntity(item.getUUID()) == null, "The erased item must leave the world's entity index");
            helper.assertTrue(exempt.isAlive(), "Frozen UUID exemptions must survive the same impact");
            helper.assertTrue(outside.isAlive(), "An entity outside the impact sphere must survive");
        }).thenSucceed();
    }

    @TestHolder("orbital_beam_erases_on_first_body_contact_without_reaching_below_the_beam")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void beamErasesOnFirstBodyContact(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        Zombie victim = spawnDamageImmuneZombie(helper, TARGET);
        Zombie exempt = spawnDamageImmuneZombie(helper, TARGET);
        ItemEntity lowerItem = new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.1, target.getZ() + 0.5,
                new ItemStack(Items.DIAMOND));
        lowerItem.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(lowerItem), "The lower target must enter the server world");
        var geometry = new OrbitalAttackGeometry.DirectedEnergy(1, OrbitalDirectedEnergyDepth.DEPTH_32, 4);
        long cursor = level.getMaxBuildHeight() - 1L - (target.getY() + 2L);
        Set<UUID> exemptions = Set.of(exempt.getUUID());

        var above = OrbitalDirectedEnergyStrike.applyBudget(level, target, geometry, cursor, exemptions, 1,
                chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
        helper.assertTrue(victim.isAlive(), "A beam cell above the target must not erase it early");
        var contact = OrbitalDirectedEnergyStrike.applyBudget(level, target, geometry, above.nextCursor(), exemptions, 1,
                chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
        helper.assertTrue(victim.isRemoved(), "Touching the head must erase the target without waiting to reach its feet");
        helper.assertTrue(lowerItem.isAlive(), "An item below the current beam cell must remain until the beam reaches it");
        OrbitalDirectedEnergyStrike.applyBudget(level, target, geometry, contact.nextCursor(), exemptions, 1,
                chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
        helper.assertTrue(lowerItem.isRemoved(), "The next beam cell must erase the lower non-living target");
        helper.assertTrue(exempt.isAlive(), "The overlapping exempt target must survive the complete contact sequence");
        helper.succeed();
    }

    @TestHolder("orbital_player_erasure_bypasses_damage_events_and_keeps_death_and_respawn")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_player_erasure", timeoutTicks = 100)
    public static void playerErasureKeepsDeathAndRespawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExtendedGameTestHelper playerHelper = new ExtendedGameTestHelper(helper.testInfo);
        GameTestPlayer victim = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer exempt = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer creative = playerHelper.makeTickingMockServerPlayerInLevel(GameType.CREATIVE);
        GameTestPlayer spectator = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SPECTATOR);
        Vec3 center = Vec3.atBottomCenterOf(helper.absolutePos(TARGET));
        for (GameTestPlayer player : new GameTestPlayer[] { victim, exempt, creative, spectator }) {
            player.moveTo(center);
            player.setNoGravity(true);
        }
        helper.assertFalse(victim.hasPermissions(2), "The survival victim must not be globally exempt as an administrator");
        victim.setInvulnerable(true);
        victim.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        victim.subscribe((LivingIncomingDamageEvent event) -> {
            if (event.getEntity() == victim) {
                event.setCanceled(true);
            }
        });

        OrbitalKineticStrike.eraseImpactEntities(level, helper.absolutePos(TARGET),
                new OrbitalAttackGeometry.Kinetic(1, 1, 1, 1, 6), Set.of(exempt.getUUID()));
        helper.assertFalse(victim.isAlive(), "Damage cancellation and a totem must not prevent orbital player erasure");
        helper.assertFalse(victim.isRemoved(), "The connected player must retain its entity until normal respawn");
        helper.assertTrue(victim.getOutboundPackets(ClientboundPlayerCombatKillPacket.class).findAny().isPresent(),
                "The client must receive the real death-screen notification");
        helper.assertTrue(exempt.isAlive() && creative.isAlive() && spectator.isAlive(),
                "Authorized, creative and spectator players must remain protected");
        victim.connection.handleClientCommand(new ServerboundClientCommandPacket(Action.PERFORM_RESPAWN));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            ServerPlayer respawned = Objects.requireNonNull(level.getServer().getPlayerList().getPlayer(victim.getUUID()));
            helper.assertTrue(respawned != victim && respawned.isAlive(), "The normal client respawn request must create a living player");
            helper.assertTrue(respawned.connection == victim.connection, "Respawn must preserve the player's connection");
        }).thenSucceed();
    }

    private static Zombie spawnDamageImmuneZombie(GameTestHelper helper, BlockPos relative) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, relative);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setInvulnerable(true);
        Objects.requireNonNull(zombie.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(1024);
        zombie.setHealth(1024);
        zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        return zombie;
    }
}
