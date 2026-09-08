package com.fish_dan_.data_energistics.orbital.attack.entity.player;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.entity.OrbitalEntityErasure;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureAttachments;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureContext;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureState.Phase;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureOutcome;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import com.mojang.authlib.GameProfile;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerErasureLifecycleGameTest {

    private PlayerErasureLifecycleGameTest() {}

    @TestHolder("orbital_erasure_commits_death_once_and_deduplicates_the_respawned_life")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_erasure_lifecycle", timeoutTicks = 100)
    public static void deathCancellationAndRespawnAreScopedToOneLife(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameTestPlayer victim = player(helper);
        GameTestPlayer unrelated = player(helper);
        OrbitalErasureStrike strike = strike();
        AtomicInteger victimEvents = new AtomicInteger();
        AtomicInteger unrelatedEvents = new AtomicInteger();
        unrelated.subscribe((LivingDeathEvent event) -> {
            if (event.getEntity() == unrelated) {
                unrelatedEvents.incrementAndGet();
                unrelated.setHealth(12);
                event.setCanceled(true);
            }
        });
        victim.subscribe((LivingDeathEvent event) -> {
            if (event.getEntity() == victim) {
                victimEvents.incrementAndGet();
                victim.setHealth(20);
                victim.heal(20);
                event.setCanceled(true);
                victim.die(event.getSource());
                unrelated.setHealth(0);
                unrelated.die(unrelated.damageSources().genericKill());
            }
        });
        CompoundTag equipmentData = new CompoundTag();
        equipmentData.putLong("test_energy", 9_000_000L);
        equipmentData.putString("test_modules", "undying,shield");
        ItemStack equipment = new ItemStack(Items.DIAMOND_CHESTPLATE);
        equipment.set(DataComponents.CUSTOM_DATA, CustomData.of(equipmentData));
        victim.setItemSlot(EquipmentSlot.CHEST, equipment);
        ItemStack beforeEquipment = equipment.copy();
        int beforeDeaths = victim.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        boolean previousKeepInventory = level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, level.getServer());
        try {
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(victim, strike), OrbitalErasureOutcome.PLAYER_DEATH_COMPLETED,
                    "A cancelled death event must still complete our owned settlement");
            var life = Objects.requireNonNull(OrbitalErasureAttachments.find(victim));
            helper.assertValueEqual(life.phase(), Phase.DEATH_COMPLETED, "Completion must be observed after the whole death body");
            helper.assertTrue(life.context() == null, "The invocation context must be closed after successful death");
            helper.assertValueEqual(victimEvents.get(), 1, "Reentrant death must not dispatch a second death event");
            helper.assertValueEqual(unrelatedEvents.get(), 1, "Another entity's nested normal death must still run its own event");
            helper.assertTrue(unrelated.isAlive() && unrelated.getHealth() == 12,
                    "Our invocation must not override cancellation or healing for another player");
            helper.assertValueEqual(victim.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)), beforeDeaths + 1,
                    "The real death statistics must settle exactly once");
            helper.assertValueEqual(victim.getOutboundPackets(ClientboundPlayerCombatKillPacket.class).count(), 1L,
                    "The real death notification must be delivered exactly once");
            victim.setHealth(10);
            victim.heal(10);
            helper.assertTrue(victim.getHealth() == 0 && !victim.isAlive(), "The old life must remain terminal after die returns");
            helper.assertTrue(ItemStack.isSameItemSameComponents(victim.getItemBySlot(EquipmentSlot.CHEST), beforeEquipment),
                    "Keep-inventory and equipment components must remain intact");
            var restoredStrike = OrbitalErasureStrike.load(strike.strikeId(), Set.of(), strike.save());
            victim.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            ServerPlayer reborn = Objects.requireNonNull(level.getServer().getPlayerList().getPlayer(victim.getUUID()));
            helper.assertTrue(reborn != victim && reborn.isAlive() && reborn.connection == victim.connection,
                    "Normal respawn must create a fresh life on the existing connection");
            helper.assertTrue(OrbitalErasureAttachments.find(reborn) == null, "A new life must not inherit the attachment");
            helper.assertTrue(ItemStack.isSameItemSameComponents(reborn.getItemBySlot(EquipmentSlot.CHEST), beforeEquipment),
                    "Equipment data must transfer through normal respawn");
            reborn.setHealth(10);
            reborn.heal(3);
            helper.assertValueEqual(reborn.getHealth(), 13.0F, "The new life must regain ordinary healing");
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(reborn, restoredStrike), OrbitalErasureOutcome.ALREADY_HANDLED,
                    "A restored strike journal must not erase a respawned object with the same UUID again");
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(reborn, strike()), OrbitalErasureOutcome.PLAYER_DEATH_COMPLETED,
                    "A later independent strike must remain valid for the new life");
        } finally {
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(previousKeepInventory, level.getServer());
        }
        helper.succeed();
    }

    @TestHolder("orbital_partial_death_failure_releases_protection_without_replaying_settlement")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_erasure_lifecycle")
    public static void callbackFailureDoesNotLeaveALivingPlayerLocked(GameTestHelper helper) {
        GameTestPlayer victim = player(helper);
        OrbitalErasureStrike strike = strike();
        AtomicInteger events = new AtomicInteger();
        victim.subscribe((LivingDeathEvent event) -> {
            if (event.getEntity() == victim) {
                events.incrementAndGet();
                throw new IllegalStateException("Intentional erasure death callback failure");
            }
        });
        helper.assertValueEqual(OrbitalEntityErasure.eraseHit(victim, strike), OrbitalErasureOutcome.PARTIAL_FAILURE,
                "An exception after the first death side effect must not be reported as successful death");
        helper.assertTrue(OrbitalErasureAttachments.find(victim) == null, "A partial failure must release the life lock and context");
        victim.setHealth(10);
        victim.heal(3);
        helper.assertValueEqual(victim.getHealth(), 13.0F, "Equipment recovery must not remain disabled after failure");
        helper.assertValueEqual(OrbitalEntityErasure.eraseHit(victim, strike), OrbitalErasureOutcome.ALREADY_HANDLED,
                "The same strike must not replay a partially completed death");
        helper.assertValueEqual(events.get(), 1, "A failed callback must be invoked only once");
        helper.assertTrue(victim.getOutboundPackets(ClientboundPlayerCombatKillPacket.class).findAny().isEmpty(),
                "An incomplete death must not be disguised by an extra manually sent death packet");
        helper.succeed();
    }

    @TestHolder("orbital_precommit_failure_restores_only_its_own_temporary_player_changes")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_erasure_lifecycle")
    public static void rejectedHealthWriteFailsBeforeDeath(GameTestHelper helper) {
        RefusingTerminalPlayer victim = new RefusingTerminalPlayer(helper.getLevel());
        Objects.requireNonNull(victim.getAttribute(Attributes.MAX_ABSORPTION)).setBaseValue(20);
        victim.setAbsorptionAmount(7);
        float health = victim.getHealth();
        helper.assertValueEqual(OrbitalEntityErasure.eraseHit(victim, strike()), OrbitalErasureOutcome.FAILED_BEFORE_COMMIT,
                "An unknown subclass that refuses terminal health must not be treated as a completed death");
        helper.assertValueEqual(victim.getHealth(), health, "A rejected health write must preserve the previous health");
        helper.assertValueEqual(victim.getAbsorptionAmount(), 7.0F, "Only our temporary absorption clear must be rolled back");
        helper.assertTrue(OrbitalErasureAttachments.find(victim) == null, "A precommit failure must leave no protection veto");
        helper.succeed();
    }

    @TestHolder("orbital_zero_health_and_a_returned_die_call_are_not_death_completion")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_erasure_lifecycle")
    public static void earlyDeathReturnDoesNotCountAsCompletion(GameTestHelper helper) {
        EarlyReturningDeathPlayer victim = new EarlyReturningDeathPlayer(helper.getLevel());
        float previous = victim.getHealth();
        helper.assertValueEqual(OrbitalEntityErasure.eraseHit(victim, strike()), OrbitalErasureOutcome.FAILED_BEFORE_COMMIT,
                "Zero health and an invoked die method cannot substitute for the real death settlement");
        helper.assertValueEqual(victim.deathCalls, 1, "An intercepted death must not be retried in a loop");
        helper.assertTrue(victim.wasZeroHealth, "The fixture must reach zero health before its early-returning death entry");
        helper.assertValueEqual(victim.getHealth(), previous, "A side-effect-free early return must restore our health write");
        helper.assertTrue(OrbitalErasureAttachments.find(victim) == null, "An early return must leave no old-life veto");
        helper.succeed();
    }

    @TestHolder("orbital_erasure_keeps_the_normal_cancellable_drops_event")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_erasure_lifecycle")
    public static void dropCancellationRemainsIndependentFromDeathCancellation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameTestPlayer victim = player(helper);
        victim.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND, 37));
        AtomicInteger dropsEvents = new AtomicInteger();
        victim.subscribe((LivingDropsEvent event) -> {
            if (event.getEntity() == victim) {
                dropsEvents.incrementAndGet();
                helper.assertTrue(event.getDrops().stream().anyMatch(item -> item.getItem().is(Items.DIAMOND)),
                        "The normal captured drop list must reach the event");
                event.setCanceled(true);
            }
        });
        boolean previous = level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, level.getServer());
        try {
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(victim, strike()), OrbitalErasureOutcome.PLAYER_DEATH_COMPLETED,
                    "Cancelling item drops must not cancel life termination");
            helper.assertValueEqual(dropsEvents.get(), 1, "The drops event must retain its once-only vanilla dispatch");
            helper.assertTrue(victim.getInventory().isEmpty(), "The non-keep-inventory rule must execute normally");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(victim.blockPosition()).inflate(2),
                    item -> item.getItem().is(Items.DIAMOND)).isEmpty(), "A cancelled drops event must keep items out of the world");
        } finally {
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(previous, level.getServer());
        }
        helper.succeed();
    }

    private static GameTestPlayer player(GameTestHelper helper) {
        GameTestPlayer player = new ExtendedGameTestHelper(helper.testInfo).makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 10, 15))));
        player.setNoGravity(true);
        return player;
    }

    @TestHolder("orbital_completed_death_cannot_reenter_before_its_outer_context_closes")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_erasure_lifecycle")
    public static void completedBodyCannotReplayInsideTheOuterExecution(GameTestHelper helper) {
        GameTestPlayer victim = player(helper);
        AtomicInteger events = new AtomicInteger();
        victim.subscribe((LivingDeathEvent event) -> {
            if (event.getEntity() == victim) {
                events.incrementAndGet();
            }
        });
        try (OrbitalErasureContext context = new OrbitalErasureContext(victim, UUID.randomUUID(), UUID.randomUUID(), null)) {
            victim.setHealth(0);
            victim.die(victim.damageSources().genericKill());
            helper.assertValueEqual(context.state().phase(), Phase.DEATH_COMPLETED, "The first body must finish while the outer execution remains open");
            victim.die(victim.damageSources().genericKill());
            helper.assertValueEqual(events.get(), 1, "An outer callback must not replay the completed death body");
            helper.assertValueEqual(victim.getOutboundPackets(ClientboundPlayerCombatKillPacket.class).count(), 1L,
                    "A still-open context must not permit a second death notification");
        }
        helper.succeed();
    }

    private static OrbitalErasureStrike strike() {
        return new OrbitalErasureStrike(UUID.randomUUID(), null, Set.of());
    }

    /** Models an unknown external subclass override through a normal API, without reflection or fake death state. */
    private static final class RefusingTerminalPlayer extends ServerPlayer {

        private boolean refuseTerminal;

        private RefusingTerminalPlayer(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "erasure-health-refusal"), ClientInformation.createDefault());
            this.refuseTerminal = true;
        }

        @Override
        public void setHealth(float health) {
            if (!this.refuseTerminal || health > 0) {
                super.setHealth(health);
            }
        }
    }

    private static final class EarlyReturningDeathPlayer extends ServerPlayer {

        private int deathCalls;
        private boolean wasZeroHealth;

        private EarlyReturningDeathPlayer(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "erasure-early-return"), ClientInformation.createDefault());
        }

        @Override
        public void die(DamageSource source) {
            this.deathCalls++;
            this.wasZeroHealth = getHealth() == 0;
        }
    }
}
