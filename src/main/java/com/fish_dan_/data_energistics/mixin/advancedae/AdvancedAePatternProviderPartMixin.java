package com.fish_dan_.data_energistics.mixin.advancedae;

import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderHostAccessor;
import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningInventoryHelper;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.pedroksl.advanced_ae.common.parts.AdvPatternProviderPart;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvPatternProviderPart.class)
public abstract class AdvancedAePatternProviderPartMixin implements PatternProviderHostAccessor {

    @Unique
    private static final String DATA_ENERGISTICS_REDSTONE_TUNING_TAG = "data_energistics_redstone_tuning_mode";
    @Unique
    private static final String DATA_ENERGISTICS_REDSTONE_UPGRADES_TAG = "data_energistics_redstone_upgrades";
    @Unique
    private static final int DATA_ENERGISTICS_REDSTONE_PULSE_TICKS = 1;

    @Unique
    private IUpgradeInventory dataEnergistics$redstoneTuningUpgrades;
    @Unique
    private RedstoneTuningMode dataEnergistics$redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
    @Unique
    private int dataEnergistics$redstonePulseTicks;
    @Unique
    private long dataEnergistics$lastPulseTickTime = Long.MIN_VALUE;
    @Unique
    private boolean dataEnergistics$pendingRedstoneInputCheck;
    @Unique
    private boolean dataEnergistics$lastRedstoneInputPowered;
    @Unique
    private boolean dataEnergistics$redstoneInputPulsePending;

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void dataEnergistics$saveRedstoneTuning(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        this.dataEnergistics$ensureUpgradeInventory().writeToNBT(data, DATA_ENERGISTICS_REDSTONE_UPGRADES_TAG, registries);
        data.putString(DATA_ENERGISTICS_REDSTONE_TUNING_TAG, this.dataEnergistics$redstoneTuningMode.name());
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void dataEnergistics$loadRedstoneTuning(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        this.dataEnergistics$ensureUpgradeInventory().readFromNBT(data, DATA_ENERGISTICS_REDSTONE_UPGRADES_TAG, registries);
        if (data.contains(DATA_ENERGISTICS_REDSTONE_TUNING_TAG)) {
            try {
                this.dataEnergistics$redstoneTuningMode = RedstoneTuningMode.valueOf(data.getString(DATA_ENERGISTICS_REDSTONE_TUNING_TAG));
            } catch (IllegalArgumentException ignored) {
                this.dataEnergistics$redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
            }
        }
    }

    @Override
    public IUpgradeInventory dataEnergistics$getRedstoneTuningUpgrades() {
        return this.dataEnergistics$ensureUpgradeInventory();
    }

    @Override
    public boolean dataEnergistics$hasRedstoneTuningCard() {
        return RedstoneTuningInventoryHelper.hasRedstoneTuningCard(this, this.dataEnergistics$ensureUpgradeInventory());
    }

    @Override
    public RedstoneTuningMode dataEnergistics$getRedstoneTuningMode() {
        return this.dataEnergistics$redstoneTuningMode;
    }

    @Override
    public boolean dataEnergistics$setRedstoneTuningMode(RedstoneTuningMode mode) {
        if (mode == null || this.dataEnergistics$redstoneTuningMode == mode) {
            return false;
        }
        this.dataEnergistics$clearPulseState();
        this.dataEnergistics$clearInputState();
        this.dataEnergistics$redstoneTuningMode = mode;
        if (mode == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            this.dataEnergistics$syncRedstoneInputBaseline();
        }
        ((AdvPatternProviderPart) (Object) this).saveChanges();
        return true;
    }

    @Override
    public void dataEnergistics$onRedstoneTuningDispatch() {
        if (!this.dataEnergistics$hasRedstoneTuningCard() || this.dataEnergistics$redstoneTuningMode != RedstoneTuningMode.EMIT_ON_DISPATCH) {
            return;
        }
        if (this.dataEnergistics$redstonePulseTicks > 0) {
            return;
        }
        this.dataEnergistics$redstonePulseTicks = DATA_ENERGISTICS_REDSTONE_PULSE_TICKS;
        this.dataEnergistics$notifyPulseChanged();
        this.dataEnergistics$schedulePulseTick();
    }

    @Override
    public void dataEnergistics$serverTick() {
        AdvPatternProviderPart self = (AdvPatternProviderPart) (Object) this;
        if (self.getLevel() == null) {
            return;
        }
        long gameTime = self.getLevel().getGameTime();
        if (this.dataEnergistics$lastPulseTickTime == gameTime) {
            return;
        }
        this.dataEnergistics$lastPulseTickTime = gameTime;
        if (this.dataEnergistics$pendingRedstoneInputCheck && self.getBlockEntity() != null) {
            this.dataEnergistics$pendingRedstoneInputCheck = false;
            boolean powered = self.getLevel().hasNeighborSignal(self.getBlockEntity().getBlockPos());
            if (powered && !this.dataEnergistics$lastRedstoneInputPowered) {
                this.dataEnergistics$redstoneInputPulsePending = true;
                this.dataEnergistics$tryForcePulseUnlock();
            }
            this.dataEnergistics$lastRedstoneInputPowered = powered;
        }
        if (this.dataEnergistics$redstonePulseTicks <= 0) {
            return;
        }
        this.dataEnergistics$redstonePulseTicks--;
        if (this.dataEnergistics$redstonePulseTicks > 0) {
            this.dataEnergistics$schedulePulseTick();
        } else {
            this.dataEnergistics$notifyPulseChanged();
        }
    }

    @Override
    public boolean dataEnergistics$isRedstoneTuningPulseActive() {
        return this.dataEnergistics$redstoneTuningMode == RedstoneTuningMode.EMIT_ON_DISPATCH && this.dataEnergistics$redstonePulseTicks > 0;
    }

    @Override
    public void dataEnergistics$scheduleRedstoneInputCheck() {
        AdvPatternProviderPart self = (AdvPatternProviderPart) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide() || self.getBlockEntity() == null) {
            return;
        }
        if (this.dataEnergistics$redstoneTuningMode != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }
        boolean powered = self.getLevel().hasNeighborSignal(self.getBlockEntity().getBlockPos());
        if (powered && !this.dataEnergistics$lastRedstoneInputPowered) {
            this.dataEnergistics$redstoneInputPulsePending = true;
            this.dataEnergistics$tryForcePulseUnlock();
        }
        this.dataEnergistics$lastRedstoneInputPowered = powered;
        this.dataEnergistics$pendingRedstoneInputCheck = true;
        if (self.getLevel() instanceof ServerLevel level) {
            level.scheduleTick(self.getBlockEntity().getBlockPos(), self.getBlockEntity().getBlockState().getBlock(), 1);
        }
    }

    @Override
    public boolean dataEnergistics$consumeRedstoneInputPulse() {
        boolean pending = this.dataEnergistics$redstoneInputPulsePending;
        this.dataEnergistics$redstoneInputPulsePending = false;
        return pending;
    }

    @Unique
    private IUpgradeInventory dataEnergistics$ensureUpgradeInventory() {
        if (this.dataEnergistics$redstoneTuningUpgrades == null) {
            this.dataEnergistics$redstoneTuningUpgrades = UpgradeInventories.forMachine(
                    ((AdvPatternProviderPart) (Object) this).getPartItem().asItem(),
                    1,
                    ((AdvPatternProviderPart) (Object) this)::saveChanges);
        }
        return this.dataEnergistics$redstoneTuningUpgrades;
    }

    @Unique
    private void dataEnergistics$notifyPulseChanged() {
        AdvPatternProviderPart self = (AdvPatternProviderPart) (Object) this;
        if (self.getHost() != null) {
            self.getHost().notifyNeighbors();
        }
    }

    @Unique
    private void dataEnergistics$schedulePulseTick() {
        AdvPatternProviderPart self = (AdvPatternProviderPart) (Object) this;
        if (self.getLevel() instanceof ServerLevel level && self.getBlockEntity() != null) {
            level.scheduleTick(self.getBlockEntity().getBlockPos(), self.getBlockEntity().getBlockState().getBlock(), 1);
        }
    }

    @Unique
    private void dataEnergistics$clearPulseState() {
        if (this.dataEnergistics$redstonePulseTicks <= 0) {
            this.dataEnergistics$lastPulseTickTime = Long.MIN_VALUE;
            return;
        }
        this.dataEnergistics$redstonePulseTicks = 0;
        this.dataEnergistics$lastPulseTickTime = Long.MIN_VALUE;
        this.dataEnergistics$notifyPulseChanged();
    }

    @Unique
    private void dataEnergistics$clearInputState() {
        this.dataEnergistics$pendingRedstoneInputCheck = false;
        this.dataEnergistics$redstoneInputPulsePending = false;
        this.dataEnergistics$lastRedstoneInputPowered = false;
    }

    @Unique
    private void dataEnergistics$syncRedstoneInputBaseline() {
        AdvPatternProviderPart self = (AdvPatternProviderPart) (Object) this;
        if (self.getLevel() != null && self.getBlockEntity() != null) {
            this.dataEnergistics$lastRedstoneInputPowered = self.getLevel().hasNeighborSignal(self.getBlockEntity().getBlockPos());
        }
    }

    @Unique
    private void dataEnergistics$tryForcePulseUnlock() {
        if (!this.dataEnergistics$redstoneInputPulsePending || !this.dataEnergistics$hasRedstoneTuningCard() || this.dataEnergistics$redstoneTuningMode != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }
        if (((AdvPatternProviderPart) (Object) this).getLogic() instanceof PatternProviderLogicAccessor accessor && accessor.dataEnergistics$forcePulseUnlock()) {
            this.dataEnergistics$redstoneInputPulsePending = false;
        }
    }
}
