package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.blockentity.machine.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.TrinityServerTickMetrics;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEventHandler;
import com.fish_dan_.data_energistics.common.tick.ServerTickDelayQueue;
import com.fish_dan_.data_energistics.effect.RadixLossControlLogic;
import com.fish_dan_.data_energistics.item.powered.DataCrystalSwordAiStripLogic;
import com.fish_dan_.data_energistics.item.powered.PersistentFarmlandLogic;
import com.fish_dan_.data_energistics.recipe.containmentsphere.RadixContainmentSphereRightClickRecipeLogic;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftTransformLogic;
import com.fish_dan_.data_energistics.world.meteorite.DataMeteoriteCompassTargetInvalidation;
import com.fish_dan_.data_energistics.world.sanctum.DataSanctumPortalLogic;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

final class CommonEventRegistrar {

    private CommonEventRegistrar() {}

    static void register() {
        NeoForge.EVENT_BUS.register(new ServerLifecycleEventHandler());
        NeoForge.EVENT_BUS.register(new PoweredToolAttributeModifierHandler());
        NeoForge.EVENT_BUS.register(new TimeShiftTransformLogic());
        NeoForge.EVENT_BUS.register(new RadixContainmentSphereRightClickRecipeLogic());
        NeoForge.EVENT_BUS.register(new DataCrystalSwordAiStripLogic());
        NeoForge.EVENT_BUS.register(new RadixLossControlLogic());
        NeoForge.EVENT_BUS.register(new PersistentFarmlandLogic());
        NeoForge.EVENT_BUS.register(new RecipeReloadEventHandler());
        NeoForge.EVENT_BUS.register(new DataMeteoriteCompassTargetInvalidation());
        NeoForge.EVENT_BUS.register(new DataSanctumPortalLogic());
        NeoForge.EVENT_BUS.register(new ServerTickDelayQueue());
        NeoForge.EVENT_BUS.register(new TrinityServerTickMetrics());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DataMimeticFieldBlockEntity::captureSimulatedSpawnedDrops);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DataMimeticFieldBlockEntity::captureSimulatedDeathDrops);
    }
}
