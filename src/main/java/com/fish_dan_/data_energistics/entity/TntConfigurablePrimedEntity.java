package com.fish_dan_.data_energistics.entity;

import com.fish_dan_.data_energistics.FlatteningTntConfig;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

public class TntConfigurablePrimedEntity extends AbstractFlatteningTntPrimedEntity {

    public TntConfigurablePrimedEntity(EntityType<? extends TntConfigurablePrimedEntity> entityType, Level level) {
        super(entityType, level);
    }

    public TntConfigurablePrimedEntity(Level level, BlockPos origin, @Nullable LivingEntity owner) {
        super(ModEntities.TNT_CONFIGURABLE_PRIMED.get(), level, origin, owner,
                ModBlocks.TNT_CONFIGURABLE.get().defaultBlockState());
    }

    @Override
    protected FlatteningTntConfig.Definition getDefinition() {
        return FlatteningTntConfig.configurableTnt;
    }
}
