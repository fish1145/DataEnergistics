package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorageImpl;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
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
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

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
}
