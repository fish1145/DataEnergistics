package com.fish_dan_.data_energistics.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;

import java.util.List;

public class DataExtractorBlockEntity extends AENetworkedBlockEntity implements IActionHost, IUpgradeableObject, InternalInventoryHost {
    public static final int WORK_INTERVAL_TICKS = 5 * 20;
    public static final int DATA_FLOW_PER_CYCLE = 100;
    public static final int DAMAGE_PER_CYCLE = 5;
    public static final double AE_POWER_PER_TICK = 160.0;
    private static final int DEBUFF_DURATION_TICKS = 60;
    private static final int STORAGE_SLOTS = 2;
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int UPGRADE_SLOTS = 6;
    private static final String STORAGE_TAG = "storage";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String SHOW_RANGE_TAG = "show_range";
    private static final String WORK_PROGRESS_TAG = "work_progress";

    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.DATA_EXTRACTOR.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, STORAGE_SLOTS);
    private boolean showRange;
    private int workTicks;

    public DataExtractorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_EXTRACTOR.get())
                .setIdlePowerUsage(0.0);
        this.storage.setMaxStackSize(INPUT_SLOT, 1);
        this.storage.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
                return slot == INPUT_SLOT && stack.is(ModItems.DATA_CARRIER.get());
            }
        });
    }

    @Override
    public void onReady() {
        super.onReady();
        updateOnlineState();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        updateBlockState(false);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.storage.readFromNBT(data, STORAGE_TAG, registries);
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.showRange = data.getBoolean(SHOW_RANGE_TAG);
        this.workTicks = Math.max(0, data.getInt(WORK_PROGRESS_TAG));
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.storage.writeToNBT(data, STORAGE_TAG, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putBoolean(SHOW_RANGE_TAG, this.showRange);
        data.putInt(WORK_PROGRESS_TAG, this.workTicks);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.showRange);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean showRange = data.readBoolean();
        if (showRange != this.showRange) {
            this.showRange = showRange;
            changed = true;
        }
        return changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.storage) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
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

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
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

        tryOutputCompletedCarrier();
        performWork();
        updateOnlineState();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public int getTargetCount() {
        return getTargets().size();
    }

    public boolean toggleRangeDisplay() {
        this.showRange = !this.showRange;
        this.setChanged();
        this.markForClientUpdate();
        return this.showRange;
    }

    public boolean isRangeDisplayEnabled() {
        return this.showRange;
    }

    public AABB getCoverageAabb() {
        int minX = this.worldPosition.getX() - 1;
        int minY = this.worldPosition.getY() + 1;
        int minZ = this.worldPosition.getZ() - 1;
        int maxX = this.worldPosition.getX() + 2;
        int maxY = this.worldPosition.getY() + 4;
        int maxZ = this.worldPosition.getZ() + 2;

        if (this.level == null) {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return new AABB(
                minX,
                Math.max(this.level.getMinBuildHeight(), minY),
                minZ,
                maxX,
                Math.min(this.level.getMaxBuildHeight(), maxY),
                maxZ
        );
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
        double simulated = energyService.extractAEPower(AE_POWER_PER_TICK, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (simulated + 0.0001 < AE_POWER_PER_TICK) {
            resetWorkProgress();
            return;
        }

        energyService.extractAEPower(AE_POWER_PER_TICK, Actionable.MODULATE, PowerMultiplier.CONFIG);
        applyDebuffs(targets);
        this.workTicks++;
        if (this.workTicks < WORK_INTERVAL_TICKS) {
            return;
        }

        this.workTicks = 0;
        applyDamageAndCollectBiology(targets);
        var inventory = node.getGrid().getStorageService().getInventory();
        inventory.insert(DataFlowKey.of(), DATA_FLOW_PER_CYCLE, Actionable.MODULATE, IActionSource.ofMachine(this));
        tryOutputCompletedCarrier();
    }

    private List<LivingEntity> getTargets() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        return serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                getCoverageAabb(),
                entity -> entity.isAlive() && !(entity instanceof Player));
    }

    private void applyDebuffs(List<LivingEntity> targets) {
        if (!(this.level instanceof ServerLevel)) {
            return;
        }

        for (LivingEntity entity : targets) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DEBUFF_DURATION_TICKS, 2, false, true, true));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_DURATION_TICKS, 1, false, true, true));
        }
    }

    private void applyDamageAndCollectBiology(List<LivingEntity> targets) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack carrier = this.storage.getStackInSlot(INPUT_SLOT);
        boolean canCollectBiology = carrier.is(ModItems.DATA_CARRIER.get()) && !BiologyDataCarrierData.isComplete(carrier);
        ResourceLocation recordedEntityId = canCollectBiology ? BiologyDataCarrierData.getEntityTypeId(carrier) : null;
        float collectedDamage = 0.0F;
        boolean carrierUpdated = false;

        for (LivingEntity entity : targets) {
            float healthBefore = entity.getHealth();
            if (!entity.hurt(serverLevel.damageSources().magic(), DAMAGE_PER_CYCLE)) {
                continue;
            }

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
            this.storage.setItemDirect(INPUT_SLOT, carrier);
        }
    }

    private void resetWorkProgress() {
        this.workTicks = 0;
    }

    private void tryOutputCompletedCarrier() {
        ItemStack input = this.storage.getStackInSlot(INPUT_SLOT);
        if (!input.is(ModItems.DATA_CARRIER.get()) || !BiologyDataCarrierData.isComplete(input)) {
            return;
        }

        ItemStack result = BiologyDataCarrierData.createCompletedCarrier(input);
        ItemStack output = this.storage.getStackInSlot(OUTPUT_SLOT);
        if (!canAcceptCompletedCarrier(output, result)) {
            return;
        }

        if (output.isEmpty()) {
            this.storage.setItemDirect(OUTPUT_SLOT, result);
        } else {
            ItemStack merged = output.copy();
            merged.grow(result.getCount());
            this.storage.setItemDirect(OUTPUT_SLOT, merged);
        }

        this.storage.setItemDirect(INPUT_SLOT, ItemStack.EMPTY);
    }

    private static boolean canAcceptCompletedCarrier(ItemStack output, ItemStack result) {
        return output.isEmpty()
                || ItemStack.isSameItemSameComponents(output, result) && output.getCount() < output.getMaxStackSize();
    }

    private void onUpgradesChanged() {
        this.saveChanges();
    }

    private void updateOnlineState() {
        updateBlockState(this.getMainNode().isOnline());
    }

    private void updateBlockState(boolean online) {
        if (this.level == null) {
            return;
        }

        BlockState state = this.getBlockState();
        if (state.hasProperty(DataExtractorBlock.LIT) && state.getValue(DataExtractorBlock.LIT) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataExtractorBlock.LIT, online), 3);
        }
    }
}
