package com.fish_dan_.data_energistics.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class DataExtractorBlockEntity extends AENetworkedBlockEntity implements IActionHost, IUpgradeableObject {
    public static final int DATA_FLOW_PER_TICK = 100;
    public static final int DAMAGE_PER_TICK = 10;
    public static final double AE_POWER_PER_TICK = 160.0;
    private static final int DEBUFF_DURATION_TICKS = 60;
    private static final int UPGRADE_SLOTS = 6;
    private static final String UPGRADES_TAG = "upgrades";
    private static final String SHOW_RANGE_TAG = "show_range";

    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.DATA_EXTRACTOR.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private boolean showRange;

    public DataExtractorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_EXTRACTOR.get())
                .setIdlePowerUsage(0.0);
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
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.showRange = data.getBoolean(SHOW_RANGE_TAG);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putBoolean(SHOW_RANGE_TAG, this.showRange);
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
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public IGridNode getActionableNode() {
        return this.getMainNode().getNode();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        performWork();
        updateOnlineState();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public int getSpeedCardCount() {
        return this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
    }

    public int getCapacityCardCount() {
        return this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD);
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
            return;
        }

        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }

        var energyService = node.getGrid().getEnergyService();
        double simulated = energyService.extractAEPower(AE_POWER_PER_TICK, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (simulated + 0.0001 < AE_POWER_PER_TICK) {
            return;
        }

        energyService.extractAEPower(AE_POWER_PER_TICK, Actionable.MODULATE, PowerMultiplier.CONFIG);
        applyDebuffsAndDamage(targets);
        var inventory = node.getGrid().getStorageService().getInventory();
        inventory.insert(DataFlowKey.of(), DATA_FLOW_PER_TICK, Actionable.MODULATE, IActionSource.ofMachine(this));
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

    private void applyDebuffsAndDamage(List<LivingEntity> targets) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (LivingEntity entity : targets) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DEBUFF_DURATION_TICKS, 2, false, true, true));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_DURATION_TICKS, 1, false, true, true));
            entity.hurt(serverLevel.damageSources().magic(), DAMAGE_PER_TICK);
        }
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
