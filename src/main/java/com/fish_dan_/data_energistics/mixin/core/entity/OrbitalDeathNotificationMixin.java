package com.fish_dan_.data_energistics.mixin.core.entity;

import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureAttachments;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Delivers the real death routine's packet for its own terminal player; unrelated packets retain their chain. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class OrbitalDeathNotificationMixin {

    @Shadow
    @Final
    protected Connection connection;
    @Shadow
    @Final
    protected MinecraftServer server;
    @Shadow
    private volatile boolean suspendFlushingOnServerThread;

    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V")
    private void dataEnergistics$routeDeathNotification(Packet<?> packet, @Nullable PacketSendListener listener,
                                                        Operation<Void> original) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game && packet instanceof ClientboundPlayerCombatKillPacket death && death.playerId() == game.player.getId() && OrbitalErasureAttachments.blocksRecovery(game.player)) {
            NetworkRegistry.checkPacket(packet, game);
            this.connection.send(packet, listener, !this.suspendFlushingOnServerThread || !this.server.isSameThread());
        } else {
            original.call(packet, listener);
        }
    }
}
