package com.fish_dan_.data_energistics.part;

import appeng.api.config.Actionable;
import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigManagerBuilder;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import com.fish_dan_.data_energistics.Config;
import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataRipperSettings;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.util.DataRipperConfigParsingUtils;
import com.fish_dan_.data_energistics.util.DataRipperPowerUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class DataRipperPart extends UpgradeablePart implements IGridTickable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation MODEL_BASE =
            ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/data_ripper_base");

    @PartModels
    private static final PartModel MODELS_OFF;
    @PartModels
    private static final PartModel MODELS_ON;
    @PartModels
    private static final PartModel MODELS_HAS_CHANNEL;

    static {
        MODELS_OFF = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/data_ripper_off"));
        MODELS_ON = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/data_ripper_on"));
        MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/data_ripper_has_channel"));
    }

    private YesNo networkEnergySufficient = YesNo.YES;

    public DataRipperPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(2.0)
                .addService(IGridTickable.class, this);
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(DataRipperSettings.ACCELERATE, YesNo.YES);
        builder.registerSetting(DataRipperSettings.REDSTONE_CONTROL, YesNo.NO);
    }

    @Override
    protected int getUpgradeSlots() {
        return 8;
    }

    @Override
    protected boolean isSleeping() {
        if (this.getConfigManager().getSetting(DataRipperSettings.ACCELERATE) != YesNo.YES) {
            return true;
        }

        if (this.getConfigManager().getSetting(DataRipperSettings.REDSTONE_CONTROL) != YesNo.YES) {
            return false;
        }

        return !this.getHost().hasRedstone();
    }

    @Override
    protected void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        var mainNode = this.getMainNode();
        if (mainNode == null || mainNode.getGrid() == null || mainNode.getNode() == null) {
            return;
        }

        if (this.isSleeping()) {
            mainNode.getGrid().getTickManager().sleepDevice(mainNode.getNode());
        } else {
            mainNode.getGrid().getTickManager().wakeDevice(mainNode.getNode());
        }
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        if (this.getConfigManager().getSetting(DataRipperSettings.REDSTONE_CONTROL) != YesNo.YES) {
            return;
        }

        var mainNode = this.getMainNode();
        if (mainNode == null || mainNode.getGrid() == null || mainNode.getNode() == null) {
            return;
        }

        if (this.getHost().hasRedstone()) {
            mainNode.getGrid().getTickManager().wakeDevice(mainNode.getNode());
        } else {
            mainNode.getGrid().getTickManager().sleepDevice(mainNode.getNode());
        }
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!this.isClientSide()) {
            MenuOpener.open(ModMenus.DATA_RIPPER.get(), player, MenuLocators.forPart(this));
        }
        return true;
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
        bch.addBox(3, 3, 14, 13, 13, 16);
        bch.addBox(5, 5, 11, 11, 11, 14);
    }

    public boolean isNetworkEnergySufficient() {
        return this.networkEnergySufficient == YesNo.YES;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, this.isSleeping());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.isAccelerating()) {
            return TickRateModulation.IDLE;
        }

        if (this.isSleeping()) {
            return TickRateModulation.SLEEP;
        }

        var level = this.getLevel();
        if (level == null || this.getBlockEntity() == null) {
            return TickRateModulation.IDLE;
        }

        BlockEntity target = level.getBlockEntity(this.getBlockEntity().getBlockPos().relative(this.getSide()));
        if (target == null || !this.isActive()) {
            return TickRateModulation.IDLE;
        }

        this.ticker(target);
        return TickRateModulation.IDLE;
    }

    public void saveChanges() {
        this.getHost().markForSave();
    }

    private boolean isAccelerating() {
        return this.getConfigManager().getSetting(DataRipperSettings.ACCELERATE) == YesNo.YES;
    }

    private void setNetworkEnergySufficient(boolean sufficient) {
        YesNo next = sufficient ? YesNo.YES : YesNo.NO;
        if (this.networkEnergySufficient != next) {
            this.networkEnergySufficient = next;
            this.saveChanges();
        }
    }

    private <T extends BlockEntity> void ticker(@NotNull T blockEntity) {
        if (!this.isValidForTicking()) {
            return;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString();
        if (DataRipperConfigParsingUtils.isBlockBlacklisted(blockId, Config.dataRipperBlacklist)) {
            return;
        }

        BlockEntityTicker<T> ticker = this.getTicker(blockEntity);
        if (ticker == null) {
            return;
        }

        int speed = this.calculateSpeed();
        if (speed <= 0) {
            return;
        }

        double requiredPower = this.calculateRequiredPower(speed, blockId);
        if (!this.extractPower(requiredPower)) {
            return;
        }

        this.performTicks(blockEntity, ticker, speed);
    }

    private boolean isValidForTicking() {
        var mainNode = this.getMainNode();
        return mainNode != null && mainNode.getGrid() != null;
    }

    @SuppressWarnings("unchecked")
    private <T extends BlockEntity> BlockEntityTicker<T> getTicker(T blockEntity) {
        var level = this.getLevel();
        if (level == null) {
            return null;
        }

        return level.getBlockState(blockEntity.getBlockPos()).getTicker(level, (BlockEntityType<T>) blockEntity.getType());
    }

    private int calculateSpeed() {
        int cardCount = this.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD);
        if (cardCount <= 0) {
            return 0;
        }
        return DataRipperPowerUtils.computeProductWithCap(this.getUpgrades());
    }

    private double calculateRequiredPower(int speed, String blockId) {
        int energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
        double multiplier = DataRipperConfigParsingUtils.getMultiplierForBlock(blockId, Config.dataRipperMultipliers);
        return DataRipperPowerUtils.computeFinalPowerForProduct(speed, energyCardCount) * multiplier;
    }

    private <T extends BlockEntity> void performTicks(T blockEntity, BlockEntityTicker<T> ticker, int speed) {
        for (int i = 0; i < speed - 1; i++) {
            try {
                ticker.tick(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity);
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("LegacyRandomSource")) {
                    LOGGER.warn(
                            "Detected random access conflict while accelerating block entity {} at {}. Stopping this acceleration pass.",
                            blockEntity.getType(),
                            blockEntity.getBlockPos()
                    );
                    break;
                }
                throw e;
            } catch (Exception e) {
                LOGGER.error(
                        "Failed while accelerating block entity {} at {}",
                        blockEntity.getType(),
                        blockEntity.getBlockPos(),
                        e
                );
                break;
            }
        }
    }

    private boolean extractPower(double requiredPower) {
        long requiredDataFlow = DataRipperPowerUtils.toDataFlowCost(requiredPower);
        if (requiredDataFlow <= 0L) {
            return true;
        }

        try {
            var mainNode = this.getMainNode();
            if (mainNode == null || mainNode.getGrid() == null) {
                this.setNetworkEnergySufficient(false);
                return false;
            }

            var inventory = mainNode.getGrid().getStorageService().getInventory();
            long simulated = inventory.extract(DataFlowKey.of(), requiredDataFlow, Actionable.SIMULATE, IActionSource.ofMachine(this));
            if (simulated < requiredDataFlow) {
                this.setNetworkEnergySufficient(false);
                return false;
            }

            inventory.extract(DataFlowKey.of(), requiredDataFlow, Actionable.MODULATE, IActionSource.ofMachine(this));
            this.setNetworkEnergySufficient(true);
            return true;
        } catch (Throwable ignored) {
        }

        this.setNetworkEnergySufficient(false);
        return false;
    }
}
