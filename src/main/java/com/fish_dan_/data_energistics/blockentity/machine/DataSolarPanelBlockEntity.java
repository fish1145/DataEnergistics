package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.block.machine.DataSolarPanelBlock;
import com.fish_dan_.data_energistics.common.solar.energy.SolarEnergyPool;
import com.fish_dan_.data_energistics.common.solar.energy.SolarPanelArray;
import com.fish_dan_.data_energistics.common.solar.energy.SolarPanelArrayPort;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.SolarPanelSchema;
import com.fish_dan_.data_energistics.menu.machine.DataSolarPanelMenuHost;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.config.Actionable;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.orientation.BlockOrientation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataSolarPanelBlockEntity extends AENetworkedPoweredBlockEntity implements IUpgradeableObject, DataSolarPanelMenuHost {

    public static final double ENERGY_CAPACITY = 160_000.0D;
    public static final double ME_DATA_GENERATION_MULTIPLIER = 1.75D;
    public static final int UPGRADE_SLOTS = 3;
    public static final int ME_DATA_UPGRADE_SLOTS = 5;
    public static final int MAX_SPEED_CARDS = 3;
    public static final int MAX_ENERGY_CARDS = 3;
    private static final ResourceLocation SPATIAL_STORAGE_DIMENSION = ResourceLocation.fromNamespaceAndPath("ae2", "spatial_storage");
    private static final ResourceLocation THE_END_DIMENSION = ResourceLocation.withDefaultNamespace("the_end");
    private static final String UPGRADES_TAG = "upgrades";
    private static final String REDSTONE_CONTROLLED_TAG = "redstone_controlled";

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            getUpgradeMachine(),
            getUpgradeSlots(),
            this::onUpgradesChanged);
    private final SolarPanelArray.Membership arrayMembership = new SolarPanelArray.Membership(this, new LocalEnergyCell());
    private boolean redstoneControlled;
    private boolean loadingUpgrades;

    public DataSolarPanelBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(getUpgradeMachine())
                .setIdlePowerUsage(0.0D)
                .addService(IAEPowerStorage.class, new SolarPanelArrayPort(this.arrayMembership));
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
        if (this.level != null && !this.level.isClientSide) {
            DataSolarPanelBlock.refreshLoadedConnections(this.level, this.worldPosition);
            this.arrayMembership.onReady();
        }
        updateOnlineState();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        this.arrayMembership.tick();
        updateOnlineState();
    }

    @Override
    public void setBlockState(BlockState state) {
        BlockState previous = getBlockState();
        super.setBlockState(state);
        if (previous.getBlock() != state.getBlock()) {
            this.arrayMembership.invalidate();
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (DataSolarPanelBlock.connectsOnSide(previous, direction) != DataSolarPanelBlock.connectsOnSide(state, direction)) {
                this.arrayMembership.invalidate();
                return;
            }
        }
    }

    @Override
    public void setRemoved() {
        this.arrayMembership.onUnavailable();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        this.arrayMembership.onUnavailable();
        super.onChunkUnloaded();
    }

    /** Stable internal storage handle, independent of this panel's bottom-only AE node. */
    public SolarPanelArray.Membership energyMembership() {
        return this.arrayMembership;
    }

    @Override
    public SolarEnergyPool.Snapshot getEnergyStorageSnapshot() {
        return this.arrayMembership.snapshot();
    }

    @Override
    protected double funnelPowerIntoStorage(double power, Actionable mode) {
        return power - this.arrayMembership.insert(power, mode);
    }

    @Override
    protected double getFunnelPowerDemand(double maxRequired) {
        SolarEnergyPool.Snapshot storage = getEnergyStorageSnapshot();
        return Math.min(maxRequired, storage.capacity() - storage.stored());
    }

    @Override
    protected double extractAEPower(double amount, Actionable mode) {
        return this.arrayMembership.extract(amount, mode);
    }

    /** Each member keeps its own redstone switch and generation upgrades. */
    public boolean allowsArrayGeneration() {
        return !this.redstoneControlled || isReceivingRedstonePower();
    }

    @Override
    public boolean isOnline() {
        return this.getMainNode().isOnline() && allowsArrayGeneration();
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

        SolarPanelSchema settings = DataEnergisticsConfiguration.INSTANCE.machines.solarPanel;
        double baseGeneration = specialNightGenerationDimension || !this.level.isDay() ? settings.nightGenerationAEPerTick : settings.dayGenerationAEPerTick;
        return applySpeedUpgrades(baseGeneration, this.upgrades, settings) * getGenerationMultiplier();
    }

    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    @Override
    public @Nullable InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return super.getSubInventory(id);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        // Restore the cell's capacity before AE2 loads (and clamps) its saved energy.
        this.loadingUpgrades = true;
        try {
            this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        } finally {
            this.loadingUpgrades = false;
        }
        this.setInternalMaxPower(computeMaxPower(this.upgrades));
        super.loadTag(data, registries);
        this.redstoneControlled = data.getBoolean(REDSTONE_CONTROLLED_TAG);
        this.arrayMembership.invalidate();
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
        return computeMaxPower(upgrades, DataEnergisticsConfiguration.INSTANCE.machines.solarPanel);
    }

    public static double computeMaxPower(IUpgradeInventory upgrades, SolarPanelSchema settings) {
        double capacity = ENERGY_CAPACITY + getEnergyCardCount(upgrades) * settings.energyCardCapacityBonusAE;
        if (!Double.isFinite(capacity) || capacity < 0.0D) {
            throw new IllegalArgumentException("Invalid configured solar capacity: " + capacity);
        }
        return capacity;
    }

    public static double applySpeedUpgrades(double baseGeneration, IUpgradeInventory upgrades) {
        return applySpeedUpgrades(baseGeneration, upgrades, DataEnergisticsConfiguration.INSTANCE.machines.solarPanel);
    }

    public static double applySpeedUpgrades(double baseGeneration, IUpgradeInventory upgrades, SolarPanelSchema settings) {
        return baseGeneration * (1.0D + getSpeedCardCount(upgrades) * settings.speedCardBonusRatio);
    }

    public static int getSpeedCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    public static int getEnergyCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.ENERGY_CARD));
    }

    private ItemLike getUpgradeMachine() {
        return this.getBlockState().getBlock() == DEBlocks.ME_DATA_SOLAR_PANEL.get() ? DEBlocks.ME_DATA_SOLAR_PANEL.get() : DEBlocks.DATA_SOLAR_PANEL.get();
    }

    private int getUpgradeSlots() {
        return this.getBlockState().getBlock() == DEBlocks.ME_DATA_SOLAR_PANEL.get() ? ME_DATA_UPGRADE_SLOTS : UPGRADE_SLOTS;
    }

    private double getGenerationMultiplier() {
        return this.getBlockState().getBlock() == DEBlocks.ME_DATA_SOLAR_PANEL.get() ? ME_DATA_GENERATION_MULTIPLIER : 1.0D;
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
        if (this.loadingUpgrades) {
            return;
        }
        refreshArrayCapacity();
        this.arrayMembership.invalidate();
        this.saveChanges();
        this.markForClientUpdate();
    }

    /** Applies live upgrade/config changes without discarding energy that fits in another member. */
    public void refreshArrayCapacity() {
        double capacity = computeMaxPower(this.upgrades);
        if (capacity != getInternalMaxPower()) {
            this.arrayMembership.beforeCapacityChange(capacity);
            setInternalMaxPower(capacity);
            saveChanges();
        }
    }

    // The pool uses raw local storage. NBT also stays local, so splitting/unloading never duplicates a shared total.
    private final class LocalEnergyCell implements SolarEnergyPool.Cell {

        @Override
        public double stored() {
            return getInternalCurrentPower();
        }

        @Override
        public double capacity() {
            return getInternalMaxPower();
        }

        @Override
        public double insert(double amount, Actionable mode) {
            double offered = Math.min(amount, capacity() - stored());
            double accepted = offered - DataSolarPanelBlockEntity.super.injectAEPower(offered, mode);
            if (mode == Actionable.MODULATE && accepted > 0.0D) {
                saveChanges();
            }
            return accepted;
        }

        @Override
        public double extract(double amount, Actionable mode) {
            double extracted = DataSolarPanelBlockEntity.super.extractAEPower(amount, mode);
            if (mode == Actionable.MODULATE && extracted > 0.0D) {
                saveChanges();
            }
            return extracted;
        }
    }
}
