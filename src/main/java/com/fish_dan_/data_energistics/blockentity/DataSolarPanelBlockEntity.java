package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.DataSolarPanelBlock;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.SolarPanel;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.menu.DataSolarPanelMenuHost;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataSolarPanelBlockEntity extends AENetworkedPoweredBlockEntity implements IUpgradeableObject, DataSolarPanelMenuHost {

    public static final double ENERGY_CAPACITY = 160_000.0D;
    public static final int UPGRADE_SLOTS = 3;
    public static final int MAX_SPEED_CARDS = 3;
    public static final int MAX_ENERGY_CARDS = 3;
    private static final ResourceLocation SPATIAL_STORAGE_DIMENSION = ResourceLocation.fromNamespaceAndPath("ae2", "spatial_storage");
    private static final ResourceLocation THE_END_DIMENSION = ResourceLocation.withDefaultNamespace("the_end");
    private static final String UPGRADES_TAG = "upgrades";
    private static final String REDSTONE_CONTROLLED_TAG = "redstone_controlled";

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(DEBlocks.DATA_SOLAR_PANEL.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private boolean redstoneControlled;

    public DataSolarPanelBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(DEBlocks.DATA_SOLAR_PANEL.get())
                .setIdlePowerUsage(0.0D);
        this.setInternalMaxPower(computeMaxPower(this.upgrades));
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(Direction.DOWN);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return dir == Direction.DOWN ? AECableType.COVERED : AECableType.NONE;
    }

    @Override
    public void onReady() {
        super.onReady();
        updateOnlineState();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        if (this.redstoneControlled && !isReceivingRedstonePower()) {
            updateOnlineState();
            return;
        }

        this.injectExternalPower(PowerUnit.AE, getGeneratedPowerPerTick(), Actionable.MODULATE);
        pushStoredPowerToGrid();
        updateOnlineState();
    }

    @Override
    public boolean isOnline() {
        return this.getMainNode().isOnline() && (!this.redstoneControlled || isReceivingRedstonePower());
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
        this.saveChanges();
        updateOnlineState();
        this.markForClientUpdate();
        return this.redstoneControlled;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public double getGeneratedPowerPerTick() {
        if (this.level == null) {
            return 0.0D;
        }

        ResourceLocation dimensionId = this.level.dimension().location();
        boolean specialNightGenerationDimension = dimensionId.equals(SPATIAL_STORAGE_DIMENSION) || dimensionId.equals(THE_END_DIMENSION);
        if (!specialNightGenerationDimension && !this.level.canSeeSky(this.worldPosition.above())) {
            return 0.0D;
        }

        SolarPanel settings = DataEnergisticsConfiguration.INSTANCE.solarPanel();
        double baseGeneration = specialNightGenerationDimension || !this.level.isDay() ? settings.nightGenerationAEPerTick() : settings.dayGenerationAEPerTick();
        return applySpeedUpgrades(baseGeneration, this.upgrades, settings);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return super.getSubInventory(id);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.redstoneControlled = data.getBoolean(REDSTONE_CONTROLLED_TAG);
        this.setInternalMaxPower(computeMaxPower(this.upgrades));
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
    }

    @Override
    public boolean isDaytime() {
        return this.level != null && this.level.isDay();
    }

    public static double computeMaxPower(IUpgradeInventory upgrades) {
        return computeMaxPower(upgrades, DataEnergisticsConfiguration.INSTANCE.solarPanel());
    }

    public static double computeMaxPower(IUpgradeInventory upgrades, SolarPanel settings) {
        return ENERGY_CAPACITY + getEnergyCardCount(upgrades) * settings.energyCardCapacityBonusAE();
    }

    public static double applySpeedUpgrades(double baseGeneration, IUpgradeInventory upgrades) {
        return applySpeedUpgrades(baseGeneration, upgrades, DataEnergisticsConfiguration.INSTANCE.solarPanel());
    }

    public static double applySpeedUpgrades(double baseGeneration, IUpgradeInventory upgrades, SolarPanel settings) {
        return baseGeneration * (1.0D + getSpeedCardCount(upgrades) * settings.speedCardBonusRatio());
    }

    public static int getSpeedCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    public static int getEnergyCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.ENERGY_CARD));
    }

    private void pushStoredPowerToGrid() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }

        double available = this.getAECurrentPower();
        if (available <= 0.0001D) {
            return;
        }

        var energyService = node.getGrid().getEnergyService();
        if (energyService == null) {
            return;
        }

        double overflow = energyService.injectPower(available, Actionable.MODULATE);
        double accepted = Math.max(0.0D, available - overflow);
        if (accepted > 0.0001D) {
            this.extractAEPower(accepted, Actionable.MODULATE, PowerMultiplier.ONE);
        }
    }

    private void updateOnlineState() {
        updateBlockState(isOnline());
    }

    private boolean isReceivingRedstonePower() {
        return this.level != null && this.level.hasNeighborSignal(this.worldPosition);
    }

    private void updateBlockState(boolean online) {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataSolarPanelBlock)) {
            return;
        }

        if (state.hasProperty(DataSolarPanelBlock.LIT) && state.getValue(DataSolarPanelBlock.LIT) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataSolarPanelBlock.LIT, online), 3);
        }
    }

    private void onUpgradesChanged() {
        double currentPower = this.getInternalCurrentPower();
        this.setInternalMaxPower(computeMaxPower(this.upgrades));
        if (currentPower > this.getInternalMaxPower()) {
            this.extractAEPower(currentPower - this.getInternalMaxPower(), Actionable.MODULATE, PowerMultiplier.ONE);
        }
        this.saveChanges();
        this.markForClientUpdate();
    }
}
