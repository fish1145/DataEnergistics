package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorageImpl;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.Collection;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class CompartmentBlockEntityTest {

    private CompartmentBlockEntityTest() {}

    @TestHolder("compartment_block_entity_me_input_pulls_marked_keys_from_storage")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void meInputPullsMarkedKeysFromStorage(GameTestHelper helper) {
        MeCompositeInputWarehouseBlockEntity meInput = new MeCompositeInputWarehouseBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        SimpleMEStorage network = new SimpleMEStorage();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey wrappedIron = AEItemKey.of(GenericStack.wrapInItemStack(iron, 5000L));

        meInput.markerInventory().setStack(0, new GenericStack(wrappedIron, 1L));
        network.insert(iron, 5000L, Actionable.MODULATE, IActionSource.empty());

        meInput.pullMarkedKeysFromNetwork(network);

        helper.assertValueEqual(meInput.meInputBuffer().getKey(0), iron, "ME input should pull the unwrapped marker key");
        helper.assertValueEqual(
                meInput.meInputBuffer().getAmount(0),
                4000L,
                "ME input should pull the per-tick long transfer amount");
        helper.assertValueEqual(network.amount(iron), 1000L, "Network storage should lose the pulled amount");

        meInput.pullMarkedKeysFromNetwork(network);

        helper.assertValueEqual(
                meInput.meInputBuffer().getAmount(0),
                5000L,
                "ME input should accumulate additional pulled amounts beyond an ItemStack limit");
        helper.assertValueEqual(network.amount(iron), 0L, "Network storage should be drained after the second pull");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_me_output_provider_mounts_only_when_bound")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void meOutputProviderMountsOnlyWhenBound(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        MeCompositeOutputWarehouseBlockEntity meOutput = meOutputWarehouse();
        RecordingStorageMounts mounts = new RecordingStorageMounts();

        meOutput.outputStorageProvider().mountInventories(mounts);

        helper.assertValueEqual(mounts.mountCount(), 0, "Unbound ME output should not mount AE storage");

        meOutput.compartment$bindToHost("main", host);
        MEStorage boundStorage = meOutput.outputStorage();
        if (boundStorage == null) {
            helper.fail("Bound ME output should expose output storage before mounting");
            return;
        }
        meOutput.outputStorageProvider().mountInventories(mounts);

        helper.assertValueEqual(mounts.mountCount(), 1, "Bound ME output should mount one AE storage");
        helper.assertTrue(mounts.mountedStorage() == boundStorage, "Mounted storage should be the ME output buffer");
        helper.assertValueEqual(
                mounts.priority(),
                IStorageMounts.DEFAULT_PRIORITY,
                "ME output buffer should mount with the default AE priority");

        meOutput.compartment$unbindFromHost("main", host);
        RecordingStorageMounts unboundMounts = new RecordingStorageMounts();

        meOutput.outputStorageProvider().mountInventories(unboundMounts);

        helper.assertValueEqual(unboundMounts.mountCount(), 0, "Unbound ME output should stop mounting AE storage");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_me_output_requests_storage_updates")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void meOutputRequestsStorageUpdates(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        UpdateCountingMeOutputWarehouse meOutput = updateCountingMeOutputWarehouse();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);

        helper.assertValueEqual(meOutput.storageUpdateRequests(), 0, "Fresh ME output should not request updates");

        meOutput.compartment$bindToHost("main", host);

        helper.assertValueEqual(
                meOutput.storageUpdateRequests(),
                1,
                "Binding ME output should request an AE storage update");

        MEStorage outputStorage = meOutput.outputStorage();
        if (outputStorage == null) {
            helper.fail("Bound ME output should expose output storage for content mutation");
            return;
        }

        helper.assertValueEqual(
                outputStorage.insert(iron, 3L, Actionable.MODULATE, IActionSource.empty()),
                3L,
                "Bound ME output storage should accept inserted contents");
        helper.assertValueEqual(
                meOutput.storageUpdateRequests(),
                2,
                "Changing ME output contents should request an AE storage update");

        meOutput.compartment$unbindFromHost("main", host);

        helper.assertValueEqual(
                meOutput.storageUpdateRequests(),
                3,
                "Unbinding ME output should request an AE storage update");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_plain_input_server_tick_updates_active_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void plainInputServerTickUpdatesActiveState(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        BlockState inputState = ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState();

        helper.setBlock(relativePos, inputState);
        BlockPos levelPos = helper.absolutePos(relativePos);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(levelPos);
        if (!(blockEntity instanceof CompositeWarehouseBlockEntity warehouse)) {
            helper.fail("Expected a composite warehouse block entity", relativePos);
            return;
        }

        warehouse.serverTick();
        assertActiveState(helper, levelPos, false, "Unbound plain input warehouse should stay inactive");

        TestCompartmentHost host = new TestCompartmentHost();
        warehouse.compartment$bindToHost("main", host);
        warehouse.serverTick();
        assertActiveState(helper, levelPos, true, "Bound plain input warehouse should become active");

        warehouse.compartment$unbindFromHost("main", host);
        warehouse.serverTick();
        assertActiveState(helper, levelPos, false, "Unbound plain input warehouse should become inactive again");
        helper.succeed();
    }

    private static void assertActiveState(GameTestHelper helper, BlockPos levelPos, boolean expected, String message) {
        boolean active = helper.getLevel().getBlockState(levelPos).getValue(CompartmentBlock.ACTIVE);
        helper.assertValueEqual(active, expected, message);
    }

    private static final class SimpleMEStorage implements MEStorage {

        private final CompartmentStorage storage = new CompartmentStorageImpl(() -> {});

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            return this.storage.insert(what, amount, mode == Actionable.SIMULATE);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            return this.storage.extract(what, amount, mode == Actionable.SIMULATE);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (Object2LongMap.Entry<AEKey> entry : this.storage.entries().object2LongEntrySet()) {
                if (entry.getKey() != null && entry.getLongValue() > 0L) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal("test ME storage");
        }

        private long amount(AEKey key) {
            return this.storage.amount(key);
        }
    }

    private static MeCompositeOutputWarehouseBlockEntity meOutputWarehouse() {
        return new MeCompositeOutputWarehouseBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static UpdateCountingMeOutputWarehouse updateCountingMeOutputWarehouse() {
        return new UpdateCountingMeOutputWarehouse(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static final class RecordingStorageMounts implements IStorageMounts {

        private int mountCount;
        private MEStorage mountedStorage;
        private int priority;

        @Override
        public void mount(MEStorage storage, int priority) {
            this.mountCount++;
            this.mountedStorage = storage;
            this.priority = priority;
        }

        private int mountCount() {
            return this.mountCount;
        }

        private MEStorage mountedStorage() {
            return this.mountedStorage;
        }

        private int priority() {
            return this.priority;
        }
    }

    private static final class UpdateCountingMeOutputWarehouse extends MeCompositeOutputWarehouseBlockEntity {

        private int storageUpdateRequests;

        private UpdateCountingMeOutputWarehouse(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void requestStorageUpdate() {
            this.storageUpdateRequests++;
        }

        private int storageUpdateRequests() {
            return this.storageUpdateRequests;
        }
    }

    private static final class TestCompartmentHost implements CompartmentHost {

        private final CompartmentHostState compartments = new CompartmentHostState();

        @Override
        public void compartmentHost$addCompartment(String structureName, CompartmentPart part) {
            this.compartments.addCompartment(structureName, part);
        }

        @Override
        public void compartmentHost$removeCompartment(String structureName, CompartmentPart part) {
            this.compartments.removeCompartment(structureName, part);
        }

        @Override
        public Collection<CompartmentPart> compartmentHost$getCompartments(String structureName) {
            return this.compartments.compartments(structureName);
        }
    }
}
