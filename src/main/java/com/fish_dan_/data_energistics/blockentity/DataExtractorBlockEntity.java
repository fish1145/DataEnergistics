package com.fish_dan_.data_energistics.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import appeng.api.util.AECableType;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import appeng.util.Platform;

import java.util.List;

public class DataExtractorBlockEntity extends AENetworkedPoweredBlockEntity implements IActionHost, IUpgradeableObject, InternalInventoryHost {
    public static final int BASE_WORK_INTERVAL_SECONDS = 5;
    public static final int MIN_WORK_INTERVAL_SECONDS = 1;
    public static final int WORK_INTERVAL_TICKS = BASE_WORK_INTERVAL_SECONDS * 20;
    public static final int DATA_FLOW_PER_CYCLE = 100;
    public static final int DAMAGE_PER_CYCLE = 5;
    public static final double AE_POWER_PER_TICK = 160.0;
    public static final int ENERGY_CACHE_CAPACITY = 1600;
    public static final int DATA_FLOW_PER_ENERGY_CARD = 200;
    public static final int AE_CACHE_PER_ENERGY_CARD = 100;
    public static final double DATA_FLOW_PER_EXTRA_TARGET_MULTIPLIER = 0.25D;
    public static final int BASE_TARGET_LIMIT = 20;
    public static final int TARGET_LIMIT_PER_CAPACITY_CARD = 5;
    private static final int DEBUFF_DURATION_TICKS = 60;
    private static final int STORAGE_SLOTS = 3;
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int SWORD_SLOT = 2;
    private static final int UPGRADE_SLOTS = 6;
    private static final int BASE_HORIZONTAL_RANGE = 1;
    private static final int BASE_VERTICAL_RANGE = 3;
    private static final int RANGE_PER_CAPACITY_CARD = 2;
    private static final String STORAGE_TAG = "storage";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String REDSTONE_CONTROLLED_TAG = "redstone_controlled";
    private static final String SHOW_RANGE_TAG = "show_range";

    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.DATA_EXTRACTOR.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, STORAGE_SLOTS);
    private boolean redstoneControlled;
    private boolean showRange;
    private int syncedCapacityCardCount;
    private int workTicks;

    public DataExtractorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_EXTRACTOR.get())
                .setIdlePowerUsage(0.0);
        this.setInternalMaxPower(ENERGY_CACHE_CAPACITY);
        this.storage.setMaxStackSize(INPUT_SLOT, 1);
        this.storage.setMaxStackSize(SWORD_SLOT, 1);
        this.storage.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
                return slot == INPUT_SLOT && stack.is(ModItems.DATA_CARRIER.get())
                        || slot == SWORD_SLOT && stack.is(ItemTags.SWORDS);
            }
        });
    }

    @Override
    public AECableType getCableConnectionType(net.minecraft.core.Direction dir) {
        return AECableType.COVERED;
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
        this.syncedCapacityCardCount = computeCapacityCardCount(this.upgrades);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.storage.writeToNBT(data, STORAGE_TAG, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
        data.putBoolean(SHOW_RANGE_TAG, this.showRange);
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

    @Override
    public InternalInventory getInternalInventory() {
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
        if (this.redstoneControlled && !isReceivingRedstonePower()) {
            resetWorkProgress();
            refillEnergyCache();
            updateOnlineState();
            return;
        }
        performWork();
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
        return computeWorkIntervalTicks(this.upgrades);
    }

    public int getWorkIntervalSeconds() {
        return computeWorkIntervalSeconds(this.upgrades);
    }

    public int getDataFlowPerCycle() {
        return getDataFlowPerCycle(getTargetCount());
    }

    public int getDataFlowPerCycle(int targetCount) {
        return computeDataFlowPerCycle(this.upgrades, targetCount);
    }

    public int getTargetLimit() {
        return computeTargetLimit(this.upgrades);
    }

    public boolean isRedstoneControlled() {
        return this.redstoneControlled;
    }

    public int getCapacityCardCount() {
        if (this.level != null && this.level.isClientSide()) {
            return this.syncedCapacityCardCount;
        }
        return computeCapacityCardCount(this.upgrades);
    }

    public static int computeDamagePerCycle(ItemStack sword, @org.jetbrains.annotations.Nullable HolderLookup.Provider registries) {
        return Math.round(DAMAGE_PER_CYCLE + getSwordInheritedDamage(sword) + getStaticSwordEnchantmentDamage(sword, registries));
    }

    public static int computeCapacityCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD));
    }

    public static int computeTargetLimit(IUpgradeInventory upgrades) {
        return BASE_TARGET_LIMIT + computeCapacityCardCount(upgrades) * TARGET_LIMIT_PER_CAPACITY_CARD;
    }

    public static int computeEnergyCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.ENERGY_CARD));
    }

    public static int computeDataFlowPerCycle(IUpgradeInventory upgrades) {
        return DATA_FLOW_PER_CYCLE + computeEnergyCardCount(upgrades) * DATA_FLOW_PER_ENERGY_CARD;
    }

    public static int computeDataFlowPerCycle(IUpgradeInventory upgrades, int targetCount) {
        if (targetCount <= 0) {
            return 0;
        }

        int baseDataFlow = computeDataFlowPerCycle(upgrades);
        double multiplier = 1.0D + Math.max(0, targetCount - 1) * DATA_FLOW_PER_EXTRA_TARGET_MULTIPLIER;
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
        int effectiveSpeedCards = Math.min(Math.max(0, speedCardCount), BASE_WORK_INTERVAL_SECONDS - MIN_WORK_INTERVAL_SECONDS);
        return BASE_WORK_INTERVAL_SECONDS - effectiveSpeedCards;
    }

    public int getTargetCount() {
        return getEntitiesInRange().size();
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

    public AABB getCoverageAabb() {
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
        double localAvailable = this.extractAEPower(AE_POWER_PER_TICK, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        double remaining = Math.max(0.0D, AE_POWER_PER_TICK - localAvailable);
        double gridAvailable = remaining > 0.0D
                ? energyService.extractAEPower(remaining, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                : 0.0D;
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

        ItemStack storedSword = this.storage.getStackInSlot(SWORD_SLOT);
        ItemStack sword = storedSword.copy();
        boolean useSword = sword.is(ItemTags.SWORDS);
        ItemStack carrier = this.storage.getStackInSlot(INPUT_SLOT);
        boolean canCollectBiology = carrier.is(ModItems.DATA_CARRIER.get()) && !BiologyDataCarrierData.isComplete(carrier);
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
                damaged = entity.hurt(serverLevel.damageSources().magic(), DAMAGE_PER_CYCLE);
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
            this.storage.setItemDirect(INPUT_SLOT, carrier.copy());
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

        Player fakePlayer = Platform.getFakePlayer(level, null);
        ItemStack originalMainHand = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack originalOffHand = fakePlayer.getItemInHand(InteractionHand.OFF_HAND);

        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, sword);
        fakePlayer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        fakePlayer.moveTo(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 1.0,
                this.worldPosition.getZ() + 0.5,
                fakePlayer.getYRot(),
                fakePlayer.getXRot()
        );

        DamageSource damageSource = level.damageSources().playerAttack(fakePlayer);
        float totalDamage = DAMAGE_PER_CYCLE + getSwordInheritedDamage(sword);
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
        final double[] addValue = {0.0D};
        final double[] addMultipliedBase = {0.0D};
        final double[] addMultipliedTotal = {0.0D};

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
            @org.jetbrains.annotations.Nullable HolderLookup.Provider registries
    ) {
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

    private record SwordAttackResult(boolean damaged, ItemStack updatedSword) {
    }

    private void onUpgradesChanged() {
        double currentPower = this.getInternalCurrentPower();
        this.setInternalMaxPower(computeEnergyCacheCapacity(this.upgrades));
        if (currentPower > this.getInternalMaxPower()) {
            this.extractAEPower(currentPower - this.getInternalMaxPower(), Actionable.MODULATE, PowerMultiplier.ONE);
        }
        this.saveChanges();
        this.markForClientUpdate();
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
