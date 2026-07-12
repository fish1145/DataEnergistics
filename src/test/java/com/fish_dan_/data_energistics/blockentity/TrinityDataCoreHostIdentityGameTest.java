package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import com.mojang.authlib.GameProfile;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/** Verifies that Data Core item movement preserves storage ownership and the independent crafting-route identity. */
@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityDataCoreHostIdentityGameTest {

    private static final int FIRST_BACKPACK_SLOT = 9;

    private TrinityDataCoreHostIdentityGameTest() {}

    @TestHolder("trinity_data_core_host_identity_survives_item_movement")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void hostIdentitySurvivesItemMovement(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos destinationPos = new BlockPos(3, 1, 1);
        placeHost(helper, sourcePos);
        TrinityDataCoreBlockEntity source = requireHost(helper, sourcePos);
        helper.assertTrue(
                !source.getHostId().equals(source.getStorageId()),
                "Trinity crafting host UUID must be independent from the main-storage UUID");

        ItemStack movedHost = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        source.saveIdentityToItem(movedHost);
        assertIdentity(helper, movedHost, source.getStorageId(), source.getHostId(), "Moved host item");

        placeHost(helper, destinationPos);
        TrinityDataCoreBlockEntity destination = requireHost(helper, destinationPos);
        destination.restoreIdentityFromItem(movedHost);

        helper.assertValueEqual(
                destination.getStorageId(), source.getStorageId(), "Moved host should retain its main-storage UUID");
        helper.assertValueEqual(
                destination.getHostId(), source.getHostId(), "Moved host should retain its independent crafting route UUID");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_stateful_drop_restores_saved_data_storage")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void statefulDropRestoresSavedDataStorage(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos destinationPos = new BlockPos(3, 1, 1);
        placeHost(helper, sourcePos);
        TrinityDataCoreBlockEntity source = requireHost(helper, sourcePos);
        UUID storageId = source.getStorageId();
        UUID hostId = source.getHostId();
        AEItemKey storedKey = AEItemKey.of(Items.DIAMOND);
        TrinityDataCoreStorageSavedData storage = TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer());
        helper.assertValueEqual(
                storage.insert(storageId, storedKey, 37L, Actionable.MODULATE),
                37L,
                "Source host should seed its SavedData storage");

        List<ItemStack> drops = Block.getDrops(
                source.getBlockState(),
                helper.getLevel(),
                helper.absolutePos(sourcePos),
                source);
        helper.assertValueEqual(drops.size(), 1, "A stateful Trinity host should create exactly one drop");
        ItemStack movedHost = drops.getFirst();
        assertIdentity(helper, movedHost, storageId, hostId, "Stateful host drop");

        helper.setBlock(sourcePos, Blocks.AIR);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, movedHost);
        helper.placeAt(player, movedHost, destinationPos.below(), Direction.UP);

        assertRestoredHostAndContents(helper, destinationPos, storage, storedKey, storageId, hostId, 37L);
        helper.succeed();
    }

    @TestHolder("trinity_data_core_survival_break_round_trips_identity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void survivalBreakRoundTripsIdentityThroughEntityNbtAndBackpack(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos destinationPos = new BlockPos(3, 1, 1);
        placeHost(helper, sourcePos);
        TrinityDataCoreBlockEntity source = requireHost(helper, sourcePos);
        UUID storageId = source.getStorageId();
        UUID hostId = source.getHostId();
        AEItemKey storedKey = AEItemKey.of(Items.DIAMOND);
        TrinityDataCoreStorageSavedData storage = TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer());
        storage.insert(storageId, storedKey, 53L, Actionable.MODULATE);

        ServerPlayer player = makeServerPlayer(helper);
        ServerPlayerGameMode gameMode = new DirectDestroyGameMode(player, GameType.SURVIVAL);
        fillHotbarForBackpackPickup(player.getInventory());
        helper.assertTrue(
                source.getBlockState().canHarvestBlock(
                        helper.getLevel(), helper.absolutePos(sourcePos), player),
                "A Netherite pickaxe must satisfy the Trinity host harvest tags");
        helper.assertTrue(
                gameMode.destroyBlock(helper.absolutePos(sourcePos)),
                "Survival player should actually destroy the Trinity host");
        helper.assertTrue(helper.getBlockState(sourcePos).isAir(), "Survival break should remove the host block");

        ItemEntity drop = requireOnlyItemDrop(helper);
        assertIdentity(helper, drop.getItem(), storageId, hostId, "Survival ItemEntity");
        CompoundTag entityData = new CompoundTag();
        helper.assertTrue(drop.save(entityData), "Live Trinity host ItemEntity should save to NBT");
        Entity restoredEntity = EntityType.loadEntityRecursive(entityData, helper.getLevel(), entity -> entity);
        if (!(restoredEntity instanceof ItemEntity restoredDrop)) {
            helper.fail("Saved Trinity host ItemEntity should reload as an ItemEntity");
            throw new IllegalStateException("Saved Trinity host drop did not reload as an ItemEntity");
        }
        assertIdentity(helper, restoredDrop.getItem(), storageId, hostId, "Reloaded ItemEntity");

        drop.setNoPickUpDelay();
        drop.playerTouch(player);
        ItemStack backpackStack = player.getInventory().getItem(FIRST_BACKPACK_SLOT);
        assertIdentity(helper, backpackStack, storageId, hostId, "Ordinary backpack slot");

        ItemStack placementStack = player.getInventory().removeItemNoUpdate(FIRST_BACKPACK_SLOT);
        player.getInventory().setItem(0, placementStack);
        player.getInventory().selected = 0;
        helper.placeAt(player, placementStack, destinationPos.below(), Direction.UP);
        assertRestoredHostAndContents(helper, destinationPos, storage, storedKey, storageId, hostId, 53L);
        helper.succeed();
    }

    @TestHolder("trinity_data_core_creative_break_creates_one_stateful_drop")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void creativeBreakCreatesExactlyOneStatefulDrop(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        placeHost(helper, sourcePos);
        TrinityDataCoreBlockEntity source = requireHost(helper, sourcePos);
        UUID storageId = source.getStorageId();
        UUID hostId = source.getHostId();

        ServerPlayer player = makeServerPlayer(helper);
        ServerPlayerGameMode gameMode = new DirectDestroyGameMode(player, GameType.CREATIVE);
        helper.assertTrue(
                gameMode.destroyBlock(helper.absolutePos(sourcePos)),
                "Creative player should actually destroy the Trinity host");
        helper.assertTrue(helper.getBlockState(sourcePos).isAir(), "Creative break should remove the host block");
        ItemEntity drop = requireOnlyItemDrop(helper);
        assertIdentity(helper, drop.getItem(), storageId, hostId, "Creative ItemEntity");
        helper.succeed();
    }

    @SuppressWarnings("deprecation")
    @TestHolder("trinity_data_core_middle_click_clone_has_fresh_identity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void middleClickCloneHasNoIdentityAndPlacesWithFreshIdentity(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos destinationPos = new BlockPos(3, 1, 1);
        placeHost(helper, sourcePos);
        TrinityDataCoreBlockEntity source = requireHost(helper, sourcePos);
        UUID originalStorageId = source.getStorageId();
        UUID originalHostId = source.getHostId();

        ItemStack clone = source.getBlockState()
                .getBlock()
                .getCloneItemStack(helper.getLevel(), helper.absolutePos(sourcePos), source.getBlockState());
        helper.assertTrue(
                !clone.has(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                "Middle-click clone must not copy the storage UUID");
        helper.assertTrue(
                !clone.has(ModDataComponents.TRINITY_DATA_CORE_HOST_ID),
                "Middle-click clone must not copy the crafting UUID");

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, clone);
        helper.placeAt(player, clone, destinationPos.below(), Direction.UP);
        TrinityDataCoreBlockEntity placed = requireHost(helper, destinationPos);
        helper.assertTrue(
                !placed.getStorageId().equals(originalStorageId),
                "Identityless clone should place with a fresh storage UUID");
        helper.assertTrue(
                !placed.getHostId().equals(originalHostId),
                "Identityless clone should place with a fresh crafting UUID");
        helper.assertTrue(
                !placed.getStorageId().equals(placed.getHostId()),
                "Fresh storage and crafting UUIDs should remain independent");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_rejects_partial_item_identity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsPartialItemIdentity(GameTestHelper helper) {
        TrinityDataCoreBlockEntity host = new TrinityDataCoreBlockEntity(
                BlockPos.ZERO,
                ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        UUID originalStorageId = host.getStorageId();
        UUID originalHostId = host.getHostId();
        ItemStack storageOnly = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        storageOnly.set(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID, UUID.randomUUID());
        ItemStack hostOnly = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        hostOnly.set(ModDataComponents.TRINITY_DATA_CORE_HOST_ID, UUID.randomUUID());

        assertPartialIdentityRejected(helper, host, storageOnly, originalStorageId, originalHostId, "storage-only");
        assertPartialIdentityRejected(helper, host, hostOnly, originalStorageId, originalHostId, "host-only");
        helper.succeed();
    }

    private static void placeHost(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
    }

    private static TrinityDataCoreBlockEntity requireHost(GameTestHelper helper, BlockPos position) {
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof TrinityDataCoreBlockEntity host) {
            return host;
        }
        helper.fail("Expected a Trinity host block entity", position);
        throw new IllegalStateException("Placed Trinity host has no matching block entity");
    }

    private static ItemEntity requireOnlyItemDrop(GameTestHelper helper) {
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM);
        helper.assertValueEqual(drops.size(), 1, "Actual host break should create exactly one ItemEntity");
        ItemEntity drop = drops.getFirst();
        helper.assertTrue(drop.getItem().is(ModBlocks.TRINITY_DATA_CORE.get().asItem()),
                "Actual host break should drop the Trinity Data Core item");
        return drop;
    }

    private static void assertIdentity(GameTestHelper helper,
                                       ItemStack stack,
                                       UUID storageId,
                                       UUID hostId,
                                       String source) {
        helper.assertValueEqual(
                stack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                storageId,
                source + " should carry the storage UUID");
        helper.assertValueEqual(
                stack.get(ModDataComponents.TRINITY_DATA_CORE_HOST_ID),
                hostId,
                source + " should carry the crafting UUID");
    }

    private static void assertRestoredHostAndContents(GameTestHelper helper,
                                                      BlockPos destinationPos,
                                                      TrinityDataCoreStorageSavedData storage,
                                                      AEItemKey storedKey,
                                                      UUID storageId,
                                                      UUID hostId,
                                                      long amount) {
        TrinityDataCoreBlockEntity restored = requireHost(helper, destinationPos);
        helper.assertValueEqual(
                restored.getStorageId(), storageId, "Placed stateful host should restore the original storage UUID");
        helper.assertValueEqual(
                restored.getHostId(), hostId, "Placed stateful host should restore the original crafting UUID");
        helper.assertValueEqual(
                storage.amount(restored.getStorageId(), storedKey),
                BigInteger.valueOf(amount),
                "Placed stateful host should resolve the original SavedData contents");
    }

    private static void assertPartialIdentityRejected(GameTestHelper helper,
                                                      TrinityDataCoreBlockEntity host,
                                                      ItemStack malformed,
                                                      UUID originalStorageId,
                                                      UUID originalHostId,
                                                      String component) {
        helper.assertFalse(
                host.restoreIdentityFromItem(malformed),
                "A host item with a " + component + " identity must be rejected");
        helper.assertValueEqual(
                host.getStorageId(), originalStorageId, "Rejected partial identity must not replace the storage UUID");
        helper.assertValueEqual(
                host.getHostId(), originalHostId, "Rejected partial identity must not replace the crafting UUID");
    }

    private static void fillHotbarForBackpackPickup(Inventory inventory) {
        inventory.selected = 0;
        inventory.setItem(0, new ItemStack(Items.NETHERITE_PICKAXE));
        for (int slot = 1; slot < FIRST_BACKPACK_SLOT; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
    }

    private static ServerPlayer makeServerPlayer(GameTestHelper helper) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "direct-breaker"),
                ClientInformation.createDefault());
    }

    private static final class DirectDestroyGameMode extends ServerPlayerGameMode {

        private DirectDestroyGameMode(ServerPlayer player, GameType gameType) {
            super(player);
            setGameModeForPlayer(gameType, null);
        }
    }
}
