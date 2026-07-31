package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.integration.ae2lt.Ae2LtAdaptiveProviderCompat;
import com.fish_dan_.data_energistics.integration.ae2lt.Ae2LtWirelessBridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.network.WirelessConnectorUsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(value = WirelessConnectorUsePacket.class, remap = false)
public abstract class Ae2ltWirelessConnectorUsePacketMixin {

    @Shadow
    public abstract InteractionHand hand();

    @Shadow
    public abstract BlockPos pos();

    @Shadow
    public abstract Direction face();

    @Shadow
    public abstract boolean contiguous();

    @Inject(method = "handleOnServer", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$handleAdaptiveOverloadedProvider(ServerPlayer player, CallbackInfo ci) {
        Level level = player.level();
        BlockPos pos = this.pos();
        Direction face = this.face();
        if (!level.isLoaded(pos)) {
            return;
        }

        ItemStack stack = player.getItemInHand(this.hand());
        if (!Ae2LtWirelessBridge.isConnectorItem(stack)) {
            return;
        }
        if (!player.canInteractWithBlock(pos, 1.0D)) {
            return;
        }

        BlockEntity targetBe = level.getBlockEntity(pos);
        boolean isAdaptiveProvider = Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(targetBe);
        boolean hasSelection = Ae2LtWirelessBridge.hasSelection(stack);
        String hostType = hasSelection ? Ae2LtWirelessBridge.getSelectedHostType(stack) : null;
        String hostProviderType = Ae2LtWirelessBridge.hostProviderType();
        boolean selectedAdaptiveProvider = hasSelection && hostProviderType != null && hostProviderType.equals(hostType) && Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(
                dataEnergistics$getSelectedAdaptiveProvider(level, stack));

        if (isAdaptiveProvider && !hasSelection) {
            if (!Ae2LtAdaptiveProviderCompat.isWirelessMode(targetBe)) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.connector.need_wireless").withStyle(ChatFormatting.GREEN), true);
                ci.cancel();
                return;
            }

            Ae2LtWirelessBridge.selectHost(stack, level, pos, hostProviderType);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.selected", pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN),
                    true);
            ci.cancel();
            return;
        }

        if (!selectedAdaptiveProvider) {
            return;
        }
        if (!Ae2LtWirelessBridge.isSelectionInCurrentDimension(level, stack)) {
            return;
        }

        BlockEntity selectedHost = dataEnergistics$getSelectedAdaptiveProvider(level, stack);
        if (selectedHost == null) {
            return;
        }

        if (Ae2LtWirelessBridge.isVanillaOverloadedProvider(targetBe) || Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(targetBe)) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.cannot_bind_provider").withStyle(ChatFormatting.RED), true);
            ci.cancel();
            return;
        }

        var targets = Ae2LtWirelessBridge.collectTargets(level, pos, this.contiguous());
        if (targets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.not_machine").withStyle(ChatFormatting.GREEN), true);
            ci.cancel();
            return;
        }

        var targetDim = level.dimension();
        var disconnected = new ArrayList<BlockPos>();
        var updated = new ArrayList<BlockPos>();
        var connected = new ArrayList<BlockPos>();
        var existingConnections = Ae2LtAdaptiveProviderCompat.getConnections(selectedHost);

        for (var targetPos : targets) {
            var existing = existingConnections.stream()
                    .filter(c -> c.sameTarget(targetDim, targetPos))
                    .findFirst().orElse(null);

            if (existing != null) {
                if (existing.boundFace() == face) {
                    boolean removed = Ae2LtAdaptiveProviderCompat.removeConnection(
                            selectedHost, targetDim, targetPos);
                    if (removed) {
                        disconnected.add(targetPos.immutable());
                    }
                } else {
                    if (dataEnergistics$addConnection(selectedHost, targetDim, targetPos, face)) {
                        updated.add(targetPos.immutable());
                    }
                }
            } else {
                if (dataEnergistics$addConnection(selectedHost, targetDim, targetPos, face)) {
                    connected.add(targetPos.immutable());
                }
            }
        }

        dataEnergistics$sendConnectionFeedback(player, disconnected, updated, connected, face);
        ci.cancel();
    }

    @Unique
    private BlockEntity dataEnergistics$getSelectedAdaptiveProvider(Level level, ItemStack stack) {
        return Ae2LtAdaptiveProviderCompat.asAdaptiveOverloadedProvider(
                Ae2LtWirelessBridge.resolveSelectedBlockEntity(level, stack));
    }

    @Unique
    private boolean dataEnergistics$addConnection(BlockEntity provider,
                                                  ResourceKey<Level> dimension,
                                                  BlockPos pos,
                                                  Direction face) {
        return Ae2LtAdaptiveProviderCompat.addOrUpdateConnection(provider, dimension, pos, face);
    }

    @Unique
    private void dataEnergistics$sendConnectionFeedback(ServerPlayer player,
                                                        ArrayList<BlockPos> disconnected,
                                                        ArrayList<BlockPos> updated,
                                                        ArrayList<BlockPos> connected,
                                                        Direction face) {
        boolean many = (disconnected.size() + updated.size() + connected.size()) > 1;

        if (many) {
            if (!disconnected.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.disconnected_many", disconnected.size(), face.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (!updated.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.updated_many", updated.size(), face.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (!connected.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.connected_many", connected.size(), face.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return;
        }

        if (!disconnected.isEmpty()) {
            var p = disconnected.getFirst();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.disconnected", p.getX(), p.getY(), p.getZ())
                    .withStyle(ChatFormatting.GREEN), true);
        } else if (!updated.isEmpty()) {
            var p = updated.getFirst();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.updated", p.getX(), p.getY(), p.getZ(), face.getName())
                    .withStyle(ChatFormatting.GREEN), true);
        } else if (!connected.isEmpty()) {
            var p = connected.getFirst();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.connected", p.getX(), p.getY(), p.getZ(), face.getName())
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }
}
