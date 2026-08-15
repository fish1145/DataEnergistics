package com.fish_dan_.data_energistics.part;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataSolarPanelBlockEntity;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.SolarPanel;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.menu.machine.DataSolarPanelMenuHost;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;

public class MeSolarPanelPart extends UpgradeablePart implements IGridTickable, DataSolarPanelMenuHost {

    private static final ResourceLocation MODEL_OFF = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/me_solar_panel_part_off");
    private static final ResourceLocation MODEL_ON = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/me_solar_panel_part_on");
    private static final String STORED_POWER_TAG = "storedPower";
    private static final String REDSTONE_CONTROLLED_TAG = "redstoneControlled";
    private static final TickingRequest TICKING_REQUEST = new TickingRequest(1, 1, false);

    @PartModels
    private static final PartModel MODELS_OFF = new PartModel(MODEL_OFF);
    @PartModels
    private static final PartModel MODELS_ON = new PartModel(MODEL_ON);
    @PartModels
    private static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_ON);

    private double storedPower;
    private double maxPower;
    private boolean redstoneControlled;

    public MeSolarPanelPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(0.0D)
                .addService(IGridTickable.class, this);
        this.maxPower = DataSolarPanelBlockEntity.computeMaxPower(this.getUpgrades());
    }

    @Override
    protected int getUpgradeSlots() {
        return DataSolarPanelBlockEntity.UPGRADE_SLOTS;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!this.isClientSide()) {
            MenuOpener.open(DEMenus.DATA_SOLAR_PANEL.get(), player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        if (this.getHost() != null) {
            this.getHost().markForUpdate();
        }
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(1, 1, 14, 15, 15, 16);
        bch.addBox(4, 4, 13, 12, 12, 14);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return TICKING_REQUEST;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.redstoneControlled && !isReceivingRedstonePower()) {
            return TickRateModulation.SAME;
        }

        double generated = getGeneratedPowerPerTick();
        if (generated > 0.0D) {
            this.storedPower = Math.min(getAEMaxPower(), this.storedPower + generated);
            pushStoredPowerToGrid(node);
        }

        return TickRateModulation.SAME;
    }

    @Override
    public void upgradesChanged() {
        this.maxPower = DataSolarPanelBlockEntity.computeMaxPower(this.getUpgrades());
        if (this.storedPower > this.maxPower) {
            this.storedPower = this.maxPower;
        }
        markForSaveAndUpdate();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.maxPower = DataSolarPanelBlockEntity.computeMaxPower(this.getUpgrades());
        this.storedPower = Math.max(0.0D, Math.min(data.getDouble(STORED_POWER_TAG), this.maxPower));
        this.redstoneControlled = data.getBoolean(REDSTONE_CONTROLLED_TAG);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putDouble(STORED_POWER_TAG, this.storedPower);
        data.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
    }

    @Override
    public boolean isOnline() {
        return this.getMainNode().isOnline() && (!this.redstoneControlled || isReceivingRedstonePower());
    }

    @Override
    public boolean isDaytime() {
        Level level = this.getLevel();
        return level != null && level.isDay();
    }

    @Override
    public double getAECurrentPower() {
        return this.storedPower;
    }

    @Override
    public double getAEMaxPower() {
        return this.maxPower;
    }

    @Override
    public double getGeneratedPowerPerTick() {
        Level level = this.getLevel();
        if (level == null) {
            return 0.0D;
        }

        ResourceLocation dimensionId = level.dimension().location();
        boolean specialNightGenerationDimension = dimensionId.equals(
                ResourceLocation.fromNamespaceAndPath("ae2", "spatial_storage")) || dimensionId.equals(ResourceLocation.withDefaultNamespace("the_end"));

        BlockPos samplePos = getSkyAccessCheckPos();
        if (!specialNightGenerationDimension && (samplePos == null || !level.canSeeSky(samplePos))) {
            return 0.0D;
        }

        SolarPanel settings = DataEnergisticsConfiguration.INSTANCE.solarPanel();
        double baseGeneration = specialNightGenerationDimension || !level.isDay() ? settings.nightGenerationAEPerTick() : settings.dayGenerationAEPerTick();
        double adjustedGeneration = DataSolarPanelBlockEntity.applySpeedUpgrades(
                baseGeneration,
                this.getUpgrades(),
                settings);
        return adjustedGeneration * getFacingGenerationMultiplier();
    }

    @Override
    public boolean isRedstoneControlled() {
        return this.redstoneControlled;
    }

    @Override
    public boolean setRedstoneControlled(boolean enabled) {
        if (this.redstoneControlled == enabled) {
            return this.redstoneControlled;
        }

        this.redstoneControlled = enabled;
        markForSaveAndUpdate();
        return this.redstoneControlled;
    }

    private void pushStoredPowerToGrid(IGridNode node) {
        if (node == null || node.getGrid() == null) {
            return;
        }

        if (this.storedPower <= 0.0001D) {
            return;
        }

        var energyService = node.getGrid().getEnergyService();
        if (energyService == null) {
            return;
        }

        double overflow = energyService.injectPower(this.storedPower, Actionable.MODULATE);
        this.storedPower = Math.max(0.0D, overflow);
    }

    private boolean isReceivingRedstonePower() {
        var blockEntity = this.getBlockEntity();
        Level level = blockEntity != null ? blockEntity.getLevel() : null;
        return level != null && level.hasNeighborSignal(blockEntity.getBlockPos());
    }

    private double getFacingGenerationMultiplier() {
        Direction side = this.getSide();
        if (side == null) {
            return 0.0D;
        }

        if (side == Direction.DOWN) {
            return 0.0D;
        }

        if (side.getAxis().isHorizontal()) {
            return 2.0D / 3.0D;
        }

        return 1.0D;
    }

    private BlockPos getSkyAccessCheckPos() {
        var blockEntity = this.getBlockEntity();
        Direction side = this.getSide();
        if (blockEntity == null || side == null) {
            return null;
        }
        return blockEntity.getBlockPos().relative(side);
    }

    private void markForSaveAndUpdate() {
        if (this.getHost() != null) {
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        }
    }
}
