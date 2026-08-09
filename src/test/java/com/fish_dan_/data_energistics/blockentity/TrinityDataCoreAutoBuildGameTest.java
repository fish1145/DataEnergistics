package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildOptions;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.pattern.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityItemAmount;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.parts.PartHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreAutoBuildGameTest {

    private static final BlockPos LOCAL_ORIGIN = new BlockPos(25, 4, 25);
    private static final int SEARCH_RADIUS = 32;
    private static final int FIXED_CHILD_CORE_COUNT = 80;
    private static final int REPEATED_CHILD_CORE_COUNT = 16;

    private TrinityDataCoreAutoBuildGameTest() {}

    @TestHolder("trinity_data_core_auto_build_main_uses_requested_storage_tier")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void mainUsesRequestedStorageTier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrinityDataCoreBlockEntity host = placeHost(helper, LOCAL_ORIGIN);
        Player player = creativePlayer(helper);
        Block expectedStorageCore = TrinityAutoBuildBlockMap.resolveBlock(TrinityAutoBuildBlockMap.STORAGE_CORE, 2);

        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.STORAGE_CORE, 2)));
        host.serverTick();

        List<BlockPos> storageCores = coresOfKind(level, host.getBlockPos(), TrinityCoreKind.STORAGE_TYPES);
        helper.assertTrue(!storageCores.isEmpty(), "Main auto-build should place storage cores");
        for (BlockPos storageCore : storageCores) {
            helper.assertTrue(level.getBlockState(storageCore).getBlock() == expectedStorageCore,
                    "Every main storage-core candidate must use the selected tier");
        }
        helper.assertTrue(hasCenteredCable(level, host.getBlockPos()),
                "Main auto-build should place its AE2 cable through the explicit center-cable resolver");
        helper.assertTrue(host.isStructureFormed(), "Requested main structure should form after recheck: " +
                host.getLastFailureReason());
        helper.succeed();
    }

    @TestHolder("trinity_data_core_auto_build_selects_every_main_storage_candidate")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void mainSelectionMapsEveryStorageCandidate(GameTestHelper helper) {
        Block selected = TrinityAutoBuildBlockMap.resolveBlock(TrinityAutoBuildBlockMap.STORAGE_CORE, 4);
        Map<Block, Block> selections = TrinityAutoBuildBlockMap.selectedTierBlocks(
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.STORAGE_CORE, 4));

        helper.assertTrue(selections.size() == 10,
                "Main structure selection must map every storage-core candidate");
        helper.assertTrue(selections.values().stream().allMatch(selected::equals),
                "Every main storage-core candidate must resolve to the requested tier");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_auto_build_false_request_leaves_world_unchanged")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void falseRequestLeavesWorldUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrinityDataCoreBlockEntity host = placeHost(helper, LOCAL_ORIGIN);
        Player player = creativePlayer(helper);
        int before = nonControllerBlocks(level, host.getBlockPos());

        host.autoBuildTrinityStructure(player, new TrinityAutoBuildRequest(
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                new TrinityAutoBuildOptions(false, 1, Map.of())));

        helper.assertTrue(nonControllerBlocks(level, host.getBlockPos()) == before,
                "A request without buildRequested must not change the structure world");
        helper.assertFalse(host.isStructureFormed(), "A request without buildRequested must not force a structure recheck");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_auto_build_children_require_formed_main")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void childrenRequireFormedMain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrinityDataCoreBlockEntity host = placeHost(helper, LOCAL_ORIGIN);
        Player player = creativePlayer(helper);

        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 1)));
        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 1)));

        helper.assertTrue(coresOfKind(level, host.getBlockPos(), TrinityCoreKind.PARALLEL_CPU).isEmpty(),
                "CPU build must not start before the main structure forms");
        helper.assertTrue(coresOfKind(level, host.getBlockPos(), TrinityCoreKind.PATTERN_PROCESSING).isEmpty(),
                "Crafting build must not start before the main structure forms");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_auto_build_children_use_requested_tiers")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void childrenUseRequestedTiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrinityDataCoreBlockEntity host = placeHost(helper, LOCAL_ORIGIN);
        Player player = creativePlayer(helper);
        Block expectedCpuCore = TrinityAutoBuildBlockMap.resolveBlock(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 2);
        Block expectedPatternCore = TrinityAutoBuildBlockMap.resolveBlock(
                TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE,
                3);

        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.STORAGE_CORE, 1)));
        host.serverTick();
        helper.assertTrue(host.isStructureFormed(), "Main structure must form before child auto-build");

        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX,
                3,
                Map.of(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 2)));
        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                2,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 3)));
        host.serverTick();

        assertOnlyCoreTier(helper, level, host.getBlockPos(), TrinityCoreKind.PARALLEL_CPU, expectedCpuCore,
                "CPU child must use its selected merged-storage tier");
        assertOnlyCoreTier(helper, level, host.getBlockPos(), TrinityCoreKind.PATTERN_PROCESSING, expectedPatternCore,
                "Crafting child must use its selected pattern-core tier");
        helper.assertTrue(coresOfKind(level, host.getBlockPos(), TrinityCoreKind.PARALLEL_CPU).size() ==
                FIXED_CHILD_CORE_COUNT + REPEATED_CHILD_CORE_COUNT * 3,
                "CPU repeat=3 must build three physical repeated core layers");
        helper.assertTrue(coresOfKind(level, host.getBlockPos(), TrinityCoreKind.PATTERN_PROCESSING).size() ==
                FIXED_CHILD_CORE_COUNT + REPEATED_CHILD_CORE_COUNT * 2,
                "Crafting repeat=2 must build two physical repeated core layers");
        helper.assertTrue(host.isCpuStructureFormed(), "CPU child should form after its requested build: " +
                host.getCpuLastFailureReason());
        helper.assertTrue(host.isCraftingStructureFormed(), "Crafting child should form after its requested build: " +
                host.getCraftingLastFailureReason());
        helper.succeed();
    }

    @TestHolder("trinity_data_core_auto_build_upgrades_pattern_cores_without_copying_persistent_state")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void upgradesPatternCoresWithoutCopyingPersistentState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrinityDataCoreBlockEntity host = placeHost(helper, LOCAL_ORIGIN);
        Player player = creativePlayer(helper);
        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.STORAGE_CORE, 1)));
        host.serverTick();
        helper.assertTrue(host.isStructureFormed(), "Main structure must form before building crafting cores");
        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 1)));
        host.serverTick();

        BlockPos sourcePosition = coresOfKind(level, host.getBlockPos(), TrinityCoreKind.PATTERN_PROCESSING).getFirst();
        BlockEntity sourceBlockEntity = level.getBlockEntity(sourcePosition);
        if (!(sourceBlockEntity instanceof TrinityPatternCoreBlockEntity sourceCore)) {
            helper.fail("Expected a Trinity P core before tier replacement", sourcePosition);
            return;
        }
        PatternRoute sourceRoute = new PatternRoute(host.getHostId(), sourceCore.coreId(), 0);
        sourceCore.appendPendingOutputs(
                sourceRoute,
                List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 2))));
        ItemStack expectedDrop = Block.getDrops(
                sourceCore.getBlockState(),
                level,
                sourcePosition,
                sourceCore).getFirst();

        host.autoBuildTrinityStructure(player, request(
                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 3)));
        host.serverTick();

        Block expectedTier = TrinityAutoBuildBlockMap.resolveBlock(
                TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE,
                3);
        helper.assertValueEqual(level.getBlockState(sourcePosition).getBlock(), expectedTier,
                "Tier replacement must install the requested P-core tier");
        BlockEntity replacementBlockEntity = level.getBlockEntity(sourcePosition);
        if (!(replacementBlockEntity instanceof TrinityPatternCoreBlockEntity replacementCore)) {
            helper.fail("Expected an entity-backed P core after tier replacement", sourcePosition);
            return;
        }
        helper.assertFalse(replacementCore.coreId().equals(sourceCore.coreId()),
                "New P core must not inherit the replaced core UUID or persistent queues");
        helper.assertTrue(hasInventoryStack(player, expectedDrop),
                "Replacing a P core must return its exact BLOCK_ENTITY_DATA loot to the player inventory first");
        helper.assertFalse(hasDroppedStack(level, sourcePosition, expectedDrop),
                "P-core loot must not drop while the player inventory can accept it");
        helper.succeed();
    }

    private static TrinityDataCoreBlockEntity placeHost(GameTestHelper helper, BlockPos localOrigin) {
        helper.setBlock(localOrigin, DEBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        BlockPos origin = helper.absolutePos(localOrigin);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(origin);
        if (blockEntity instanceof TrinityDataCoreBlockEntity host) {
            return host;
        }
        helper.fail("Expected a placed Trinity Data Core block entity", localOrigin);
        throw new IllegalStateException("Missing Trinity Data Core block entity at " + origin);
    }

    private static Player creativePlayer(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.CREATIVE);
    }

    private static TrinityAutoBuildRequest request(int structureIndex,
                                                   int repeatCount,
                                                   Map<String, Integer> tierSelections) {
        return new TrinityAutoBuildRequest(
                structureIndex,
                new TrinityAutoBuildOptions(true, repeatCount, tierSelections));
    }

    private static void assertOnlyCoreTier(GameTestHelper helper,
                                           ServerLevel level,
                                           BlockPos origin,
                                           TrinityCoreKind kind,
                                           Block expected,
                                           String message) {
        List<BlockPos> cores = coresOfKind(level, origin, kind);
        helper.assertTrue(!cores.isEmpty(), message + ": expected at least one core");
        for (BlockPos core : cores) {
            helper.assertTrue(level.getBlockState(core).getBlock() == expected, message);
        }
    }

    private static List<BlockPos> coresOfKind(ServerLevel level, BlockPos origin, TrinityCoreKind kind) {
        List<BlockPos> cores = new ArrayList<>();
        for (BlockPos position : searchArea(origin)) {
            Block block = level.getBlockState(position).getBlock();
            if (block instanceof TrinityCoreComponent component && component.kind() == kind) {
                cores.add(position.immutable());
            }
        }
        return List.copyOf(cores);
    }

    private static boolean hasCenteredCable(ServerLevel level, BlockPos origin) {
        Block cableBus = block("ae2:cable_bus");
        for (BlockPos position : searchArea(origin)) {
            if (level.getBlockState(position).getBlock() == cableBus && PartHelper.getPart(level, position, null) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDroppedStack(ServerLevel level, BlockPos position, ItemStack expected) {
        AABB searchArea = AABB.ofSize(Vec3.atCenterOf(position), 4.0D, 4.0D, 4.0D);
        return level.getEntitiesOfClass(ItemEntity.class, searchArea).stream()
                .map(ItemEntity::getItem)
                .anyMatch(stack -> stack.getCount() == expected.getCount() &&
                        ItemStack.isSameItemSameComponents(stack, expected));
    }

    private static boolean hasInventoryStack(Player player, ItemStack expected) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getCount() == expected.getCount() && ItemStack.isSameItemSameComponents(stack, expected)) {
                return true;
            }
        }
        return false;
    }

    private static int nonControllerBlocks(ServerLevel level, BlockPos origin) {
        int count = 0;
        for (BlockPos position : searchArea(origin)) {
            if (!position.equals(origin) && !level.getBlockState(position).is(Blocks.AIR)) {
                count++;
            }
        }
        return count;
    }

    private static Iterable<BlockPos> searchArea(BlockPos origin) {
        return BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -8, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS));
    }

    private static Block block(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (!location.equals(BuiltInRegistries.BLOCK.getKey(block))) {
            throw new IllegalStateException("Missing test block: " + id);
        }
        return block;
    }
}
