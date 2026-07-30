package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreMetadata;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import appeng.hooks.WrenchHook;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrinityPatternCoreBlockEntity patternCore) {
            if (patternCore.isCoreStateReady()) {
                MenuOpener.open(ModMenus.TRINITY_PATTERN_CORE.get(), player, MenuLocators.forBlockEntity(patternCore));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return createMiningDrops(blockEntity);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrinityPatternCoreBlockEntity patternCore) {
            if (!patternCore.hasMiningDropSnapshot()) {
                patternCore.freezeMiningDropSnapshot();
            }
            if (patternCore.hasMiningDropSnapshot() && player.getAbilities().instabuild &&
                    !WrenchHook.isDisassembling()) {
                for (ItemStack drop : patternCore.getMiningDrops()) {
                    Block.popResource(level, pos, drop);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (level.isClientSide()) {
            return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        }
        if (!(level.getBlockEntity(pos) instanceof TrinityPatternCoreBlockEntity patternCore)) {
            Data_Energistics.LOGGER.error(
                    "Refused to remove Trinity pattern core at {} because its mining-drop snapshot has no block entity",
                    pos);
            return false;
        }
        if (!patternCore.hasMiningDropSnapshot()) {
            Data_Energistics.LOGGER.error(
                    "Refused to remove Trinity pattern core at {} because its mining-drop snapshot was not frozen",
                    pos);
            return false;
        }
        boolean removed = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        if (!removed) {
            patternCore.discardMiningDropSnapshot();
        }
        return removed;
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

    private List<ItemStack> createMiningDrops(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof TrinityPatternCoreBlockEntity patternCore) {
            return patternCore.getMiningDrops();
        }
        Data_Energistics.LOGGER.error("Cannot create Trinity pattern core drops without its block entity");
        return List.of();
    }
}
