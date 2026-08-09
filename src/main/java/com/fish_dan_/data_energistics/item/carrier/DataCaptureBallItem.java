package com.fish_dan_.data_energistics.item.carrier;

import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.DataKeyType;
import com.fish_dan_.data_energistics.entity.DispersingDataEntity;
import com.fish_dan_.data_energistics.recipe.captureball.DataCaptureBallRightClickRecipe;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.Tooltips;
import appeng.util.ConfigInventory;

import java.util.Comparator;
import java.util.List;

public class DataCaptureBallItem extends Item implements IAEItemPowerStorage, IBasicCellItem {

    private static final double MAX_POWER = 50_000.0D;
    private static final double CHARGE_RATE = 50_000.0D;
    private static final double INITIAL_POWER = 1_000.0D;
    public static final double ENERGY_PER_CAPTURE = 1_000.0D;
    private static final int BYTES = 16;
    private static final int BYTES_PER_TYPE = 1;
    private static final int TOTAL_TYPES = 1;
    private static final int MAX_UPGRADES = 3;
    private static final double RANGE_CAPTURE_RADIUS = 3.0D;
    private static final double RANGE_CAPTURE_RADIUS_SQR = RANGE_CAPTURE_RADIUS * RANGE_CAPTURE_RADIUS;

    public DataCaptureBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        lines.add(Tooltips.energyStorageComponent(this.getAECurrentPower(stack), this.getAEMaxPower(stack)));
        this.addCellInformationToTooltip(stack, lines);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp((int) Math.round(this.getAECurrentPower(stack) / this.getAEMaxPower(stack) * 13.0D), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(1.0F / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        this.removeIfDepleted(stack);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (this.isDepleted(stack)) {
            entity.discard();
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (player == null || !this.hasRangeCapture(stack)) {
            return super.useOn(context);
        }

        boolean captured = this.captureNearbyDispersingData(stack, player, context.getClickLocation());
        return captured ? InteractionResult.sidedSuccess(context.getLevel().isClientSide) : super.useOn(context);
    }

    @Override
    public double injectAEPower(ItemStack stack, double amount, Actionable mode) {
        double maxStorage = this.getAEMaxPower(stack);
        double currentStorage = this.getAECurrentPower(stack);
        double required = maxStorage - currentStorage;
        double overflow = Math.max(0.0D, Math.min(amount - required, amount));
        if (mode == Actionable.MODULATE) {
            double toAdd = Math.min(amount, required);
            this.setAECurrentPower(stack, currentStorage + toAdd);
        }
        return overflow;
    }

    @Override
    public double extractAEPower(ItemStack stack, double amount, Actionable mode) {
        double currentStorage = this.getAECurrentPower(stack);
        double fulfillable = Math.min(amount, currentStorage);
        if (mode == Actionable.MODULATE) {
            this.setAECurrentPower(stack, currentStorage - fulfillable);
            this.removeIfDepleted(stack);
        }
        return fulfillable;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return MAX_POWER * getEnergyCapacityMultiplier(stack);
    }

    @Override
    public double getAECurrentPower(ItemStack stack) {
        return stack.getOrDefault(AEComponents.STORED_ENERGY, 0.0D);
    }

    private void setAECurrentPower(ItemStack stack, double power) {
        if (power < 1.0E-4D) {
            stack.remove(AEComponents.STORED_ENERGY);
        } else {
            stack.set(AEComponents.STORED_ENERGY, power);
        }
    }

    @Override
    public AccessRestriction getPowerFlow(ItemStack stack) {
        return AccessRestriction.WRITE;
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return CHARGE_RATE + CHARGE_RATE * Upgrades.getEnergyCardMultiplier(this.getUpgrades(stack));
    }

    @Override
    public AEKeyType getKeyType() {
        return DataKeyType.TYPE;
    }

    @Override
    public int getBytes(ItemStack stack) {
        return BYTES;
    }

    @Override
    public int getBytesPerType(ItemStack stack) {
        return BYTES_PER_TYPE;
    }

    @Override
    public int getTotalTypes(ItemStack stack) {
        return TOTAL_TYPES;
    }

    @Override
    public boolean isBlackListed(ItemStack stack, AEKey requestedAddition) {
        return requestedAddition != DataKey.of();
    }

    @Override
    public double getIdleDrain() {
        return 0.0D;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return ConfigInventory.emptyTypes();
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, MAX_UPGRADES, this::onUpgradesChanged);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode fuzzyMode) {}

    public static ItemStack createChargedStack() {
        ItemStack stack = DEItems.DATA_CAPTURE_BALL.toStack();
        stack.set(AEComponents.STORED_ENERGY, INITIAL_POWER);
        return stack;
    }

    public static ItemStack createConfiguredStack(double energy, long dataAmount) {
        ItemStack stack = DEItems.DATA_CAPTURE_BALL.toStack();
        if (energy > 0.0D) {
            stack.set(AEComponents.STORED_ENERGY, energy);
        }

        if (dataAmount > 0L) {
            var cellInventory = StorageCells.getCellInventory(stack, null);
            if (cellInventory != null) {
                cellInventory.insert(DataKey.of(), dataAmount, Actionable.MODULATE, IActionSource.empty());
            }
        }

        return stack;
    }

    public static float getFillModelValue(ItemStack stack) {
        return getVisualFillStage(stack) / 4.0F;
    }

    public static long getStoredDataAmount(ItemStack stack) {
        return getStoredDataAmountStatic(stack);
    }

    public void clearStoredData(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        if (cellInventory == null) {
            return;
        }

        long stored = getStoredDataAmountStatic(stack);
        if (stored > 0L) {
            cellInventory.extract(DataKey.of(), stored, Actionable.MODULATE, IActionSource.empty());
        }
    }

    public static int getVisualFillStage(ItemStack stack) {
        long storedData = getStoredDataAmountStatic(stack);
        if (storedData <= 0L) {
            return 0;
        }

        if (storedData <= 16L) {
            return 1;
        }
        if (storedData <= 32L) {
            return 2;
        }
        if (storedData <= 64L) {
            return 3;
        }
        return 4;
    }

    public boolean captureDispersingData(ItemStack stack, Player player, DispersingDataEntity dispersingDataEntity) {
        int affordableAmount = (int) Math.min(
                dispersingDataEntity.getDataAmount(),
                Math.floor(this.getAECurrentPower(stack) / ENERGY_PER_CAPTURE));
        if (affordableAmount <= 0) {
            this.removeIfDepleted(stack);
            return false;
        }

        var cellInventory = StorageCells.getCellInventory(stack, null);
        if (cellInventory == null) {
            return false;
        }

        long inserted = cellInventory.insert(
                DataKey.of(),
                affordableAmount,
                Actionable.MODULATE,
                IActionSource.ofPlayer(player));
        if (inserted <= 0) {
            return false;
        }

        this.extractAEPower(stack, ENERGY_PER_CAPTURE * inserted, Actionable.MODULATE);
        if (!player.level().isClientSide()) {
            dispersingDataEntity.removeDataAmount(Math.toIntExact(inserted));
        }
        return true;
    }

    public boolean captureNearbyDispersingData(ItemStack stack, Player player, Vec3 center) {
        if (!this.hasRangeCapture(stack) || !this.canCaptureOne(stack, player)) {
            return false;
        }

        Level level = player.level();
        AABB range = new AABB(
                center.x - RANGE_CAPTURE_RADIUS,
                center.y - RANGE_CAPTURE_RADIUS,
                center.z - RANGE_CAPTURE_RADIUS,
                center.x + RANGE_CAPTURE_RADIUS,
                center.y + RANGE_CAPTURE_RADIUS,
                center.z + RANGE_CAPTURE_RADIUS);
        List<DispersingDataEntity> targets = level.getEntitiesOfClass(DispersingDataEntity.class, range,
                entity -> entity.isAlive() && entity.getBoundingBox().getCenter().distanceToSqr(center) <= RANGE_CAPTURE_RADIUS_SQR);
        if (targets.isEmpty()) {
            return false;
        }

        targets.sort(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().distanceToSqr(center)));
        if (level.isClientSide) {
            return true;
        }

        boolean captured = false;
        for (DispersingDataEntity target : targets) {
            if (this.captureDispersingData(stack, player, target)) {
                captured = true;
            } else if (this.getAECurrentPower(stack) < ENERGY_PER_CAPTURE || !this.canCaptureOne(stack, player)) {
                break;
            }
        }
        return captured;
    }

    public boolean hasRangeCapture(ItemStack stack) {
        return this.getUpgrades(stack).getInstalledUpgrades(AEItems.FUZZY_CARD) > 0;
    }

    public boolean canRunRightClickRecipe(ItemStack stack, DataCaptureBallRightClickRecipe recipe) {
        return this.getAECurrentPower(stack) >= recipe.getEnergyCost() && this.getStoredDataAmount(stack) >= recipe.getDataCost();
    }

    public boolean runRightClickRecipe(ItemStack stack, Player player, DataCaptureBallRightClickRecipe recipe) {
        if (!this.canRunRightClickRecipe(stack, recipe)) {
            return false;
        }

        var cellInventory = StorageCells.getCellInventory(stack, null);
        if (cellInventory == null) {
            return false;
        }

        long dataCost = recipe.getDataCost();
        if (dataCost > 0L) {
            long extracted = cellInventory.extract(DataKey.of(), dataCost, Actionable.MODULATE,
                    IActionSource.ofPlayer(player));
            if (extracted < dataCost) {
                if (extracted > 0L) {
                    cellInventory.insert(DataKey.of(), extracted, Actionable.MODULATE,
                            IActionSource.ofPlayer(player));
                }
                return false;
            }
        }

        double energyCost = recipe.getEnergyCost();
        if (energyCost > 0.0D) {
            double extractedPower = this.extractAEPower(stack, energyCost, Actionable.MODULATE);
            if (Math.abs(extractedPower - energyCost) > 1.0E-4D) {
                if (dataCost > 0L) {
                    cellInventory.insert(DataKey.of(), dataCost, Actionable.MODULATE, IActionSource.ofPlayer(player));
                }
                return false;
            }
        }

        return true;
    }

    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        double maxPower = this.getAEMaxPower(stack);
        if (this.getAECurrentPower(stack) > maxPower) {
            this.setAECurrentPower(stack, maxPower);
        }
    }

    private static int getEnergyCapacityMultiplier(ItemStack stack) {
        return 1 + Upgrades.getEnergyCardMultiplier(UpgradeInventories.forItem(stack, MAX_UPGRADES)) * 2;
    }

    private boolean canCaptureOne(ItemStack stack, Player player) {
        if (this.getAECurrentPower(stack) < ENERGY_PER_CAPTURE) {
            return false;
        }

        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory != null && cellInventory.insert(DataKey.of(), 1, Actionable.SIMULATE, IActionSource.ofPlayer(player)) > 0;
    }

    private static long getStoredDataAmountStatic(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory == null ? 0 : cellInventory.getAvailableStacks().get(DataKey.of());
    }

    private void removeIfDepleted(ItemStack stack) {
        if (this.isDepleted(stack) && !stack.isEmpty()) {
            stack.shrink(1);
        }
    }

    private boolean isDepleted(ItemStack stack) {
        return this.getAECurrentPower(stack) < 1.0E-4D;
    }
}
