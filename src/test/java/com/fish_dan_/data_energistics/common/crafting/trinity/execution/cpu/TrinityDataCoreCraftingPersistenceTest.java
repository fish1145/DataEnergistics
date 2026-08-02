package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuContribution;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreCraftingPersistenceTest {

    private static final int RUNTIME_SCHEMA_VERSION = 2;
    private static final int CPU_LOGIC_SCHEMA_VERSION = 1;

    private TrinityDataCoreCraftingPersistenceTest() {}

    @TestHolder("trinity_data_core_current_cpu_runtime_round_trips")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void currentCpuRuntimeRoundTrips(GameTestHelper helper) {
        TrinityDataCoreCraftingRuntime original = runtime(helper, new BlockPos(1, 1, 1));
        original.setContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 2, 1));

        CompoundTag saved = new CompoundTag();
        original.writeToTag(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(
                saved.getInt("schema_version"),
                RUNTIME_SCHEMA_VERSION,
                "Current runtime should persist its schema version");

        TrinityDataCoreCraftingRuntime restored = runtime(helper, new BlockPos(2, 1, 1));
        restored.readFromTag(saved, helper.getLevel().registryAccess());

        helper.assertValueEqual(restored.profile().partitionCount(), 1, "Current runtime should restore its worker capacity");
        helper.assertValueEqual(
                saved.getList("partitions", 10).size(),
                0,
                "Idle runtime should not persist reserved CPU 0 or empty workers");
        helper.assertValueEqual(restored.profile().storageBytes(), 1024L, "Current runtime should restore CPU storage");
        helper.assertValueEqual(restored.profile().coProcessors(), 2, "Current runtime should restore co-processors");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_unsupported_cpu_runtime_schema_resets_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unsupportedCpuRuntimeSchemaResetsState(GameTestHelper helper) {
        TrinityDataCoreCraftingRuntime source = runtime(helper, new BlockPos(1, 1, 1));
        source.setContribution("source", TrinityDataCoreCpuContribution.of(1024L, 2, 1));
        CompoundTag unsupportedTag = new CompoundTag();
        source.writeToTag(unsupportedTag, helper.getLevel().registryAccess());
        unsupportedTag.putInt("schema_version", RUNTIME_SCHEMA_VERSION + 1);

        TrinityDataCoreCraftingRuntime unsupported = runtime(helper, new BlockPos(2, 1, 1));
        unsupported.setContribution("existing", TrinityDataCoreCpuContribution.of(2048L, 1, 1));
        unsupported.readFromTag(unsupportedTag, helper.getLevel().registryAccess());

        helper.assertValueEqual(
                unsupported.partitions().size(),
                0,
                "Unsupported runtime schema must not restore CPU partitions");
        helper.assertValueEqual(
                unsupported.profile().storageBytes(),
                0L,
                "Unsupported runtime schema must clear existing CPU storage");
        helper.assertFalse(unsupported.hasBusyJobs(), "Unsupported runtime schema must not restore running jobs");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_unsupported_cpu_logic_schema_resets_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unsupportedCpuLogicSchemaResetsState(GameTestHelper helper) {
        TrinityDataCoreVirtualCpu cpu = activeCpu(helper, new BlockPos(1, 1, 1));
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        CompoundTag current = cpu.logic().writeToTag(helper.getLevel().registryAccess());
        ListTag inventory = new ListTag();
        CompoundTag storedIron = iron.toTagGeneric(helper.getLevel().registryAccess());
        storedIron.putLong("#", 7L);
        inventory.add(storedIron);
        current.put("inventory", inventory);
        cpu.logic().readFromTag(current, helper.getLevel().registryAccess());
        helper.assertValueEqual(cpu.getStored(iron), 7L, "Supported CPU logic schema should restore inventory state");

        CompoundTag unsupported = current.copy();
        unsupported.putInt("schema_version", CPU_LOGIC_SCHEMA_VERSION + 1);
        cpu.logic().readFromTag(unsupported, helper.getLevel().registryAccess());

        helper.assertValueEqual(cpu.getStored(iron), 0L, "Unsupported CPU logic schema must not retain inventory state");
        helper.assertFalse(cpu.logic().hasJob(), "Unsupported CPU logic schema must not restore a job");
        helper.succeed();
    }

    private static TrinityDataCoreVirtualCpu activeCpu(GameTestHelper helper, BlockPos pos) {
        TestHost host = new TestHost(helper.absolutePos(pos));
        host.setLevel(helper.getLevel());
        TrinityDataCoreCraftingRuntime runtime = host.getCraftingRuntime();
        runtime.setMainStructureFormed(true);
        runtime.setContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 0, 1));
        return new TrinityDataCoreVirtualCpu(host, runtime, runtime.profile().partition(1));
    }

    private static TrinityDataCoreCraftingRuntime runtime(GameTestHelper helper, BlockPos pos) {
        TestHost host = new TestHost(helper.absolutePos(pos));
        host.setLevel(helper.getLevel());
        TrinityDataCoreCraftingRuntime runtime = host.getCraftingRuntime();
        runtime.setMainStructureFormed(true);
        return runtime;
    }

    private static final class TestHost extends TrinityDataCoreBlockEntity {

        private TestHost(BlockPos pos) {
            super(pos, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        }
    }
}
