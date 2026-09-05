package com.fish_dan_.data_energistics.common.solar.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataSolarPanelBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jspecify.annotations.NullMarked;

@NullMarked
@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class SolarPanelArrayGameTest {

    private static final double TOLERANCE = 0.000001D;
    private static final int READY_DELAY_TICKS = 3;
    private static final BlockPos WEST_POS = new BlockPos(1, 2, 2);
    private static final BlockPos CENTER_POS = new BlockPos(2, 2, 2);
    private static final BlockPos EAST_POS = new BlockPos(3, 2, 2);

    private SolarPanelArrayGameTest() {}

    @TestHolder("solar_panel_array_mixed_variants_share_capacity_and_energy")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void mixedVariantsShareCapacityAndEnergy(GameTestHelper helper) {
        DataSolarPanelBlockEntity west = placeDisabledPanel(helper, WEST_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity center = placeDisabledPanel(helper, CENTER_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity east = placeDisabledPanel(helper, EAST_POS, DEBlocks.DATA_SOLAR_PANEL.get());

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            double expectedCapacity = DataSolarPanelBlockEntity.ENERGY_CAPACITY * 3.0D;
            assertSnapshot(west, 0.0D, expectedCapacity);
            assertSnapshot(center, 0.0D, expectedCapacity);
            assertSnapshot(east, 0.0D, expectedCapacity);

            assertEquals(
                    0.0D,
                    west.injectExternalPower(PowerUnit.AE, 200_000.0D, Actionable.MODULATE),
                    "Mixed array must accept energy injected through one member");
            assertSnapshot(east, 200_000.0D, expectedCapacity);

            assertEquals(
                    75_000.0D,
                    east.extractAEPower(75_000.0D, Actionable.MODULATE, PowerMultiplier.ONE),
                    "A different member must extract from the shared array");
            assertSnapshot(west, 125_000.0D, expectedCapacity);
            helper.succeed();
        });
    }

    @TestHolder("solar_panel_array_split_keeps_only_remaining_raw_shares")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void splitKeepsOnlyRemainingRawShares(GameTestHelper helper) {
        DataSolarPanelBlockEntity west = placeDisabledPanel(helper, WEST_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity center = placeDisabledPanel(helper, CENTER_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity east = placeDisabledPanel(helper, EAST_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            assertEquals(
                    0.0D,
                    west.injectExternalPower(PowerUnit.AE, 250_000.0D, Actionable.MODULATE),
                    "Connected array must accept the split-test energy");
            double westShare = west.getInternalCurrentPower();
            double centerShare = center.getInternalCurrentPower();
            double eastShare = east.getInternalCurrentPower();
            assertEquals(250_000.0D, westShare + centerShare + eastShare, "Raw shares must own the aggregate before split");

            helper.setBlock(CENTER_POS, Blocks.AIR);

            assertSnapshot(west, westShare, DataSolarPanelBlockEntity.ENERGY_CAPACITY);
            assertSnapshot(east, eastShare, DataSolarPanelBlockEntity.ENERGY_CAPACITY);
            assertEquals(
                    westShare + eastShare,
                    west.getEnergyStorageSnapshot().stored() + east.getEnergyStorageSnapshot().stored(),
                    "Removing the bridge must not copy its share into either remaining component");
            assertEquals(
                    250_000.0D - centerShare,
                    west.getEnergyStorageSnapshot().stored() + east.getEnergyStorageSnapshot().stored(),
                    "Remaining components must contain exactly the surviving raw shares");
            helper.succeed();
        });
    }

    @TestHolder("solar_panel_array_unavailable_member_detaches_and_rejoins_without_copying")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void unavailableMemberDetachesAndRejoinsWithoutCopying(GameTestHelper helper) {
        DataSolarPanelBlockEntity west = placeDisabledPanel(helper, WEST_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity center = placeDisabledPanel(helper, CENTER_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            assertEquals(
                    0.0D,
                    west.injectExternalPower(PowerUnit.AE, 200_000.0D, Actionable.MODULATE),
                    "Connected pair must accept the lifecycle-test energy");
            double westShare = west.getInternalCurrentPower();
            double centerShare = center.getInternalCurrentPower();

            center.energyMembership().onUnavailable();
            assertSnapshot(west, westShare, DataSolarPanelBlockEntity.ENERGY_CAPACITY);
            assertSnapshot(center, centerShare, DataSolarPanelBlockEntity.ENERGY_CAPACITY);

            center.energyMembership().onReady();
            assertSnapshot(west, westShare + centerShare, DataSolarPanelBlockEntity.ENERGY_CAPACITY * 2.0D);
            assertSnapshot(center, westShare + centerShare, DataSolarPanelBlockEntity.ENERGY_CAPACITY * 2.0D);
            helper.succeed();
        });
    }

    @TestHolder("solar_panel_array_ignores_diagonal_and_vertical_neighbors")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void ignoresDiagonalAndVerticalNeighbors(GameTestHelper helper) {
        BlockPos originPos = new BlockPos(1, 2, 1);
        BlockPos diagonalPos = new BlockPos(2, 2, 2);
        BlockPos abovePos = originPos.above();
        DataSolarPanelBlockEntity origin = placeDisabledPanel(helper, originPos, DEBlocks.DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity diagonal = placeDisabledPanel(helper, diagonalPos, DEBlocks.ME_DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity above = placeDisabledPanel(helper, abovePos, DEBlocks.DATA_SOLAR_PANEL.get());

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            assertEquals(
                    0.0D,
                    origin.injectExternalPower(PowerUnit.AE, 50_000.0D, Actionable.MODULATE),
                    "Origin panel must accept isolated energy");
            assertSnapshot(origin, 50_000.0D, DataSolarPanelBlockEntity.ENERGY_CAPACITY);
            assertSnapshot(diagonal, 0.0D, DataSolarPanelBlockEntity.ENERGY_CAPACITY);
            assertSnapshot(above, 0.0D, DataSolarPanelBlockEntity.ENERGY_CAPACITY);
            helper.succeed();
        });
    }

    @TestHolder("solar_panel_array_keeps_bottom_only_private_ae_nodes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void keepsBottomOnlyPrivateAeNodes(GameTestHelper helper) {
        DataSolarPanelBlockEntity west = placeDisabledPanel(helper, WEST_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity center = placeDisabledPanel(helper, CENTER_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            assertBottomOnly(west);
            assertBottomOnly(center);
            IGridNode westNode = requireNode(west);
            IGridNode centerNode = requireNode(center);
            helper.assertTrue(
                    westNode.getGrid() != centerNode.getGrid(),
                    "Horizontally connected panels must not merge their AE grids or channels");
            helper.assertTrue(
                    !westNode.getConnectedSides().contains(Direction.EAST),
                    "West panel node must not expose its visual east connection");
            helper.assertTrue(
                    !centerNode.getConnectedSides().contains(Direction.WEST),
                    "Center panel node must not expose its visual west connection");

            IAEPowerStorage westPort = requirePowerPort(westNode);
            IAEPowerStorage centerPort = requirePowerPort(centerNode);
            helper.assertTrue(!westPort.isAEPublicPowerStorage(), "Array port must remain a private AE storage");
            helper.assertTrue(!centerPort.isAEPublicPowerStorage(), "Every member port must remain private");
            assertEquals(
                    DataSolarPanelBlockEntity.ENERGY_CAPACITY * 2.0D,
                    westPort.getAEMaxPower(),
                    "Private port may report shared capacity without registering it as a public battery");
            helper.succeed();
        });
    }

    @TestHolder("solar_panel_array_generates_into_real_bottom_energy_cell")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 50)
    public static void generatesIntoRealBottomEnergyCell(GameTestHelper helper) {
        DataSolarPanelBlockEntity panel = placePanel(helper, CENTER_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        BlockPos energyCellPos = CENTER_POS.below();
        helper.setBlock(energyCellPos, AEBlocks.ENERGY_CELL.block());
        EnergyCellBlockEntity energyCell = requireEnergyCell(helper, energyCellPos);

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            helper.assertTrue(
                    helper.getLevel().canSeeSky(panel.getBlockPos().above()),
                    "Automatic-generation test panel must have real sky access");
            helper.assertTrue(
                    panel.getGeneratedPowerPerTick() > 0.0D,
                    "Current world day/night settings must provide a positive nominal generation rate");
            double storedBefore = energyCell.getAECurrentPower();

            helper.runAfterDelay(5, () -> {
                helper.assertTrue(
                        energyCell.getAECurrentPower() > storedBefore,
                        "Real server ticks must generate energy into the bottom AE energy cell");
                helper.succeed();
            });
        });
    }

    @TestHolder("solar_panel_array_supplies_two_separate_bottom_grids_without_merging")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 50)
    public static void suppliesTwoSeparateBottomGridsWithoutMerging(GameTestHelper helper) {
        DataSolarPanelBlockEntity west = placeDisabledPanel(helper, WEST_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity center = placeDisabledPanel(helper, CENTER_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity east = placeDisabledPanel(helper, EAST_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        BlockPos westCellPos = WEST_POS.below();
        BlockPos eastCellPos = EAST_POS.below();
        helper.setBlock(westCellPos, AEBlocks.ENERGY_CELL.block());
        helper.setBlock(eastCellPos, AEBlocks.ENERGY_CELL.block());
        EnergyCellBlockEntity westCell = requireEnergyCell(helper, westCellPos);
        EnergyCellBlockEntity eastCell = requireEnergyCell(helper, eastCellPos);

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            IGridNode westNode = requireNode(west);
            IGridNode eastNode = requireNode(east);
            IGrid westGrid = westNode.getGrid();
            IGrid eastGrid = eastNode.getGrid();
            helper.assertTrue(westGrid != eastGrid, "Separated bottom energy cells must start on different AE grids");
            assertEquals(
                    0.0D,
                    west.injectExternalPower(PowerUnit.AE, 450_000.0D, Actionable.MODULATE),
                    "Three-panel private pool must accept the dual-grid test energy");
            double storedBefore = west.getEnergyStorageSnapshot().stored();
            assertEquals(450_000.0D, storedBefore, "Dual-grid test must start with the expected private energy");

            west.setRedstoneControlled(false);
            east.setRedstoneControlled(false);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(westCell.getAECurrentPower() > 0.0D, "West bottom grid must receive shared energy");
                helper.assertTrue(eastCell.getAECurrentPower() > 0.0D, "East bottom grid must receive shared energy");
                helper.assertTrue(
                        west.getEnergyStorageSnapshot().stored() < storedBefore,
                        "Trying both distinct outputs must lower the shared private pool");
                helper.assertTrue(westNode.getGrid() == westGrid, "West panel must remain on its original AE grid");
                helper.assertTrue(eastNode.getGrid() == eastGrid, "East panel must remain on its original AE grid");
                helper.assertTrue(
                        westNode.getGrid() != eastNode.getGrid(),
                        "Shared energy transfer must not merge the two bottom AE grids or their channels");
                assertSnapshot(
                        center,
                        west.getEnergyStorageSnapshot().stored(),
                        DataSolarPanelBlockEntity.ENERGY_CAPACITY * 3.0D);
                helper.succeed();
            });
        });
    }

    @TestHolder("solar_panel_energy_card_nbt_reload_preserves_energy_above_base_capacity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void energyCardNbtReloadPreservesEnergyAboveBaseCapacity(GameTestHelper helper) {
        DataSolarPanelBlockEntity panel = placeDisabledPanel(helper, CENTER_POS, DEBlocks.DATA_SOLAR_PANEL.get());

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            installEnergyCard(panel);
            double upgradedCapacity = panel.getInternalMaxPower();
            helper.assertTrue(
                    upgradedCapacity > DataSolarPanelBlockEntity.ENERGY_CAPACITY,
                    "Energy card must raise the panel's local capacity before saving");
            double stored = DataSolarPanelBlockEntity.ENERGY_CAPACITY + 40_000.0D;
            panel.setInternalCurrentPower(stored);

            CompoundTag saved = panel.saveWithoutMetadata(helper.getLevel().registryAccess());
            DataSolarPanelBlockEntity restored = new DataSolarPanelBlockEntity(panel.getBlockPos(), panel.getBlockState());
            restored.loadWithComponents(saved, helper.getLevel().registryAccess());

            assertEquals(upgradedCapacity, restored.getInternalMaxPower(), "NBT load must restore upgrades before capacity");
            assertEquals(stored, restored.getInternalCurrentPower(), "NBT load must not clamp energy to base capacity");
            helper.assertValueEqual(
                    DataSolarPanelBlockEntity.getEnergyCardCount(restored.getUpgrades()),
                    1,
                    "NBT load must restore the installed energy card");
            helper.succeed();
        });
    }

    @TestHolder("solar_panel_array_shrink_moves_excess_to_connected_member")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void shrinkMovesExcessToConnectedMember(GameTestHelper helper) {
        DataSolarPanelBlockEntity west = placeDisabledPanel(helper, WEST_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        DataSolarPanelBlockEntity center = placeDisabledPanel(helper, CENTER_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());

        helper.runAfterDelay(READY_DELAY_TICKS, () -> {
            installEnergyCard(west);
            west.setInternalCurrentPower(200_000.0D);
            assertSnapshot(
                    center,
                    200_000.0D,
                    west.getInternalMaxPower() + DataSolarPanelBlockEntity.ENERGY_CAPACITY);

            ItemStack removed = west.getUpgrades().extractItem(0, 1, false);
            helper.assertTrue(AEItems.ENERGY_CARD.is(removed), "Shrink test must remove the installed energy card");

            assertEquals(
                    DataSolarPanelBlockEntity.ENERGY_CAPACITY,
                    west.getInternalMaxPower(),
                    "Removing the card must restore base local capacity");
            assertEquals(
                    DataSolarPanelBlockEntity.ENERGY_CAPACITY,
                    west.getInternalCurrentPower(),
                    "Source must retain only the energy that fits after shrinking");
            assertEquals(40_000.0D, center.getInternalCurrentPower(), "Connected member must receive the shrink excess");
            assertSnapshot(
                    west,
                    200_000.0D,
                    DataSolarPanelBlockEntity.ENERGY_CAPACITY * 2.0D);
            helper.succeed();
        });
    }

    private static DataSolarPanelBlockEntity placeDisabledPanel(GameTestHelper helper, BlockPos pos, Block block) {
        DataSolarPanelBlockEntity panel = placePanel(helper, pos, block);
        panel.setRedstoneControlled(true);
        return panel;
    }

    private static DataSolarPanelBlockEntity placePanel(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        if (helper.getBlockEntity(pos) instanceof DataSolarPanelBlockEntity panel) {
            return panel;
        }
        throw new GameTestAssertException("Placed solar panel has no matching block entity at " + pos);
    }

    private static EnergyCellBlockEntity requireEnergyCell(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof EnergyCellBlockEntity energyCell) {
            return energyCell;
        }
        throw new GameTestAssertException("Placed AE energy cell has no matching block entity at " + pos);
    }

    private static void installEnergyCard(DataSolarPanelBlockEntity panel) {
        ItemStack overflow = panel.getUpgrades().addItems(AEItems.ENERGY_CARD.stack());
        if (!overflow.isEmpty()) {
            throw new GameTestAssertException("Solar panel rejected a supported energy card");
        }
    }

    private static void assertBottomOnly(DataSolarPanelBlockEntity panel) {
        var sides = panel.getGridConnectableSides(BlockOrientation.get(panel.getBlockState()));
        if (sides.size() != 1 || !sides.contains(Direction.DOWN)) {
            throw new GameTestAssertException("Solar panel AE node must be exposed only on its bottom side: " + sides);
        }
    }

    private static IGridNode requireNode(DataSolarPanelBlockEntity panel) {
        IGridNode node = panel.getMainNode().getNode();
        if (node == null) {
            throw new GameTestAssertException("Solar panel main node was not ready");
        }
        return node;
    }

    private static IAEPowerStorage requirePowerPort(IGridNode node) {
        IAEPowerStorage port = node.getService(IAEPowerStorage.class);
        if (port == null) {
            throw new GameTestAssertException("Solar panel node has no AE power-storage port");
        }
        return port;
    }

    private static void assertSnapshot(DataSolarPanelBlockEntity panel, double stored, double capacity) {
        SolarEnergyPool.Snapshot snapshot = panel.getEnergyStorageSnapshot();
        assertEquals(stored, snapshot.stored(), "Unexpected shared stored energy at " + panel.getBlockPos());
        assertEquals(capacity, snapshot.capacity(), "Unexpected shared capacity at " + panel.getBlockPos());
    }

    private static void assertEquals(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > TOLERANCE) {
            throw new GameTestAssertException(message + ": expected " + expected + ", got " + actual);
        }
    }
}
