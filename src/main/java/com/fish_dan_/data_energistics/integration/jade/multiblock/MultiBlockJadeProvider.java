package com.fish_dan_.data_energistics.integration.jade.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockFailureText;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockStatusProvider;
import com.fish_dan_.data_energistics.menu.trinity.TrinityDataCoreMenuHost;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class MultiBlockJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "multiblock");
    public static final ResourceLocation BLOCKS_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "multiblock.blocks");
    public static final ResourceLocation ROLE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "multiblock.role");
    public static final ResourceLocation DEBUG_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "multiblock.debug");
    private static final String TAG_FORMED = "formed";
    private static final String TAG_ONLINE = "online";
    private static final String TAG_HEIGHT = "height";
    private static final String TAG_MATCHED_BLOCK_COUNT = "matched_block_count";
    private static final String TAG_CONTROLLER = "controller";
    private static final String TAG_FAILURE_REASON = "failure_reason";
    private static final String TAG_FAILURE_X = "failure_x";
    private static final String TAG_FAILURE_Y = "failure_y";
    private static final String TAG_FAILURE_Z = "failure_z";
    private static final String TAG_TRINITY_DATA_CORE = "trinity_data_core";
    private static final String TAG_CPU_STRUCTURE_FORMED = "cpu_structure_formed";
    private static final String TAG_CPU_STRUCTURE_MATCHED_BLOCK_COUNT = "cpu_structure_matched_block_count";
    private static final String TAG_CPU_FAILURE_REASON = "cpu_failure_reason";
    private static final String TAG_CPU_FAILURE_X = "cpu_failure_x";
    private static final String TAG_CPU_FAILURE_Y = "cpu_failure_y";
    private static final String TAG_CPU_FAILURE_Z = "cpu_failure_z";
    private static final String TAG_CRAFTING_STRUCTURE_FORMED = "crafting_structure_formed";
    private static final String TAG_CRAFTING_STRUCTURE_MATCHED_BLOCK_COUNT = "crafting_structure_matched_block_count";
    private static final String TAG_CRAFTING_PATTERN_CORE_COUNT = "crafting_pattern_core_count";
    private static final String TAG_CRAFTING_PATTERN_CAPACITY = "crafting_pattern_capacity";
    private static final String TAG_CRAFTING_FAILURE_REASON = "crafting_failure_reason";
    private static final String TAG_CRAFTING_FAILURE_X = "crafting_failure_x";
    private static final String TAG_CRAFTING_FAILURE_Y = "crafting_failure_y";
    private static final String TAG_CRAFTING_FAILURE_Z = "crafting_failure_z";
    private static final String TAG_STORED_TYPE_COUNT = "stored_type_count";
    private static final String TAG_STORED_AMOUNT = "stored_amount";
    private static final String TAG_STORED_TYPE_CAPACITY = "stored_type_capacity";
    private static final String TAG_STORED_AMOUNT_CAPACITY = "stored_amount_capacity";
    private static final String TAG_CPU_PARTITION_COUNT = "cpu_partition_count";
    private static final String TAG_BUSY_CPU_PARTITION_COUNT = "busy_cpu_partition_count";
    private static final String TAG_CPU_STORAGE_BYTES = "cpu_storage_bytes";
    private static final String TAG_CPU_CO_PROCESSORS = "cpu_co_processors";

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(TAG_FORMED)) {
            return;
        }

        tooltip.add(Component.translatable(
                serverData.getBoolean(TAG_FORMED) ? "jade.data_energistics.multiblock.formed" : "jade.data_energistics.multiblock.unformed"));
        if (serverData.getBoolean(TAG_FORMED)) {
            appendFormedTooltip(tooltip, serverData, config);
        } else {
            appendFailureTooltip(tooltip, serverData);
        }
        if (config.get(DEBUG_ID) && serverData.getBoolean(TAG_TRINITY_DATA_CORE)) {
            appendTrinityDataCoreDebugTooltip(tooltip, serverData);
        }
    }

    private static void appendFormedTooltip(ITooltip tooltip, CompoundTag serverData, IPluginConfig config) {
        int height = serverData.getInt(TAG_HEIGHT);
        if (height > 0) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.height",
                    height));
        }
        int matchedBlockCount = serverData.getInt(TAG_MATCHED_BLOCK_COUNT);
        if (config.get(BLOCKS_ID) && matchedBlockCount > 0) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.blocks",
                    matchedBlockCount));
        }
        if (config.get(ROLE_ID)) {
            tooltip.add(Component.translatable(
                    serverData.getBoolean(TAG_CONTROLLER) ? "jade.data_energistics.multiblock.role.controller" : "jade.data_energistics.multiblock.role.part"));
        }
    }

    private static void appendFailureTooltip(ITooltip tooltip, CompoundTag serverData) {
        if (serverData.contains(TAG_FAILURE_REASON)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.failure",
                    serverData.getBoolean(TAG_TRINITY_DATA_CORE) ?
                            MultiBlockFailureText.describeTrinityDataCore(serverData.getString(TAG_FAILURE_REASON)) :
                            MultiBlockFailureText.describe(serverData.getString(TAG_FAILURE_REASON))));
        }
        if (serverData.contains(TAG_FAILURE_X) && serverData.contains(TAG_FAILURE_Y) && serverData.contains(TAG_FAILURE_Z)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.failure_position",
                    serverData.getInt(TAG_FAILURE_X),
                    serverData.getInt(TAG_FAILURE_Y),
                    serverData.getInt(TAG_FAILURE_Z)));
        }
    }

    private static void appendTrinityDataCoreDebugTooltip(ITooltip tooltip, CompoundTag serverData) {
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.online",
                Component.translatable(serverData.getBoolean(TAG_ONLINE) ? "jade.data_energistics.yes" : "jade.data_energistics.no")));
        tooltip.add(Component.translatable(
                serverData.getBoolean(TAG_CPU_STRUCTURE_FORMED) ? "jade.data_energistics.multiblock.cpu_structure.formed" : "jade.data_energistics.multiblock.cpu_structure.unformed"));
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.cpu_blocks",
                serverData.getInt(TAG_CPU_STRUCTURE_MATCHED_BLOCK_COUNT)));
        appendCpuFailureTooltip(tooltip, serverData);
        tooltip.add(Component.translatable(
                serverData.getBoolean(TAG_CRAFTING_STRUCTURE_FORMED) ? "jade.data_energistics.multiblock.crafting_structure.formed" : "jade.data_energistics.multiblock.crafting_structure.unformed"));
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.crafting_blocks",
                serverData.getInt(TAG_CRAFTING_STRUCTURE_MATCHED_BLOCK_COUNT)));
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.crafting_pattern_cores",
                serverData.getInt(TAG_CRAFTING_PATTERN_CORE_COUNT)));
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.crafting_pattern_capacity",
                serverData.getInt(TAG_CRAFTING_PATTERN_CAPACITY)));
        appendCraftingFailureTooltip(tooltip, serverData);
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.storage_types",
                serverData.getInt(TAG_STORED_TYPE_COUNT),
                serverData.getString(TAG_STORED_TYPE_CAPACITY)));
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.storage_amount",
                serverData.getString(TAG_STORED_AMOUNT),
                serverData.getString(TAG_STORED_AMOUNT_CAPACITY)));
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.cpu_partitions",
                serverData.getInt(TAG_BUSY_CPU_PARTITION_COUNT),
                serverData.getInt(TAG_CPU_PARTITION_COUNT)));
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.cpu_storage",
                serverData.getLong(TAG_CPU_STORAGE_BYTES)));
        tooltip.add(Component.translatable(
                "jade.data_energistics.multiblock.cpu_coprocessors",
                formatCpuCoProcessors(serverData.getInt(TAG_CPU_CO_PROCESSORS))));
    }

    private static void appendCpuFailureTooltip(ITooltip tooltip, CompoundTag serverData) {
        if (serverData.contains(TAG_CPU_FAILURE_REASON)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.cpu_failure",
                    MultiBlockFailureText.describeTrinityDataCore(serverData.getString(TAG_CPU_FAILURE_REASON))));
        }
        if (serverData.contains(TAG_CPU_FAILURE_X) && serverData.contains(TAG_CPU_FAILURE_Y) && serverData.contains(TAG_CPU_FAILURE_Z)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.cpu_failure_position",
                    serverData.getInt(TAG_CPU_FAILURE_X),
                    serverData.getInt(TAG_CPU_FAILURE_Y),
                    serverData.getInt(TAG_CPU_FAILURE_Z)));
        }
    }

    private static Component formatCpuCoProcessors(int coProcessors) {
        return coProcessors == Integer.MAX_VALUE ?
                Component.translatable("gui.data_energistics.trinity.unlimited") :
                Component.literal(Integer.toString(coProcessors));
    }

    private static void appendCraftingFailureTooltip(ITooltip tooltip, CompoundTag serverData) {
        if (serverData.contains(TAG_CRAFTING_FAILURE_REASON)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.crafting_failure",
                    MultiBlockFailureText.describeTrinityDataCore(serverData.getString(TAG_CRAFTING_FAILURE_REASON))));
        }
        if (serverData.contains(TAG_CRAFTING_FAILURE_X) && serverData.contains(TAG_CRAFTING_FAILURE_Y) &&
                serverData.contains(TAG_CRAFTING_FAILURE_Z)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.crafting_failure_position",
                    serverData.getInt(TAG_CRAFTING_FAILURE_X),
                    serverData.getInt(TAG_CRAFTING_FAILURE_Y),
                    serverData.getInt(TAG_CRAFTING_FAILURE_Z)));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        MultiBlockStatusProvider multiBlock = resolveMultiBlock(accessor);
        if (multiBlock == null) {
            return;
        }

        data.putBoolean(TAG_FORMED, multiBlock.multiBlock$isFormed());
        data.putBoolean(TAG_ONLINE, multiBlock.multiBlock$isOnline());
        data.putInt(TAG_HEIGHT, multiBlock.multiBlock$getHeight());
        data.putInt(TAG_MATCHED_BLOCK_COUNT, multiBlock.multiBlock$getMatchedBlockCount());
        data.putBoolean(TAG_CONTROLLER, multiBlock.multiBlock$isController());
        String failureReason = multiBlock.multiBlock$getLastFailureReason();
        if (!failureReason.isBlank()) {
            data.putString(TAG_FAILURE_REASON, failureReason);
        }
        BlockPos failurePosition = multiBlock.multiBlock$getLastFailurePosition();
        if (failurePosition != null) {
            data.putInt(TAG_FAILURE_X, failurePosition.getX());
            data.putInt(TAG_FAILURE_Y, failurePosition.getY());
            data.putInt(TAG_FAILURE_Z, failurePosition.getZ());
        }
        if (multiBlock instanceof TrinityDataCoreMenuHost host) {
            appendTrinityDataCoreServerData(data, host);
        }
    }

    private static void appendTrinityDataCoreServerData(CompoundTag data, TrinityDataCoreMenuHost host) {
        data.putBoolean(TAG_TRINITY_DATA_CORE, true);
        data.putBoolean(TAG_CPU_STRUCTURE_FORMED, host.isCpuStructureFormed());
        data.putInt(TAG_CPU_STRUCTURE_MATCHED_BLOCK_COUNT, host.getCpuStructureMatchedBlockCount());
        String cpuFailureReason = host.getCpuLastFailureReason();
        if (!cpuFailureReason.isBlank()) {
            data.putString(TAG_CPU_FAILURE_REASON, cpuFailureReason);
        }
        BlockPos cpuFailurePosition = host.getCpuLastFailurePosition();
        if (cpuFailurePosition != null) {
            data.putInt(TAG_CPU_FAILURE_X, cpuFailurePosition.getX());
            data.putInt(TAG_CPU_FAILURE_Y, cpuFailurePosition.getY());
            data.putInt(TAG_CPU_FAILURE_Z, cpuFailurePosition.getZ());
        }
        data.putBoolean(TAG_CRAFTING_STRUCTURE_FORMED, host.isCraftingStructureFormed());
        data.putInt(TAG_CRAFTING_STRUCTURE_MATCHED_BLOCK_COUNT, host.getCraftingStructureMatchedBlockCount());
        data.putInt(TAG_CRAFTING_PATTERN_CORE_COUNT, host.getCraftingPatternCoreCount());
        data.putInt(TAG_CRAFTING_PATTERN_CAPACITY, host.getCraftingPatternCapacity());
        String craftingFailureReason = host.getCraftingLastFailureReason();
        if (!craftingFailureReason.isBlank()) {
            data.putString(TAG_CRAFTING_FAILURE_REASON, craftingFailureReason);
        }
        BlockPos craftingFailurePosition = host.getCraftingLastFailurePosition();
        if (craftingFailurePosition != null) {
            data.putInt(TAG_CRAFTING_FAILURE_X, craftingFailurePosition.getX());
            data.putInt(TAG_CRAFTING_FAILURE_Y, craftingFailurePosition.getY());
            data.putInt(TAG_CRAFTING_FAILURE_Z, craftingFailurePosition.getZ());
        }
        data.putInt(TAG_STORED_TYPE_COUNT, host.getStoredTypeCount());
        data.putString(TAG_STORED_AMOUNT, host.getStoredAmountText());
        data.putString(TAG_STORED_TYPE_CAPACITY, host.getStoredTypeCapacityText());
        data.putString(TAG_STORED_AMOUNT_CAPACITY, host.getStoredAmountCapacityText());
        data.putInt(TAG_CPU_PARTITION_COUNT, host.getCpuPartitionCount());
        data.putInt(TAG_BUSY_CPU_PARTITION_COUNT, host.getBusyCpuPartitionCount());
        data.putLong(TAG_CPU_STORAGE_BYTES, host.getCpuStorageBytes());
        data.putInt(TAG_CPU_CO_PROCESSORS, host.getCpuCoProcessors());
    }

    private static MultiBlockStatusProvider resolveMultiBlock(BlockAccessor accessor) {
        return accessor.getBlockEntity() instanceof MultiBlockStatusProvider multiBlock ? multiBlock : null;
    }
}
