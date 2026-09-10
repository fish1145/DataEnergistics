package com.fish_dan_.data_energistics.item.powered.cannon.rail;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonCharge;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMobEffects;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKey;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/** Server-thread firing lifecycle. Persisted escrow is reconciled instead of resuming after reload. */
public final class RailFiring {

    public static final int COOLDOWN_TICKS = 80;
    private static final Map<UUID, Active> ACTIVE = new Object2ObjectOpenHashMap<>();

    public static boolean cooling(ItemStack stack, long time) {
        return stack.getOrDefault(DEDataComponents.RAIL_COOLDOWN_END.get(), 0L) > time;
    }

    public static void begin(Player player, InteractionHand hand, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level) || !(stack.getItem() instanceof MatterConvergingCrossbowItem item)) return;
        reconcile(stack, level);
        if (stack.has(DEDataComponents.RAIL_SESSION.get()) || cooling(stack, level.getGameTime()) || player.getCooldowns().isOnCooldown(item) || !player.isAlive() || player.isSpectator() || player.isUsingItem() || player.containerMenu != player.inventoryMenu || player.hasEffect(DEMobEffects.RADIX_LOSS)) return;
        AEKey key = MountedAmmoCells.selectedKey(stack, MatterConvergingCrossbowMode.RAIL);
        if (key == null) return;
        RailAmmunition ammo = RailAmmunition.fromKey(key);
        if (ammo == null || item.getAECurrentPower(stack) < 200 || MountedAmmoCells.transfer(stack, key, ammo.cost(), false, Actionable.SIMULATE) != ammo.cost()) return;
        long reserved = MountedAmmoCells.transfer(stack, key, ammo.cost(), false, Actionable.MODULATE);
        if (reserved != ammo.cost()) {
            if (MountedAmmoCells.transfer(stack, key, reserved, true, Actionable.MODULATE) != reserved) throw new IllegalStateException("Ammunition reservation rollback failed");
            return;
        }
        item.extractAEPower(stack, 200, Actionable.MODULATE);
        RailSession session = new RailSession(UUID.randomUUID(), key,
                Math.clamp(item.getUpgrades(stack).getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()), 0, 2), 0);
        stack.set(DEDataComponents.RAIL_SESSION.get(), session);
        stack.set(DEDataComponents.CANNON_CHARGE.get(), new CannonCharge(level.getGameTime(), ammo.duration(),
                MatterConvergingCrossbowMode.RAIL, hand, player.getUUID(), level.dimension().location()));
        ACTIVE.put(session.id(), new Active(player, hand, stack, level));
    }

    public static void stop(Player player, ItemStack stack) {
        if (player.level() instanceof ServerLevel level) settle(stack, level, player);
    }

    public static void stopForMutation(ItemStack stack) {
        RailSession session = stack.get(DEDataComponents.RAIL_SESSION.get());
        Active active = session == null ? null : ACTIVE.get(session.id());
        if (active != null) settle(stack, active.level, active.player);
    }

    /** Also called on inventory/menu access: an escrow without its live session came from a reload. */
    public static void reconcile(ItemStack stack, ServerLevel level) {
        RailSession session = stack.get(DEDataComponents.RAIL_SESSION.get());
        if (session != null && !ACTIVE.containsKey(session.id())) settle(stack, level, null);
    }

    private static void settle(ItemStack stack, ServerLevel level, @Nullable Player player) {
        RailSession session = stack.get(DEDataComponents.RAIL_SESSION.get());
        if (session == null) return;
        ACTIVE.remove(session.id());
        // The normal insert path rejects mutations while an escrow is active. Remove the
        // marker for this one authoritative refund, then publish the settled state below.
        stack.remove(DEDataComponents.RAIL_SESSION.get());
        long refund = session.ammunition().cost() - session.ammunition().consumed(session.elapsed());
        if (refund > 0 && MountedAmmoCells.transfer(stack, session.resource(), refund, true, Actionable.MODULATE) != refund) {
            stack.set(DEDataComponents.RAIL_SESSION.get(), session);
            throw new IllegalStateException("Reserved rail cell cannot accept its ammunition refund");
        }
        double energyRefund = 200 - session.ammunition().energy(session.elapsed());
        stack.set(AEComponents.STORED_ENERGY, stack.getOrDefault(AEComponents.STORED_ENERGY, 0.0) + energyRefund);
        stack.remove(DEDataComponents.CANNON_CHARGE.get());
        if (session.elapsed() > 0) {
            stack.set(DEDataComponents.RAIL_COOLDOWN_END.get(), level.getGameTime() + COOLDOWN_TICKS);
            stack.set(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0) + 1);
            if (player != null) player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
        }
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        for (Active active : new ObjectArrayList<>(ACTIVE.values())) {
            if (active.level.getServer() != event.getServer()) continue;
            RailSession session = active.stack.get(DEDataComponents.RAIL_SESSION.get());
            if (session == null) continue;
            Player player = active.player;
            if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.level() != active.level || player.getItemInHand(active.hand) != active.stack || player.containerMenu != player.inventoryMenu || player.hasEffect(DEMobEffects.RADIX_LOSS) || MatterConvergingCrossbowItem.mode(active.stack) != MatterConvergingCrossbowMode.RAIL) {
                settle(active.stack, active.level, player);
                continue;
            }
            try {
                session = session.advance();
                active.stack.set(DEDataComponents.RAIL_SESSION.get(), session);
                RailBeam.Contact contact = RailBeam.trace(active.level, player, session.ammunition(), session.cards());
                int target = contact.target() == null ? -1 : contact.target().getId();
                active.continuous = target < 0 ? 0 : target == active.target ? active.continuous + 1 : 1;
                active.target = target;
                RailBeam.hit(player, session, contact, active.continuous);
                RailVisuals.send(player, active.hand, session.ammunition(), contact);
                if (session.elapsed() >= session.ammunition().duration()) settle(active.stack, active.level, player);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Rail firing failed for {} in {} at {}", player.getUUID(), active.level.dimension().location(), player.blockPosition(), exception);
                settle(active.stack, active.level, player);
            }
        }
    }

    private static void stopPlayer(Player player) {
        for (Active active : new ObjectArrayList<>(ACTIVE.values())) {
            if (active.player == player) settle(active.stack, active.level, player);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        stopPlayer(event.getEntity());
    }

    @SubscribeEvent
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        stopPlayer(event.getEntity());
    }

    @SubscribeEvent
    public void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) stopPlayer(player);
    }

    @SubscribeEvent
    public void stopping(ServerStoppingEvent event) {
        for (Active active : new ObjectArrayList<>(ACTIVE.values())) if (active.level.getServer() == event.getServer()) settle(active.stack, active.level, active.player);
    }

    @SubscribeEvent
    public void toss(ItemTossEvent event) {
        ItemStack dropped = event.getEntity().getItem();
        RailSession session = dropped.get(DEDataComponents.RAIL_SESSION.get());
        if (session == null || !(event.getPlayer().level() instanceof ServerLevel level)) return;
        Active active = ACTIVE.get(session.id());
        settle(dropped, level, event.getPlayer());
        if (active != null && active.stack != dropped) {
            active.stack.remove(DEDataComponents.RAIL_SESSION.get());
            active.stack.remove(DEDataComponents.CANNON_CHARGE.get());
        }
    }

    private static final class Active {

        private final Player player;
        private final InteractionHand hand;
        private final ItemStack stack;
        private final ServerLevel level;
        private int target = -1;
        private int continuous;

        private Active(Player player, InteractionHand hand, ItemStack stack, ServerLevel level) {
            this.player = player;
            this.hand = hand;
            this.stack = stack;
            this.level = level;
        }
    }
}
