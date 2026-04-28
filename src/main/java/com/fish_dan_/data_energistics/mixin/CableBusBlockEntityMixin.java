package com.fish_dan_.data_energistics.mixin;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CableBusBlockEntity.class)
public abstract class CableBusBlockEntityMixin extends AEBaseBlockEntity {
    public CableBusBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    @Inject(method = "addPart", at = @At("RETURN"))
    private void dataEnergistics$refreshTowerLinksAfterAdd(IPartItem<? extends IPart> partItem, Direction side, @Nullable Player player,
                                                           CallbackInfoReturnable<? extends IPart> cir) {
        if (cir.getReturnValue() != null && this.level != null) {
            DataDistributionTowerBlockEntity.onPotentialNodeAdded(this.level, this.worldPosition);
        }
    }

    @Inject(method = "replacePart", at = @At("RETURN"))
    private void dataEnergistics$refreshTowerLinksAfterReplace(IPartItem<? extends IPart> partItem, @Nullable Direction side,
                                                               Player owner, InteractionHand hand,
                                                               CallbackInfoReturnable<? extends IPart> cir) {
        if (cir.getReturnValue() != null && this.level != null) {
            DataDistributionTowerBlockEntity.onPotentialNodeAdded(this.level, this.worldPosition);
        }
    }

    @Inject(method = "removePart", at = @At("RETURN"))
    private void dataEnergistics$refreshTowerLinksAfterRemove(IPart part, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && this.level != null) {
            DataDistributionTowerBlockEntity.onPotentialNodeAdded(this.level, this.worldPosition);
        }
    }
}
