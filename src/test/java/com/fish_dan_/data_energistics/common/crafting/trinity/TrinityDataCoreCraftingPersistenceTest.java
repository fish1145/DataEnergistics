package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;
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

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreCraftingPersistenceTest {

    private static final int SCHEMA_VERSION = 1;

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
                SCHEMA_VERSION,
                "Current runtime should persist its schema version");

        TrinityDataCoreCraftingRuntime restored = runtime(helper, new BlockPos(2, 1, 1));
        restored.readFromTag(saved, helper.getLevel().registryAccess());

        helper.assertValueEqual(restored.partitions().size(), 1, "Current runtime should restore its CPU partition");
        helper.assertValueEqual(restored.profile().storageBytes(), 1024L, "Current runtime should restore CPU storage");
        helper.assertValueEqual(restored.profile().coProcessors(), 2, "Current runtime should restore co-processors");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_legacy_cpu_runtime_is_ignored")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void legacyCpuRuntimeIsIgnored(GameTestHelper helper) {
        TrinityDataCoreCraftingRuntime runtime = runtime(helper, new BlockPos(1, 1, 1));
        CompoundTag legacy = runtimeTagWithoutSchema();

        runtime.readFromTag(legacy, helper.getLevel().registryAccess());

        helper.assertValueEqual(runtime.partitions().size(), 0, "Legacy runtime must not restore CPU partitions");
        helper.assertValueEqual(runtime.profile().storageBytes(), 0L, "Legacy runtime must not restore CPU storage");
        helper.assertFalse(runtime.hasBusyJobs(), "Legacy runtime must not restore running jobs");

        TrinityDataCoreCraftingRuntime unsupported = runtime(helper, new BlockPos(2, 1, 1));
        CompoundTag unsupportedTag = runtimeTagWithoutSchema();
        unsupportedTag.putInt("schema_version", 2);
        unsupported.readFromTag(unsupportedTag, helper.getLevel().registryAccess());

        helper.assertValueEqual(
                unsupported.partitions().size(),
                0,
                "Unsupported runtime schema must not restore CPU partitions");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_legacy_cpu_logic_is_ignored")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void legacyCpuLogicIsIgnored(GameTestHelper helper) {
        TrinityDataCoreVirtualCpu cpu = activeCpu(helper, new BlockPos(1, 1, 1));
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        cpu.insert(iron, 7L, Actionable.MODULATE);

        CompoundTag legacy = new CompoundTag();
        legacy.put("inventory", new ListTag());
        legacy.put("job", new CompoundTag());
        cpu.logic().readFromTag(legacy, helper.getLevel().registryAccess());

        helper.assertValueEqual(cpu.getStored(iron), 0L, "Legacy CPU logic must not retain inventory state");
        helper.assertFalse(cpu.logic().hasJob(), "Legacy CPU logic must not restore a job");

        cpu.insert(iron, 7L, Actionable.MODULATE);
        legacy.putInt("schema_version", 2);
        cpu.logic().readFromTag(legacy, helper.getLevel().registryAccess());

        helper.assertValueEqual(cpu.getStored(iron), 0L, "Unsupported CPU logic schema must not retain inventory state");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_legacy_cpu_job_is_ignored")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void legacyCpuJobIsIgnored(GameTestHelper helper) {
        TrinityDataCoreVirtualCpu cpu = activeCpu(helper, new BlockPos(1, 1, 1));
        CompoundTag currentLogic = new CompoundTag();
        currentLogic.putInt("schema_version", SCHEMA_VERSION);
        currentLogic.put("inventory", new ListTag());
        currentLogic.put("job", new CompoundTag());

        cpu.logic().readFromTag(currentLogic, helper.getLevel().registryAccess());

        helper.assertFalse(cpu.logic().hasJob(), "Legacy unversioned job must not be restored");
        helper.succeed();
    }

    private static TrinityDataCoreVirtualCpu activeCpu(GameTestHelper helper, BlockPos pos) {
        TrinityDataCoreCraftingRuntime runtime = runtime(helper, pos);
        runtime.setContribution("cpu", TrinityDataCoreCpuContribution.of(1024L, 0, 1));
        return runtime.partitions().getFirst();
    }

    private static TrinityDataCoreCraftingRuntime runtime(GameTestHelper helper, BlockPos pos) {
        TestHost host = new TestHost(helper.absolutePos(pos));
        host.setLevel(helper.getLevel());
        TrinityDataCoreCraftingRuntime runtime = host.getCraftingRuntime();
        runtime.setMainStructureFormed(true);
        return runtime;
    }

    private static CompoundTag runtimeTagWithoutSchema() {
        CompoundTag runtime = new CompoundTag();
        ListTag contributions = new ListTag();
        CompoundTag contribution = new CompoundTag();
        contribution.putString("name", "cpu");
        contribution.putLong("storage_bytes", 1024L);
        contribution.putInt("co_processors", 2);
        contribution.putInt("partition_count", 1);
        contribution.putString("selection_mode", CpuSelectionMode.ANY.name());
        contributions.add(contribution);
        runtime.put("contributions", contributions);
        runtime.put("partitions", new ListTag());
        return runtime;
    }

    private static final class TestHost extends DigitalConstructFlowerBlockEntity {

        private TestHost(BlockPos pos) {
            super(pos, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        }
    }
}
