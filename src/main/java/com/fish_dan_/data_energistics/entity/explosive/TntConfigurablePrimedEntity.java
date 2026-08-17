package com.fish_dan_.data_energistics.entity.explosive;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.ConfigurableTntSchema;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

public class TntConfigurablePrimedEntity extends AbstractFlatteningTntPrimedEntity {

    public TntConfigurablePrimedEntity(EntityType<? extends TntConfigurablePrimedEntity> entityType, Level level) {
        super(entityType, level);
    }

    public TntConfigurablePrimedEntity(Level level, BlockPos origin, @Nullable LivingEntity owner) {
        super(DEEntities.TNT_CONFIGURABLE_PRIMED.get(), level, origin, owner,
                DEBlocks.TNT_CONFIGURABLE.get().defaultBlockState());
    }

    @Override
    protected ConfigurableTntSchema getDefinition() {
        return DataEnergisticsConfiguration.INSTANCE.explosives.flatteningTnt;
    }
}
