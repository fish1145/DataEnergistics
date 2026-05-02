package com.fish_dan_.data_energistics.mixin;

import com.fish_dan_.data_energistics.integration.Ae2LtAdaptiveProviderCompat;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import com.moakiee.ae2lt.network.WirelessConnectorUsePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(WirelessConnectorUsePacket.class)
public abstract class Ae2ltWirelessConnectorUsePacketMixin {
    @Shadow
    private BlockPos pos;

    @Shadow
    private Direction face;

    @Shadow
    private boolean contiguous;

    @Inject(method = "handleOnServer", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$handleAdaptiveOverloadedProvider(ServerPlayer player, CallbackInfo ci) {
        Level level = player.level();
        if (!level.isLoaded(this.pos)) {
            return;
        }

        ItemStack stack = player.getItemInHand(((WirelessConnectorUsePacket) (Object) this).hand());
        if (!(stack.getItem() instanceof com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem)) {
            return;
        }
        if (!player.canInteractWithBlock(this.pos, 1.0D)) {
            return;
        }

        BlockEntity targetBe = level.getBlockEntity(this.pos);
        boolean isAdaptiveProvider = Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(targetBe);
        boolean hasSelection = OverloadedWirelessConnectorItem.hasSelection(stack);
        String hostType = hasSelection ? OverloadedWirelessConnectorItem.getSelectedHostType(stack) : null;
        boolean selectedAdaptiveProvider = hasSelection
                && OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(hostType)
                && Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(
                dataEnergistics$getSelectedAdaptiveOrVanillaProvider(level, stack));

        if (isAdaptiveProvider && !hasSelection) {
            if (!Ae2LtAdaptiveProviderCompat.isWirelessMode(targetBe)) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.connector.need_wireless").withStyle(ChatFormatting.GREEN), true);
                ci.cancel();
                return;
            }

            OverloadedWirelessConnectorItem.selectHost(stack, level, this.pos, OverloadedWirelessConnectorItem.HOST_PROVIDER);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.selected", this.pos.getX(), this.pos.getY(), this.pos.getZ())
                            .withStyle(ChatFormatting.GREEN), true);
            ci.cancel();
            return;
        }

        if (!selectedAdaptiveProvider) {
            return;
        }
        if (!OverloadedWirelessConnectorItem.isSelectionInCurrentDimension(level, stack)) {
            return;
        }

        BlockEntity selectedHost = dataEnergistics$getSelectedAdaptiveOrVanillaProvider(level, stack);
        if (selectedHost == null) {
            return;
        }

        if (targetBe instanceof com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity
                || Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(targetBe)) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.cannot_bind_provider").withStyle(ChatFormatting.RED), true);
            ci.cancel();
            return;
        }

        var targets = WirelessConnectorTargetHelper.collectTargets(level, this.pos, this.contiguous);
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
        var existingConnections = Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(selectedHost)
                ? Ae2LtAdaptiveProviderCompat.getConnections(selectedHost)
                : ((com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity) selectedHost).getConnections();

        for (var targetPos : targets) {
            var existing = existingConnections.stream()
                    .filter(c -> c.sameTarget(targetDim, targetPos))
                    .findFirst().orElse(null);

            if (existing != null) {
                if (existing.boundFace() == this.face) {
                    boolean removed = Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(selectedHost)
                            ? Ae2LtAdaptiveProviderCompat.removeConnection(selectedHost, targetDim, targetPos)
                            : ((com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity) selectedHost)
                            .removeConnection(targetDim, targetPos);
                    if (removed) {
                        disconnected.add(targetPos.immutable());
                    }
                } else {
                    dataEnergistics$addConnection(selectedHost, targetDim, targetPos, this.face);
                    updated.add(targetPos.immutable());
                }
            } else {
                dataEnergistics$addConnection(selectedHost, targetDim, targetPos, this.face);
                connected.add(targetPos.immutable());
            }
        }

        dataEnergistics$sendConnectionFeedback(player, disconnected, updated, connected);
        ci.cancel();
    }

    @Unique
    private BlockEntity dataEnergistics$getSelectedAdaptiveOrVanillaProvider(Level level, ItemStack stack) {
        BlockEntity vanilla = OverloadedWirelessConnectorItem.getSelectedProvider(level, stack);
        if (vanilla != null) {
            return vanilla;
        }

        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (!tag.contains("SelectedProvider")) {
            return null;
        }

        var sel = tag.getCompound("SelectedProvider");
        var dimKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(sel.getString("Dim")));
        var selectedPos = BlockPos.of(sel.getLong("Pos"));
        if (!level.dimension().equals(dimKey) || !level.isLoaded(selectedPos)) {
            return null;
        }

        return Ae2LtAdaptiveProviderCompat.asAdaptiveOverloadedProvider(level.getBlockEntity(selectedPos));
    }

    @Unique
    private void dataEnergistics$addConnection(BlockEntity provider, net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        if (Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(provider)) {
            Ae2LtAdaptiveProviderCompat.addOrUpdateConnection(provider, dimension, pos, face);
        } else {
            ((com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity) provider).addOrUpdateConnection(dimension, pos, face);
        }
    }

    @Unique
    private void dataEnergistics$sendConnectionFeedback(ServerPlayer player,
                                                        ArrayList<BlockPos> disconnected,
                                                        ArrayList<BlockPos> updated,
                                                        ArrayList<BlockPos> connected) {
        boolean many = (disconnected.size() + updated.size() + connected.size()) > 1;

        if (many) {
            if (!disconnected.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.disconnected_many", disconnected.size(), this.face.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (!updated.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.updated_many", updated.size(), this.face.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (!connected.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.connected_many", connected.size(), this.face.getName())
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
                    "ae2lt.connector.updated", p.getX(), p.getY(), p.getZ(), this.face.getName())
                    .withStyle(ChatFormatting.GREEN), true);
        } else if (!connected.isEmpty()) {
            var p = connected.getFirst();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.connected", p.getX(), p.getY(), p.getZ(), this.face.getName())
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }
}
