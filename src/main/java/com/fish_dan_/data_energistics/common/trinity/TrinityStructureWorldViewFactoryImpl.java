package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/** Default tracking world-view factory backed by a Minecraft level. */
public final class TrinityStructureWorldViewFactoryImpl implements TrinityStructureWorldViewFactory {

    @Override
    public View create(Level level) {
        return new LevelView(level);
    }

    /** One-match view that records unloaded access without loading chunks. */
    private static final class LevelView implements View {

        /** World delegated to by every structure lookup. */
        private final Level level;

        /** First unavailable matcher coordinate, retained for deferred scheduling. */
        @Nullable
        private BlockPos firstUnloadedPosition;

        /**
         * Creates a view for one matcher invocation.
         *
         * @param level world delegated to by this view
         */
        private LevelView(Level level) {
            this.level = level;
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            boolean loaded = this.level.isLoaded(pos);
            if (!loaded && this.firstUnloadedPosition == null) {
                this.firstUnloadedPosition = pos.immutable();
            }
            return loaded;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.level.getBlockState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.level.getBlockEntity(pos);
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.level.registryAccess();
        }

        @Nullable
        @Override
        public BlockPos firstUnloadedPosition() {
            return this.firstUnloadedPosition;
        }
    }
}
