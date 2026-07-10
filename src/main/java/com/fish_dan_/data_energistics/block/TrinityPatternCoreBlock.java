package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreMetadata;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import appeng.hooks.WrenchHook;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Entity-backed variant used exclusively by the three Trinity pattern processing core blocks.
 */
public final class TrinityPatternCoreBlock extends TrinityCoreBlock implements EntityBlock {

    /**
     * Creates a P-core block whose fixed capacity is exposed through its inherited component metadata.
     *
     * @param properties block properties
     * @param metadata   pattern-processing metadata
     */
    public TrinityPatternCoreBlock(Properties properties, TrinityCoreMetadata metadata) {
        super(properties, metadata);
        if (metadata.kind() != TrinityCoreKind.PATTERN_PROCESSING) {
            throw new IllegalArgumentException("TrinityPatternCoreBlock requires pattern processing metadata");
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrinityPatternCoreBlockEntity(pos, state);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return List.of(createCoreDrop(blockEntity));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.getAbilities().instabuild && !WrenchHook.isDisassembling() &&
                level.getBlockEntity(pos) instanceof TrinityPatternCoreBlockEntity patternCore) {
            Block.popResource(level, pos, createCoreDrop(patternCore));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != ModBlockEntities.TRINITY_PATTERN_CORE_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof TrinityPatternCoreBlockEntity patternCore) {
                patternCore.serverTick();
            }
        };
    }

    private ItemStack createCoreDrop(@Nullable BlockEntity blockEntity) {
        ItemStack stack = new ItemStack(this);
        if (blockEntity instanceof TrinityPatternCoreBlockEntity patternCore) {
            patternCore.saveToItem(stack, patternCore.getLevel().registryAccess());
        }
        return stack;
    }
}
