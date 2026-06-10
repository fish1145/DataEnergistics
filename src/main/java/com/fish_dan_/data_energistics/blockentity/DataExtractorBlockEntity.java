package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.block.DataExtractorBlock.Type;
import com.fish_dan_.data_energistics.config.DataExtractorConfig;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import com.fish_dan_.data_energistics.util.CropDataCarrierData;
import com.fish_dan_.data_energistics.util.MemoryCardSettingsHelper;
import com.fish_dan_.data_energistics.util.OreDataCarrierData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataExtractorBlockEntity extends AENetworkedPoweredBlockEntity
                                      implements IActionHost, IUpgradeableObject, InternalInventoryHost {

    public static final int BASE_WORK_INTERVAL_SECONDS = 5;
    public static final int MIN_WORK_INTERVAL_SECONDS = 1;
    public static final int DROP_COLLECTION_INTERVAL_TICKS = 5;
    public static final int TARGET_SCAN_INTERVAL_TICKS = 5;
    public static final double AE_POWER_PER_TICK = 160.0;
    public static final int ENERGY_CACHE_CAPACITY = 1600;
    public static final int DATA_FLOW_PER_ENERGY_CARD = 200;
    public static final int AE_CACHE_PER_ENERGY_CARD = 100;
    private static final int DEBUFF_DURATION_TICKS = 60;
    private static final int DEBUFF_REAPPLY_INTERVAL_TICKS = 10;
    private static final int STORAGE_SLOTS = 4;
    private static final int CARRIER_SLOT = 0;
    private static final int SWORD_SLOT = 1;
    private static final int ORE_SLOT = 2;
    private static final int CROP_SLOT = 3;
    private static final int UPGRADE_SLOTS = 6;
    private static final int BASE_HORIZONTAL_RANGE = 1;
    private static final int BASE_VERTICAL_RANGE = 3;
    private static final int RANGE_PER_CAPACITY_CARD = 2;
    private static final String ORE_SUFFIX = "_ore";
    private static final String[] ORE_PREFIXES = { "deepslate_", "nether_", "end_" };
    private static final String STORAGE_TAG = "storage";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String REDSTONE_CONTROLLED_TAG = "redstone_controlled";
    private static final String SHOW_RANGE_TAG = "show_range";
    private static final String AUTO_EXPORT_MODE_TAG = "auto_export_mode";
    private static final String OUTPUT_SIDES_TAG = "output_sides";
    private static final String WORK_PROGRESS_TAG = "work_progress";
    private static final TagKey<Item> C_ORES_TAG = ItemTags.create(ResourceLocation.parse("c:ores"));
    private static final TagKey<Item> C_RAW_MATERIALS_TAG = ItemTags.create(ResourceLocation.parse("c:raw_materials"));

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModBlocks.DATA_EXTRACTOR.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, STORAGE_SLOTS);
    private final InternalInventory externalInventory = new FilteredInternalInventory(
            this.storage.getSubInventory(CARRIER_SLOT, CROP_SLOT + 1),
            new IAEItemFilter() {

                @Override
                public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                    return switch (slot) {
                        case CARRIER_SLOT -> stack.is(ModItems.DATA_CARRIER.get());
                        case SWORD_SLOT -> stack.is(ItemTags.SWORDS);
                        case ORE_SLOT -> isOreOrRawOre(stack);
                        case CROP_SLOT -> isSupportedCrop(stack);
                        default -> false;
                    };
                }

                @Override
                public boolean allowExtract(InternalInventory inv, int slot, int amount) {
                    return slot == CARRIER_SLOT && isCompletedCarrier(inv.getStackInSlot(slot));
                }
            });
    private boolean redstoneControlled;
    private boolean showRange;
    private DataExtractorAutoExportMode autoExportMode = DataExtractorAutoExportMode.OFF;
    private int syncedCapacityCardCount;
    private int workTicks;
    private int dropCollectionCooldown;
    private int targetScanCooldown;
    private int debuffCooldown;
    private AABB cachedCoverageAabb;
    private List<LivingEntity> cachedTargets = List.of();
    private final Set<Direction> outputSides = EnumSet.allOf(Direction.class);
    private int cachedCapacityCardCount = -1;
    private int cachedSpeedCardCount = -1;
    private int cachedEnergyCardCount = -1;
    private List<IItemHandler> cachedAdjacentHandlers;
    private boolean adjacentHandlersDirty = true;
    private Player cachedFakePlayer;

    public DataExtractorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_EXTRACTOR.get())
                .setExposedOnSides(getCableExposedSides(blockState))
                .setIdlePowerUsage(0.0);
        this.setInternalMaxPower(ENERGY_CACHE_CAPACITY);
        this.storage.setMaxStackSize(CARRIER_SLOT, 1);
        this.storage.setMaxStackSize(SWORD_SLOT, 1);
        this.storage.setFilter(new IAEItemFilter() {

            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
                return slot == CARRIER_SLOT && stack.is(ModItems.DATA_CARRIER.get()) || slot == SWORD_SLOT && stack.is(ItemTags.SWORDS) || slot == ORE_SLOT && isOreOrRawOre(stack) || slot == CROP_SLOT && isSupportedCrop(stack);
            }
        });
    }

    @Override
    public AECableType getCableConnectionType(net.minecraft.core.Direction dir) {
        if (!isCableSideExposed(dir)) {
            return AECableType.NONE;
        }

        return AECableType.COVERED;
    }

    private boolean isCableSideExposed(Direction dir) {
        Direction front = this.getBlockState().getValue(DataExtractorBlock.FACING);
        return dir != Direction.UP && dir != front;
    }

    private static Set<Direction> getCableExposedSides(BlockState blockState) {
        Direction front = blockState.getValue(DataExtractorBlock.FACING);
        Set<Direction> exposedSides = EnumSet.allOf(Direction.class);
        exposedSides.remove(Direction.UP);
        exposedSides.remove(front);
        return exposedSides;
    }

    @Override
    public void onReady() {
        super.onReady();
        updateOnlineState();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.storage.readFromNBT(data, STORAGE_TAG, registries);
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.redstoneControlled = data.getBoolean(REDSTONE_CONTROLLED_TAG);
        this.showRange = data.getBoolean(SHOW_RANGE_TAG);
        this.autoExportMode = data.contains(AUTO_EXPORT_MODE_TAG) ? DataExtractorAutoExportMode.fromOrdinal(data.getInt(AUTO_EXPORT_MODE_TAG)) : DataExtractorAutoExportMode.OFF;
        this.outputSides.clear();
        if (data.contains(OUTPUT_SIDES_TAG)) {
            for (Tag name : data.getList(OUTPUT_SIDES_TAG, Tag.TAG_STRING)) {
                Direction side = Direction.byName(name.getAsString());
                if (side != null) {
                    this.outputSides.add(side);
                }
            }
        } else {
            this.outputSides.addAll(EnumSet.allOf(Direction.class));
        }
        this.syncedCapacityCardCount = computeCapacityCardCount(this.upgrades);
        this.workTicks = Math.max(0, data.getInt(WORK_PROGRESS_TAG));
        this.dropCollectionCooldown = 0;
        this.targetScanCooldown = 0;
        this.debuffCooldown = 0;
        this.cachedCoverageAabb = null;
        this.cachedTargets = List.of();
        this.cachedCapacityCardCount = -1;
        this.cachedSpeedCardCount = -1;
        this.cachedEnergyCardCount = -1;
        this.adjacentHandlersDirty = true;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.storage.writeToNBT(data, STORAGE_TAG, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
        data.putBoolean(SHOW_RANGE_TAG, this.showRange);
        data.putInt(AUTO_EXPORT_MODE_TAG, this.autoExportMode.ordinal());
        ListTag sides = new ListTag();
        for (Direction side : this.outputSides) {
            sides.add(StringTag.valueOf(side.getName()));
        }
        data.put(OUTPUT_SIDES_TAG, sides);
        data.putInt(WORK_PROGRESS_TAG, this.workTicks);
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = new CompoundTag();
        settings.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
        settings.putBoolean(SHOW_RANGE_TAG, this.showRange);
        settings.putInt(AUTO_EXPORT_MODE_TAG, this.autoExportMode.ordinal());
        settings.putInt(OUTPUT_SIDES_TAG, MemoryCardSettingsHelper.encodeSides(this.outputSides));
        builder.set(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = input.get(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get());
        if (settings != null) {
            applyMemoryCardSettings(settings);
        }
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.showRange);
        data.writeVarInt(getCapacityCardCount());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean showRange = data.readBoolean();
        if (showRange != this.showRange) {
            this.showRange = showRange;
            changed = true;
        }
        int syncedCapacityCardCount = data.readVarInt();
        if (syncedCapacityCardCount != this.syncedCapacityCardCount) {
            this.syncedCapacityCardCount = syncedCapacityCardCount;
            changed = true;
        }
        return changed;
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
        this.storage.clear();
        this.upgrades.clear();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    public AppEngInternalInventory getStorageInventory() {
        return this.storage;
    }

    public InternalInventory getExternalInventory() {
        return this.externalInventory;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.storage;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        updateCarrierTypeState();
        this.saveChanges();
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.STORAGE.equals(id)) {
            return this.storage;
        }
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return super.getSubInventory(id);
    }

    @Override
    public IGridNode getActionableNode() {
        return this.getMainNode().getNode();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        this.adjacentHandlersDirty = true;
        if (this.redstoneControlled && !isReceivingRedstonePower()) {
            resetWorkProgress();
            refillEnergyCache();
            updateOnlineState();
            return;
        }

        recordOreSample();
        recordCropSample();
        tryOutputCompletedCarrier();
        performWork();
        tryAutoExport();
        refillEnergyCache();
        updateOnlineState();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public int getDamagePerCycle() {
        return computeDamagePerCycle(this.storage.getStackInSlot(SWORD_SLOT), this.level != null ? this.level.registryAccess() : null);
    }

    public int getWorkIntervalTicks() {
        return computeWorkIntervalSeconds(getCachedSpeedCardCount()) * 20;
    }

    public int getWorkIntervalSeconds() {
        return computeWorkIntervalSeconds(getCachedSpeedCardCount());
    }

    public int getDataFlowPerCycle() {
        return getDataFlowPerCycle(getTargetCount());
    }

    public int getDataFlowPerCycle(int targetCount) {
        if (targetCount <= 0) {
            return 0;
        }
        int damagePerCycle = getDamagePerCycle();
        int baseDataFlow = DataExtractorConfig.baseDataFlowPerCycle + getCachedEnergyCardCount() * DATA_FLOW_PER_ENERGY_CARD + Math.max(0, damagePerCycle) * DataExtractorConfig.dataFlowPerSwordDamage;
        double multiplier = 1.0D + Math.max(0, targetCount - 1) * DataExtractorConfig.extraTargetDataFlowMultiplier;
        return (int) Math.round(baseDataFlow * multiplier);
    }

    public int getTargetLimit() {
        return DataExtractorConfig.baseTargetLimit + getCapacityCardCount() * DataExtractorConfig.targetLimitPerCapacityCard;
    }

    public boolean isRedstoneControlled() {
        return this.redstoneControlled;
    }

    public int getCapacityCardCount() {
        if (this.level != null && this.level.isClientSide()) {
            return this.syncedCapacityCardCount;
        }
        if (this.cachedCapacityCardCount < 0) {
            this.cachedCapacityCardCount = computeCapacityCardCount(this.upgrades);
        }
        return this.cachedCapacityCardCount;
    }

    private int getCachedSpeedCardCount() {
        if (this.cachedSpeedCardCount < 0) {
            this.cachedSpeedCardCount = Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        }
        return this.cachedSpeedCardCount;
    }

    private int getCachedEnergyCardCount() {
        if (this.cachedEnergyCardCount < 0) {
            this.cachedEnergyCardCount = Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.ENERGY_CARD));
        }
        return this.cachedEnergyCardCount;
    }

    public static int computeDamagePerCycle(ItemStack sword, @org.jetbrains.annotations.Nullable HolderLookup.Provider registries) {
        return Math.round(DataExtractorConfig.baseDamage + getSwordInheritedDamage(sword) + getStaticSwordEnchantmentDamage(sword, registries));
    }

    public static boolean isOreOrRawOre(ItemStack stack) {
        return matchesConfiguredRule(DataExtractorRuleTable.Slot.ORE, stack) || stack.is(C_ORES_TAG) || stack.is(C_RAW_MATERIALS_TAG);
    }

    public static boolean isSupportedCrop(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (matchesConfiguredRule(DataExtractorRuleTable.Slot.CROP, stack)) {
            return true;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CropDataCarrierData.isAllowedCropItem(itemId);
    }

    private static boolean matchesConfiguredRule(DataExtractorRuleTable.Slot slot, ItemStack stack) {
        return DataExtractorRuleTable.hasRuleForSlot(slot, stack);
    }

    public static int computeCapacityCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD));
    }

    public static int computeTargetLimit(IUpgradeInventory upgrades) {
        return DataExtractorConfig.baseTargetLimit + computeCapacityCardCount(upgrades) * DataExtractorConfig.targetLimitPerCapacityCard;
    }

    public static int computeEnergyCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.ENERGY_CARD));
    }

    public static int computeDataFlowPerCycle(IUpgradeInventory upgrades) {
        return computeBaseDataFlowPerCycle(upgrades, DataExtractorConfig.baseDamage);
    }

    public static int computeBaseDataFlowPerCycle(IUpgradeInventory upgrades, int damagePerCycle) {
        return DataExtractorConfig.baseDataFlowPerCycle + computeEnergyCardCount(upgrades) * DATA_FLOW_PER_ENERGY_CARD + Math.max(0, damagePerCycle) * DataExtractorConfig.dataFlowPerSwordDamage;
    }

    public static int computeDataFlowPerCycle(IUpgradeInventory upgrades, int damagePerCycle, int targetCount) {
        if (targetCount <= 0) {
            return 0;
        }

        int baseDataFlow = computeBaseDataFlowPerCycle(upgrades, damagePerCycle);
        double multiplier = 1.0D + Math.max(0, targetCount - 1) * DataExtractorConfig.extraTargetDataFlowMultiplier;
        return (int) Math.round(baseDataFlow * multiplier);
    }

    public static int computeEnergyCacheCapacity(IUpgradeInventory upgrades) {
        return ENERGY_CACHE_CAPACITY + computeEnergyCardCount(upgrades) * AE_CACHE_PER_ENERGY_CARD;
    }

    public static int computeWorkIntervalTicks(IUpgradeInventory upgrades) {
        return computeWorkIntervalSeconds(upgrades) * 20;
    }

    public static int computeWorkIntervalSeconds(IUpgradeInventory upgrades) {
        return computeWorkIntervalSeconds(upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    public static int computeWorkIntervalSeconds(int speedCardCount) {
        int baseWorkIntervalSeconds = Math.max(MIN_WORK_INTERVAL_SECONDS, DataExtractorConfig.workIntervalSeconds);
        int effectiveSpeedCards = Math.min(Math.max(0, speedCardCount), baseWorkIntervalSeconds - MIN_WORK_INTERVAL_SECONDS);
        return baseWorkIntervalSeconds - effectiveSpeedCards;
    }

    public int getTargetCount() {
        return getTargets().size();
    }

    public boolean setRedstoneControlled(boolean enabled) {
        if (this.redstoneControlled != enabled) {
            this.redstoneControlled = enabled;
            this.saveChanges();
        }
        return this.redstoneControlled;
    }

    public boolean toggleRangeDisplay() {
        return setRangeDisplayEnabled(!this.showRange);
    }

    public boolean setRangeDisplayEnabled(boolean enabled) {
        if (this.showRange != enabled) {
            this.showRange = enabled;
            this.setChanged();
            this.markForClientUpdate();
        }
        return this.showRange;
    }

    public boolean isRangeDisplayEnabled() {
        return this.showRange;
    }

    public boolean isAutoExportEnabled() {
        return this.autoExportMode != DataExtractorAutoExportMode.OFF;
    }

    public DataExtractorAutoExportMode getAutoExportMode() {
        return this.autoExportMode;
    }

    public DataExtractorAutoExportMode setAutoExportMode(DataExtractorAutoExportMode mode) {
        DataExtractorAutoExportMode resolvedMode = mode == null ? DataExtractorAutoExportMode.OFF : mode;
        if (this.autoExportMode != resolvedMode) {
            this.autoExportMode = resolvedMode;
            this.saveChanges();
            this.markForClientUpdate();
        }
        return this.autoExportMode;
    }

    public Set<Direction> getOutputSides() {
        if (this.outputSides.isEmpty()) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.copyOf(this.outputSides);
    }

    public void setOutputSideEnabled(Direction side, boolean enabled) {
        boolean changed = enabled ? this.outputSides.add(side) : this.outputSides.remove(side);
        if (!changed) {
            return;
        }

        this.adjacentHandlersDirty = true;
        this.saveChanges();
        this.markForClientUpdate();
    }

    private void applyMemoryCardSettings(CompoundTag settings) {
        boolean changed = false;
        if (settings.contains(REDSTONE_CONTROLLED_TAG)) {
            boolean redstoneControlled = settings.getBoolean(REDSTONE_CONTROLLED_TAG);
            if (this.redstoneControlled != redstoneControlled) {
                this.redstoneControlled = redstoneControlled;
                changed = true;
            }
        }
        if (settings.contains(SHOW_RANGE_TAG)) {
            boolean showRange = settings.getBoolean(SHOW_RANGE_TAG);
            if (this.showRange != showRange) {
                this.showRange = showRange;
                changed = true;
            }
        }
        if (settings.contains(AUTO_EXPORT_MODE_TAG)) {
            DataExtractorAutoExportMode autoExportMode = DataExtractorAutoExportMode.fromOrdinal(settings.getInt(AUTO_EXPORT_MODE_TAG));
            if (this.autoExportMode != autoExportMode) {
                this.autoExportMode = autoExportMode;
                changed = true;
            }
        }
        if (settings.contains(OUTPUT_SIDES_TAG) && MemoryCardSettingsHelper.replaceSides(this.outputSides, settings.getInt(OUTPUT_SIDES_TAG))) {
            this.adjacentHandlersDirty = true;
            changed = true;
        }
        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
        }
    }

    public AABB getCoverageAabb() {
        if (this.cachedCoverageAabb != null) {
            return this.cachedCoverageAabb;
        }

        int capacityCardCount = getCapacityCardCount();
        int horizontalExpansion = capacityCardCount;
        int verticalRange = BASE_VERTICAL_RANGE + capacityCardCount * RANGE_PER_CAPACITY_CARD;

        int minX = this.worldPosition.getX() - BASE_HORIZONTAL_RANGE - horizontalExpansion;
        int minY = this.worldPosition.getY() + 1;
        int minZ = this.worldPosition.getZ() - BASE_HORIZONTAL_RANGE - horizontalExpansion;
        int maxX = this.worldPosition.getX() + BASE_HORIZONTAL_RANGE + horizontalExpansion + 1;
        int maxY = this.worldPosition.getY() + verticalRange + 1;
        int maxZ = this.worldPosition.getZ() + BASE_HORIZONTAL_RANGE + horizontalExpansion + 1;

        if (this.level == null) {
            this.cachedCoverageAabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            return this.cachedCoverageAabb;
        }

        this.cachedCoverageAabb = new AABB(
                minX,
                Math.max(this.level.getMinBuildHeight(), minY),
                minZ,
                maxX,
                Math.min(this.level.getMaxBuildHeight(), maxY),
                maxZ);
        return this.cachedCoverageAabb;
    }

    private void performWork() {
        List<LivingEntity> targets = getTargets();
        if (targets.isEmpty()) {
            resetWorkProgress();
            return;
        }

        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            resetWorkProgress();
            return;
        }

        var energyService = node.getGrid().getEnergyService();
        double localAvailable = this.extractAEPower(AE_POWER_PER_TICK, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        double remaining = Math.max(0.0D, AE_POWER_PER_TICK - localAvailable);
        double gridAvailable = remaining > 0.0D ? energyService.extractAEPower(remaining, Actionable.SIMULATE, PowerMultiplier.CONFIG) : 0.0D;
        if (localAvailable + gridAvailable + 0.0001 < AE_POWER_PER_TICK) {
            resetWorkProgress();
            return;
        }

        if (localAvailable > 0.0D) {
            this.extractAEPower(localAvailable, Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
        if (remaining > 0.0D) {
            energyService.extractAEPower(remaining, Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
        applyDebuffs(targets);
        this.workTicks++;
        if (this.workTicks < getWorkIntervalTicks()) {
            return;
        }

        this.workTicks = 0;
        applyDamageAndCollectBiology(targets);
        var inventory = node.getGrid().getStorageService().getInventory();
        inventory.insert(DataFlowKey.of(), getDataFlowPerCycle(targets.size()), Actionable.MODULATE, IActionSource.ofMachine(this));
        tryOutputCompletedCarrier();
    }

    private List<LivingEntity> getTargets() {
        List<LivingEntity> targets = getEntitiesInRange();
        int targetLimit = getTargetLimit();
        if (targets.size() > targetLimit) {
            return List.copyOf(targets.subList(0, targetLimit));
        }
        return targets;
    }

    private List<LivingEntity> getEntitiesInRange() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        if (this.targetScanCooldown > 0) {
            this.targetScanCooldown--;
        } else {
            this.cachedTargets = List.copyOf(serverLevel.getEntitiesOfClass(
                    LivingEntity.class,
                    getCoverageAabb(),
                    entity -> entity.isAlive() && !(entity instanceof Player)));
            this.targetScanCooldown = TARGET_SCAN_INTERVAL_TICKS - 1;
        }

        return this.cachedTargets;
    }

    private void applyDebuffs(List<LivingEntity> targets) {
        if (!(this.level instanceof ServerLevel)) {
            return;
        }

        if (this.debuffCooldown > 0) {
            this.debuffCooldown--;
            return;
        }

        this.debuffCooldown = DEBUFF_REAPPLY_INTERVAL_TICKS - 1;

        for (LivingEntity entity : targets) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DEBUFF_DURATION_TICKS, 2, false, true, true));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_DURATION_TICKS, 1, false, true, true));
        }
    }

    private void applyDamageAndCollectBiology(List<LivingEntity> targets) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack storedSword = this.storage.getStackInSlot(SWORD_SLOT);
        ItemStack sword = storedSword.copy();
        boolean useSword = sword.is(ItemTags.SWORDS);
        ItemStack carrier = this.storage.getStackInSlot(CARRIER_SLOT);
        boolean canCollectBiology = carrier.is(ModItems.DATA_CARRIER.get()) && !BiologyDataCarrierData.isComplete(carrier) && !OreDataCarrierData.hasRecordedOre(carrier);
        ResourceLocation recordedEntityId = canCollectBiology ? BiologyDataCarrierData.getEntityTypeId(carrier) : null;
        float collectedDamage = 0.0F;
        boolean carrierUpdated = false;

        for (LivingEntity entity : targets) {
            float healthBefore = entity.getHealth();
            boolean damaged;
            if (useSword) {
                SwordAttackResult attackResult = attackWithSword(serverLevel, sword, entity);
                damaged = attackResult.damaged();
                sword = attackResult.updatedSword();
            } else {
                damaged = entity.hurt(serverLevel.damageSources().magic(), DataExtractorConfig.baseDamage);
            }
            if (!damaged) {
                continue;
            }

            clearAggro(entity);

            float damageDealt = Math.max(0.0F, healthBefore - entity.getHealth());
            if (!canCollectBiology || damageDealt <= 0.0F) {
                continue;
            }

            if (recordedEntityId == null && BiologyDataCarrierData.recordFirstEntity(carrier, entity)) {
                recordedEntityId = BiologyDataCarrierData.getEntityTypeId(carrier);
                carrierUpdated = true;
            }

            if (recordedEntityId != null && recordedEntityId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
                collectedDamage += damageDealt;
            }
        }

        if (canCollectBiology && collectedDamage > 0.0F && BiologyDataCarrierData.addCollectedDamage(carrier, collectedDamage)) {
            carrierUpdated = true;
        }

        if (carrierUpdated) {
            this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
        }

        if (useSword && !ItemStack.matches(storedSword, sword)) {
            this.storage.setItemDirect(SWORD_SLOT, sword);
        }
    }

    private void refillEnergyCache() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }

        double missing = this.getInternalMaxPower() - this.getInternalCurrentPower();
        if (missing <= 0.0001D) {
            return;
        }

        double extracted = node.getGrid().getEnergyService().extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 0.0D) {
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        }
    }

    private void resetWorkProgress() {
        this.workTicks = 0;
    }

    private void recordOreSample() {
        ItemStack carrier = this.storage.getStackInSlot(CARRIER_SLOT);
        if (!carrier.is(ModItems.DATA_CARRIER.get()) || BiologyDataCarrierData.hasRecordedEntity(carrier) || OreDataCarrierData.isComplete(carrier) || CropDataCarrierData.hasRecordedCrop(carrier)) {
            return;
        }

        ItemStack oreStack = this.storage.getStackInSlot(ORE_SLOT);
        if (oreStack.isEmpty() || !isOreOrRawOre(oreStack)) {
            return;
        }

        DataExtractorRuleTable.ItemRule configuredRule = DataExtractorRuleTable.findRule(DataExtractorRuleTable.Slot.ORE, oreStack);
        if (configuredRule != null && configuredRule.dataType() == DataExtractorRuleTable.DataType.ORE) {
            recordConfiguredOreSample(carrier, oreStack, configuredRule);
            return;
        }

        boolean updated = false;
        if (!OreDataCarrierData.hasRecordedOre(carrier)) {
            ItemStack recordedStack = resolveRecordedOreStack(oreStack);
            if (recordedStack.isEmpty()) {
                return;
            }
            updated = OreDataCarrierData.recordFirstOre(carrier, recordedStack);
            if (!updated) {
                return;
            }
        }

        ResourceLocation recordedOreId = OreDataCarrierData.getOreItemId(carrier);
        ItemStack recordedStack = resolveRecordedOreStack(oreStack);
        ResourceLocation currentRecordedId = recordedStack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(recordedStack.getItem());
        if (recordedOreId == null || currentRecordedId == null || !recordedOreId.equals(currentRecordedId)) {
            if (updated) {
                this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
            }
            return;
        }

        float remaining = Math.max(0.0F, OreDataCarrierData.getRequiredAmount(carrier) - OreDataCarrierData.getCollectedAmount(carrier));
        float amountPerItem = getRecordedAmountPerOreItem(oreStack);
        int itemCountToConsume = amountPerItem <= 0.0F ? 0 : Math.min(oreStack.getCount(), (int) Math.ceil(remaining / amountPerItem));
        float toRecord = Math.min(remaining, itemCountToConsume * amountPerItem);
        if (itemCountToConsume > 0 && toRecord > 0.0F) {
            updated = OreDataCarrierData.addCollectedOre(carrier, toRecord) || updated;
            ItemStack newOreStack = oreStack.copy();
            newOreStack.shrink(itemCountToConsume);
            this.storage.setItemDirect(ORE_SLOT, newOreStack);
        }

        if (updated) {
            this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
        }
    }

    private void recordCropSample() {
        ItemStack carrier = this.storage.getStackInSlot(CARRIER_SLOT);
        if (!carrier.is(ModItems.DATA_CARRIER.get()) || BiologyDataCarrierData.hasRecordedEntity(carrier) || OreDataCarrierData.hasRecordedOre(carrier) || CropDataCarrierData.isComplete(carrier)) {
            return;
        }

        ItemStack cropStack = this.storage.getStackInSlot(CROP_SLOT);
        if (cropStack.isEmpty() || !isSupportedCrop(cropStack)) {
            return;
        }

        DataExtractorRuleTable.ItemRule configuredRule = DataExtractorRuleTable.findRule(DataExtractorRuleTable.Slot.CROP, cropStack);
        if (configuredRule != null && configuredRule.dataType() == DataExtractorRuleTable.DataType.CROP) {
            recordConfiguredCropSample(carrier, cropStack, configuredRule);
            return;
        }

        boolean updated = false;
        if (!CropDataCarrierData.hasRecordedCrop(carrier)) {
            ItemStack recordedStack = cropStack.copyWithCount(1);
            updated = CropDataCarrierData.recordFirstCrop(carrier, recordedStack);
            if (!updated) {
                return;
            }
        }

        ResourceLocation recordedCropId = CropDataCarrierData.getCropItemId(carrier);
        ResourceLocation currentCropId = CropDataCarrierData.getRecordedCropItemId(cropStack);
        if (recordedCropId == null || currentCropId == null || !recordedCropId.equals(currentCropId)) {
            if (updated) {
                this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
            }
            return;
        }

        float remaining = Math.max(0.0F, CropDataCarrierData.getRequiredAmount(carrier) - CropDataCarrierData.getCollectedAmount(carrier));
        float progressPerItem = CropDataCarrierData.getCropProgressValue(cropStack);
        if (progressPerItem <= 0.0F) {
            if (updated) {
                this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
            }
            return;
        }
        int itemCountToConsume = Math.min(cropStack.getCount(), (int) Math.ceil(remaining / progressPerItem));
        float toRecord = Math.min(remaining, itemCountToConsume * progressPerItem);
        if (itemCountToConsume > 0 && toRecord > 0.0F) {
            updated = CropDataCarrierData.addCollectedCrop(carrier, toRecord) || updated;
            ItemStack newCropStack = cropStack.copy();
            newCropStack.shrink(itemCountToConsume);
            this.storage.setItemDirect(CROP_SLOT, newCropStack);
        }

        if (updated) {
            this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
        }
    }

    private void recordConfiguredOreSample(ItemStack carrier, ItemStack oreStack, DataExtractorRuleTable.ItemRule rule) {
        boolean updated = false;
        if (!OreDataCarrierData.hasRecordedOre(carrier)) {
            updated = OreDataCarrierData.recordFirstOre(carrier, new ItemStack(BuiltInRegistries.ITEM.get(rule.recordedItemId())));
            if (!updated) {
                return;
            }
            OreDataCarrierData.setRequiredAmount(carrier, rule.requiredAmount());
        }

        ResourceLocation recordedOreId = OreDataCarrierData.getOreItemId(carrier);
        if (recordedOreId == null || !recordedOreId.equals(rule.recordedItemId())) {
            if (updated) {
                this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
            }
            return;
        }

        float remaining = Math.max(0.0F, OreDataCarrierData.getRequiredAmount(carrier) - OreDataCarrierData.getCollectedAmount(carrier));
        int itemCountToConsume = Math.min(oreStack.getCount(), (int) Math.ceil(remaining / rule.progressPerItem()));
        float toRecord = Math.min(remaining, itemCountToConsume * rule.progressPerItem());
        if (itemCountToConsume > 0 && toRecord > 0.0F) {
            updated = OreDataCarrierData.addCollectedOre(carrier, toRecord) || updated;
            ItemStack newOreStack = oreStack.copy();
            newOreStack.shrink(itemCountToConsume);
            this.storage.setItemDirect(ORE_SLOT, newOreStack);
        }

        if (updated) {
            this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
        }
    }

    private void recordConfiguredCropSample(ItemStack carrier, ItemStack cropStack, DataExtractorRuleTable.ItemRule rule) {
        boolean updated = false;
        if (!CropDataCarrierData.hasRecordedCrop(carrier)) {
            updated = CropDataCarrierData.recordFirstCrop(carrier, new ItemStack(BuiltInRegistries.ITEM.get(rule.recordedItemId())));
            if (!updated) {
                return;
            }
            CropDataCarrierData.setRequiredAmount(carrier, rule.requiredAmount());
        }

        ResourceLocation recordedCropId = CropDataCarrierData.getCropItemId(carrier);
        if (recordedCropId == null || !recordedCropId.equals(rule.recordedItemId())) {
            if (updated) {
                this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
            }
            return;
        }

        float remaining = Math.max(0.0F, CropDataCarrierData.getRequiredAmount(carrier) - CropDataCarrierData.getCollectedAmount(carrier));
        int itemCountToConsume = Math.min(cropStack.getCount(), (int) Math.ceil(remaining / rule.progressPerItem()));
        float toRecord = Math.min(remaining, itemCountToConsume * rule.progressPerItem());
        if (itemCountToConsume > 0 && toRecord > 0.0F) {
            updated = CropDataCarrierData.addCollectedCrop(carrier, toRecord) || updated;
            ItemStack newCropStack = cropStack.copy();
            newCropStack.shrink(itemCountToConsume);
            this.storage.setItemDirect(CROP_SLOT, newCropStack);
        }

        if (updated) {
            this.storage.setItemDirect(CARRIER_SLOT, carrier.copy());
        }
    }

    private ItemStack resolveRecordedOreStack(ItemStack oreStack) {
        if (oreStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Item baseOreItem = resolveBaseOreItem(oreStack);
        if (baseOreItem != null) {
            return new ItemStack(baseOreItem);
        }

        if (oreStack.is(C_ORES_TAG)) {
            return oreStack;
        }

        if (oreStack.is(C_RAW_MATERIALS_TAG)) {
            return oreStack;
        }

        return oreStack;
    }

    private float getRecordedAmountPerOreItem(ItemStack oreStack) {
        if (oreStack.isEmpty()) {
            return 0.0F;
        }

        if (oreStack.is(C_RAW_MATERIALS_TAG)) {
            return 0.5F;
        }

        return oreStack.is(C_ORES_TAG) ? 1.0F : 0.0F;
    }

    private Item resolveBaseOreItem(ItemStack oreStack) {
        ResourceLocation oreItemId = BuiltInRegistries.ITEM.getKey(oreStack.getItem());
        if (oreItemId == null) {
            return null;
        }

        String path = oreItemId.getPath();
        if (oreStack.is(C_RAW_MATERIALS_TAG)) {
            if (!path.startsWith("raw_")) {
                return null;
            }

            String material = path.substring("raw_".length());
            if (material.isEmpty()) {
                return null;
            }

            ResourceLocation baseOreId = ResourceLocation.fromNamespaceAndPath(oreItemId.getNamespace(), material + ORE_SUFFIX);
            return BuiltInRegistries.ITEM.getOptional(baseOreId).orElse(null);
        }

        if (!oreStack.is(C_ORES_TAG)) {
            return null;
        }

        for (String prefix : ORE_PREFIXES) {
            if (!path.startsWith(prefix)) {
                continue;
            }

            ResourceLocation baseOreId = ResourceLocation.fromNamespaceAndPath(oreItemId.getNamespace(), path.substring(prefix.length()));
            Item baseOreItem = BuiltInRegistries.ITEM.getOptional(baseOreId).orElse(null);
            if (baseOreItem != null) {
                return baseOreItem;
            }
        }

        return null;
    }

    private void tryOutputCompletedCarrier() {
        ItemStack input = this.storage.getStackInSlot(CARRIER_SLOT);
        if (!input.is(ModItems.DATA_CARRIER.get())) {
            return;
        }

        ItemStack result;
        if (BiologyDataCarrierData.isComplete(input)) {
            result = BiologyDataCarrierData.createCompletedCarrier(input);
        } else if (CropDataCarrierData.isComplete(input)) {
            result = CropDataCarrierData.createCompletedCarrier(input);
        } else if (OreDataCarrierData.isComplete(input)) {
            result = OreDataCarrierData.createCompletedCarrier(input);
        } else {
            return;
        }
        this.storage.setItemDirect(CARRIER_SLOT, result);
    }

    private void tryAutoExport() {
        if (this.autoExportMode == DataExtractorAutoExportMode.OFF) {
            this.dropCollectionCooldown = 0;
            return;
        }

        if (this.autoExportMode == DataExtractorAutoExportMode.CONTAINER) {
            List<IItemHandler> adjacentHandlers = getAdjacentItemHandlers();
            if (adjacentHandlers.isEmpty()) {
                this.dropCollectionCooldown = 0;
            } else {
                exportCompletedCarrier(adjacentHandlers, null);
                tickDroppedItemCollection(adjacentHandlers, null);
            }
            return;
        }

        MEStorage networkStorage = getConnectedItemNetwork();
        exportCompletedCarrier(List.of(), networkStorage);
        tickDroppedItemCollection(List.of(), networkStorage);
    }

    private void exportCompletedCarrier(List<IItemHandler> adjacentHandlers, @org.jetbrains.annotations.Nullable MEStorage networkStorage) {
        ItemStack carrier = this.storage.getStackInSlot(CARRIER_SLOT);
        if (!isCompletedCarrier(carrier)) {
            return;
        }

        ItemStack remaining = routeAutoExportItem(carrier, adjacentHandlers, networkStorage);
        if (remaining.getCount() == carrier.getCount()) {
            return;
        }

        this.storage.setItemDirect(CARRIER_SLOT, remaining);
        this.saveChanges();
        this.markForClientUpdate();
    }

    private void tickDroppedItemCollection(List<IItemHandler> adjacentHandlers, @org.jetbrains.annotations.Nullable MEStorage networkStorage) {
        if (this.dropCollectionCooldown > 0) {
            this.dropCollectionCooldown--;
            return;
        }

        this.dropCollectionCooldown = DROP_COLLECTION_INTERVAL_TICKS - 1;
        collectAndExportDroppedItems(adjacentHandlers, networkStorage);
    }

    private void collectAndExportDroppedItems(List<IItemHandler> adjacentHandlers, @org.jetbrains.annotations.Nullable MEStorage networkStorage) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.autoExportMode != DataExtractorAutoExportMode.AE && adjacentHandlers.isEmpty()) {
            return;
        }

        for (ItemEntity itemEntity : serverLevel.getEntitiesOfClass(
                ItemEntity.class,
                getCoverageAabb(),
                entity -> entity.isAlive() && !entity.getItem().isEmpty())) {
            ItemStack currentStack = itemEntity.getItem();
            int originalCount = currentStack.getCount();
            ItemStack remaining = routeAutoExportItem(currentStack, adjacentHandlers, networkStorage);
            if (remaining.getCount() >= originalCount) {
                continue;
            }

            if (remaining.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remaining);
            }
        }
    }

    private ItemStack routeAutoExportItem(ItemStack stack, List<IItemHandler> adjacentHandlers, @org.jetbrains.annotations.Nullable MEStorage networkStorage) {
        return this.autoExportMode == DataExtractorAutoExportMode.AE ? insertIntoNetwork(stack, networkStorage) : insertIntoAdjacentHandlers(stack, adjacentHandlers);
    }

    private ItemStack insertIntoAdjacentHandlers(ItemStack stack, List<IItemHandler> adjacentHandlers) {
        ItemStack remaining = stack.copy();
        for (IItemHandler handler : adjacentHandlers) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
        }
        return remaining;
    }

    private ItemStack insertIntoNetwork(ItemStack stack, @org.jetbrains.annotations.Nullable MEStorage networkStorage) {
        if (stack.isEmpty() || networkStorage == null) {
            return stack;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return stack;
        }

        long inserted = networkStorage.insert(key, stack.getCount(), Actionable.MODULATE, IActionSource.ofMachine(this));
        if (inserted <= 0) {
            return stack;
        }

        ItemStack remaining = stack.copy();
        remaining.shrink((int) Math.min(inserted, stack.getCount()));
        return remaining;
    }

    private List<IItemHandler> getAdjacentItemHandlers() {
        if (!this.adjacentHandlersDirty && this.cachedAdjacentHandlers != null) {
            return this.cachedAdjacentHandlers;
        }
        this.adjacentHandlersDirty = false;
        if (this.level == null) {
            this.cachedAdjacentHandlers = List.of();
            return List.of();
        }

        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction direction : this.outputSides) {
            BlockPos targetPos = this.worldPosition.relative(direction);
            BlockState targetState = this.level.getBlockState(targetPos);
            if (targetState.isAir()) {
                continue;
            }

            IItemHandler handler = this.level.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    targetPos,
                    targetState,
                    this.level.getBlockEntity(targetPos),
                    direction.getOpposite());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        this.cachedAdjacentHandlers = handlers.isEmpty() ? List.of() : List.copyOf(handlers);
        return this.cachedAdjacentHandlers;
    }

    @org.jetbrains.annotations.Nullable
    private MEStorage getConnectedItemNetwork() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return null;
        }

        var storageService = node.getGrid().getStorageService();
        return storageService == null ? null : storageService.getInventory();
    }

    private static boolean isCompletedCarrier(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return stack.is(ModItems.MOB_DATA_CARRIER.get()) || stack.is(ModItems.ORE_DATA_CARRIER.get()) || stack.is(ModItems.CROP_DATA_CARRIER.get()) || stack.is(ModItems.DATA_CARRIER.get()) && (BiologyDataCarrierData.isComplete(stack) || OreDataCarrierData.isComplete(stack) || CropDataCarrierData.isComplete(stack));
    }

    private void updateCarrierTypeState() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataExtractorBlock) || !state.hasProperty(DataExtractorBlock.TYPE)) {
            return;
        }

        Type expectedType = resolveCarrierType(this.storage.getStackInSlot(CARRIER_SLOT));
        if (state.getValue(DataExtractorBlock.TYPE) != expectedType) {
            this.level.setBlock(this.worldPosition, state.setValue(DataExtractorBlock.TYPE, expectedType), 3);
        }
    }

    private static Type resolveCarrierType(ItemStack stack) {
        if (stack.isEmpty()) {
            return Type.NONE;
        }
        if (stack.is(ModItems.DATA_CARRIER.get())) {
            return Type.EMPTY;
        }
        if (stack.is(ModItems.MOB_DATA_CARRIER.get())) {
            return Type.MOB;
        }
        if (stack.is(ModItems.CROP_DATA_CARRIER.get())) {
            return Type.CROP;
        }
        if (stack.is(ModItems.ORE_DATA_CARRIER.get())) {
            return Type.ORE;
        }
        return Type.NONE;
    }

    private void clearAggro(LivingEntity entity) {
        entity.setLastHurtByPlayer(null);
        entity.setLastHurtByMob(null);
        entity.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
        entity.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        entity.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        entity.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE);
        entity.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
        entity.getBrain().eraseMemory(MemoryModuleType.AVOID_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        entity.getBrain().eraseMemory(MemoryModuleType.DISTURBANCE_LOCATION);
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        if (entity instanceof NeutralMob neutralMob) {
            neutralMob.stopBeingAngry();
        }
    }

    private SwordAttackResult attackWithSword(ServerLevel level, ItemStack sword, LivingEntity target) {
        if (!sword.is(ItemTags.SWORDS) || sword.isEmpty() || !target.isAlive()) {
            return new SwordAttackResult(false, sword);
        }

        Player fakePlayer = this.cachedFakePlayer;
        if (fakePlayer == null) {
            fakePlayer = Platform.getFakePlayer(level, null);
            this.cachedFakePlayer = fakePlayer;
        }
        ItemStack originalMainHand = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack originalOffHand = fakePlayer.getItemInHand(InteractionHand.OFF_HAND);

        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, sword);
        fakePlayer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        fakePlayer.moveTo(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 1.0,
                this.worldPosition.getZ() + 0.5,
                fakePlayer.getYRot(),
                fakePlayer.getXRot());

        DamageSource damageSource = level.damageSources().playerAttack(fakePlayer);
        float totalDamage = DataExtractorConfig.baseDamage + getSwordInheritedDamage(sword);
        totalDamage += sword.getItem().getAttackDamageBonus(target, totalDamage, damageSource);
        totalDamage = EnchantmentHelper.modifyDamage(level, sword, target, damageSource, totalDamage);

        float healthBefore = target.getHealth();
        boolean damaged = totalDamage > 0.0F && target.hurt(damageSource, totalDamage);

        boolean hurtEnemy = false;
        if (damaged && !sword.isEmpty()) {
            hurtEnemy = sword.hurtEnemy(target, fakePlayer);
            EnchantmentHelper.doPostAttackEffects(level, target, damageSource);
            if (hurtEnemy && !sword.isEmpty()) {
                sword.postHurtEnemy(target, fakePlayer);
            }
        }

        ItemStack updatedSword = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND).copy();

        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, originalMainHand);
        fakePlayer.setItemInHand(InteractionHand.OFF_HAND, originalOffHand);

        return new SwordAttackResult(damaged && (target.getHealth() + 0.0001F < healthBefore || !target.isAlive()), updatedSword);
    }

    public static float getSwordInheritedDamage(ItemStack sword) {
        if (!sword.is(ItemTags.SWORDS) || sword.isEmpty()) {
            return 0.0F;
        }

        final double playerBaseDamage = 1.0D;
        final double[] addValue = { 0.0D };
        final double[] addMultipliedBase = { 0.0D };
        final double[] addMultipliedTotal = { 0.0D };

        sword.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
                return;
            }

            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                addValue[0] += modifier.amount();
            } else if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                addMultipliedBase[0] += modifier.amount();
            } else if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                addMultipliedTotal[0] += modifier.amount();
            }
        });

        double damage = playerBaseDamage + addValue[0];
        damage += playerBaseDamage * addMultipliedBase[0];
        damage *= 1.0D + addMultipliedTotal[0];
        return Math.max(0.0F, (float) damage);
    }

    public static float getStaticSwordEnchantmentDamage(
                                                        ItemStack sword,
                                                        @org.jetbrains.annotations.Nullable HolderLookup.Provider registries) {
        if (!sword.is(ItemTags.SWORDS) || sword.isEmpty() || registries == null) {
            return 0.0F;
        }

        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        int sharpnessLevel = sword.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.SHARPNESS));
        if (sharpnessLevel <= 0) {
            return 0.0F;
        }

        return 1.0F + 0.5F * (sharpnessLevel - 1);
    }

    private record SwordAttackResult(boolean damaged, ItemStack updatedSword) {}

    private void onUpgradesChanged() {
        this.cachedCapacityCardCount = -1;
        this.cachedSpeedCardCount = -1;
        this.cachedEnergyCardCount = -1;
        double currentPower = this.getInternalCurrentPower();
        this.setInternalMaxPower(computeEnergyCacheCapacity(this.upgrades));
        if (currentPower > this.getInternalMaxPower()) {
            this.extractAEPower(currentPower - this.getInternalMaxPower(), Actionable.MODULATE, PowerMultiplier.ONE);
        }
        invalidateTargetCache();
        this.saveChanges();
        this.markForClientUpdate();
    }

    private void invalidateTargetCache() {
        this.targetScanCooldown = 0;
        this.debuffCooldown = 0;
        this.cachedCoverageAabb = null;
        this.cachedTargets = List.of();
    }

    private void updateOnlineState() {
        updateBlockState(this.getMainNode().isOnline());
    }

    private boolean isReceivingRedstonePower() {
        return this.level != null && this.level.hasNeighborSignal(this.worldPosition);
    }

    private void updateBlockState(boolean online) {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataExtractorBlock)) {
            return;
        }

        if (state.hasProperty(DataExtractorBlock.LIT) && state.getValue(DataExtractorBlock.LIT) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataExtractorBlock.LIT, online), 3);
        }
    }
}
