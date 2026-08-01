package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.ae2.EchoKey;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPart;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.FormationPlanePart;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class SonicBoomEchoCaptureGameTest {

    private static final BlockPos FIRST_PLANE_HOST_POS = new BlockPos(2, 1, 1);
    private static final BlockPos CABLE_POS = new BlockPos(2, 1, 2);
    private static final BlockPos SECOND_PLANE_HOST_POS = new BlockPos(2, 1, 3);
    private static final BlockPos SECOND_PLANE_CABLE_POS = new BlockPos(3, 1, 3);
    private static final BlockPos DEPOT_POS = new BlockPos(3, 1, 1);
    private static final BlockPos ENERGY_CELL_POS = new BlockPos(3, 1, 2);
    private static final BlockPos WARDEN_POS = new BlockPos(2, 0, -1);
    private static final BlockPos TARGET_POS = new BlockPos(2, 0, 4);

    private SonicBoomEchoCaptureGameTest() {}

    @TestHolder("sonic_boom_echo_capture_handles_planes_offline_and_full_storage")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 200)
    public static void handlesPlanesOfflineAndFullStorage(GameTestHelper helper) {
        CaptureFixture fixture = placeUnpoweredCaptureNetwork(helper);
        ServerLevel level = helper.getLevel();
        Warden warden = createWarden(helper, level);
        LivingEntity target = createTarget(helper, level);
        SonicBoomEchoCaptureImpl capture = new SonicBoomEchoCaptureImpl();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            fixture.firstPlane().getMainNode().isReady() && fixture.secondPlane().getMainNode().isReady(),
                            "Both real formation plane nodes must initialize before the offline capture check");
                })
                .thenExecute(() -> {
                    helper.assertTrue(
                            SonicBoomEchoCaptureImpl.isDirectWardenSonicBoom(
                                    level.damageSources().sonicBoom(warden)),
                            "A real Warden sonic source must be recognized");
                    helper.assertFalse(
                            SonicBoomEchoCaptureImpl.isDirectWardenSonicBoom(
                                    level.damageSources().mobAttack(warden)),
                            "Ordinary Warden damage must not be recognized as a sonic boom");
                    helper.assertValueEqual(
                            capture.capture(level, warden, target, () -> 0.0D),
                            0,
                            "Offline formation planes must not produce Echo");
                    helper.assertValueEqual(
                            storedEcho(fixture.depot()),
                            0L,
                            "The unpowered network must remain empty");
                    helper.setBlock(
                            ENERGY_CELL_POS,
                            AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            fixture.firstPlane().getMainNode().isOnline() && fixture.secondPlane().getMainNode().isOnline() && fixture.depot().isOnline(),
                            "The capture network must become online after receiving AE power: first=" + nodeState(fixture.firstPlane().getMainNode()) + ", second=" + nodeState(fixture.secondPlane().getMainNode()) + ", depot=" + nodeState(fixture.depot().getMainNode()));
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            capture.capture(level, warden, target, () -> 0.0D),
                            10,
                            "The first successful formation plane must produce ten Echo and stop the capture ray");
                    helper.assertValueEqual(
                            storedEcho(fixture.depot()),
                            10L,
                            "A successful capture must not reach the formation plane behind it");

                    double[] rolls = { 0.75D, 0.0D };
                    int[] rollIndex = { 0 };
                    helper.assertValueEqual(
                            capture.capture(level, warden, target, () -> rolls[rollIndex[0]++]),
                            10,
                            "A failed capture must allow the ray to reach the next formation plane");
                    helper.assertValueEqual(
                            storedEcho(fixture.depot()),
                            20L,
                            "The second formation plane must capture after the first plane fails its roll");

                    fillKeyStorage(fixture.depot());
                    long fullEchoAmount = storedEcho(fixture.depot());
                    helper.assertValueEqual(
                            capture.capture(level, warden, target, () -> 0.0D),
                            0,
                            "A full key inventory must reject the complete simulated insertion");
                    helper.assertValueEqual(
                            storedEcho(fixture.depot()),
                            fullEchoAmount,
                            "A rejected capture must not drop, cache, or partially insert Echo");
                })
                .thenSucceed();
    }

    private static CaptureFixture placeUnpoweredCaptureNetwork(GameTestHelper helper) {
        CableBusBlockEntity firstHost = placeCableBus(helper, FIRST_PLANE_HOST_POS);
        placeCableBus(helper, CABLE_POS);
        CableBusBlockEntity secondHost = placeCableBus(helper, SECOND_PLANE_HOST_POS);
        placeCableBus(helper, SECOND_PLANE_CABLE_POS);

        FormationPlanePart firstPlane = installFormationPlane(firstHost);
        FormationPlanePart secondPlane = installFormationPlane(secondHost);

        helper.setBlock(DEPOT_POS, ModBlocks.DIGITAL_STORAGE_DEPOT.get());
        BlockEntity blockEntity = helper.getBlockEntity(DEPOT_POS);
        if (!(blockEntity instanceof DigitalStorageDepotBlockEntity depot)) {
            throw new GameTestAssertException("Placed Echo storage depot has no matching block entity");
        }
        return new CaptureFixture(firstPlane, secondPlane, depot);
    }

    private static CableBusBlockEntity placeCableBus(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, AEBlocks.CABLE_BUS.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (!(blockEntity instanceof CableBusBlockEntity cableBus)) {
            throw new GameTestAssertException("Placed Echo capture cable bus has no matching block entity");
        }
        IPart cable = cableBus.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, null);
        if (cable == null) {
            throw new GameTestAssertException("Echo capture cable bus must accept its center glass cable");
        }
        return cableBus;
    }

    private static FormationPlanePart installFormationPlane(CableBusBlockEntity cableBus) {
        IPart part = cableBus.addPart(AEParts.FORMATION_PLANE.get(), Direction.NORTH, null);
        if (part instanceof FormationPlanePart formationPlane) {
            return formationPlane;
        }
        throw new GameTestAssertException("Echo capture cable bus must accept a north-facing formation plane");
    }

    private static String nodeState(IManagedGridNode managedNode) {
        IGridNode node = managedNode.getNode();
        return "{ready=" + managedNode.isReady() + ", online=" + managedNode.isOnline() + ", active=" + (node != null && node.isActive()) + ", grid=" + (node != null && node.getGrid() != null) + "}";
    }

    private static Warden createWarden(GameTestHelper helper, ServerLevel level) {
        Warden warden = EntityType.WARDEN.create(level);
        if (warden == null) {
            throw new GameTestAssertException("Failed to create the Echo capture Warden");
        }
        warden.setPos(Vec3.atBottomCenterOf(helper.absolutePos(WARDEN_POS)));
        return warden;
    }

    private static LivingEntity createTarget(GameTestHelper helper, ServerLevel level) {
        LivingEntity target = EntityType.ARMOR_STAND.create(level);
        if (target == null) {
            throw new GameTestAssertException("Failed to create the Echo capture target");
        }
        target.setPos(Vec3.atBottomCenterOf(helper.absolutePos(TARGET_POS)));
        return target;
    }

    private static long storedEcho(DigitalStorageDepotBlockEntity depot) {
        long amount = 0L;
        for (int slot = 0; slot < DigitalStorageDepotBlockEntity.KEY_SLOTS; slot++) {
            GenericStack stack = depot.getKeyStack(slot);
            if (stack != null && stack.what().equals(EchoKey.of())) {
                amount += stack.amount();
            }
        }
        return amount;
    }

    private static void fillKeyStorage(DigitalStorageDepotBlockEntity depot) {
        GenericInternalInventory inventory = depot.getExternalKeyInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, null);
        }
        long capacity = depot.getKeyCapacity();
        inventory.setStack(0, new GenericStack(EchoKey.of(), capacity));
        inventory.setStack(1, new GenericStack(DataKey.of(), capacity));
        inventory.setStack(2, new GenericStack(DataFlowKey.of(), capacity));
    }

    private record CaptureFixture(
                                  FormationPlanePart firstPlane,
                                  FormationPlanePart secondPlane,
                                  DigitalStorageDepotBlockEntity depot) {}
}
