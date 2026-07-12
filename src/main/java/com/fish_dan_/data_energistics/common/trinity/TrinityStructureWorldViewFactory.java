package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import com.modularmc.mdl.api.multiblock.StructureWorldView;
import org.jetbrains.annotations.Nullable;

/** Creates structure world views that retain the first unloaded position observed during one match attempt. */
public interface TrinityStructureWorldViewFactory {

    /**
     * Creates an isolated tracking view for one validation pass.
     *
     * @param level world being validated
     * @return tracking structure view
     */
    View create(Level level);

    /** Structure matcher view with an unloaded-position observation channel. */
    interface View extends StructureWorldView {

        /**
         * Returns the first position for which {@link #isLoaded(BlockPos)} returned false.
         *
         * @return observed unloaded position, or {@code null} when every queried position was loaded
         */
        @Nullable
        BlockPos firstUnloadedPosition();
    }
}
