package com.fish_dan_.data_energistics.item.powered.cannon.rail;

import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.network.action.RailBeamPayload;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

final class RailVisuals {

    private RailVisuals() {}

    static void send(Player owner, InteractionHand hand, RailAmmunition ammo, RailBeam.Contact contact) {
        List<Vec3> points = new ObjectArrayList<>();
        points.add(contact.end());
        for (var target : contact.chained()) points.add(target.getBoundingBox().getCenter());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, new RailBeamPayload(owner.getId(), hand, ammo.color(), ammo == RailAmmunition.FE, points));
    }
}
