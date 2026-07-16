package com.fish_dan_.data_energistics.mixin.neoecoae;

import com.fish_dan_.data_energistics.client.integration.NeoEcoAdditionalRendererGuard;

import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer;

import cn.dancingsnow.neoecoae.client.NeoECOAEClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = NeoECOAEClient.class, remap = false)
public class NeoECOAEClientMixin {

    @WrapOperation(
                   method = "onAddChunkGeometry",
                   at = @At(
                            value = "INVOKE",
                            target = "Lnet/neoforged/neoforge/client/event/AddSectionGeometryEvent;addRenderer(Lnet/neoforged/neoforge/client/event/AddSectionGeometryEvent$AdditionalSectionRenderer;)V"),
                   remap = false)
    private static void dataEnergistics$guardAdditionalRenderer(
                                                                AddSectionGeometryEvent event, AdditionalSectionRenderer renderer, Operation<Void> original) {
        original.call(event, NeoEcoAdditionalRendererGuard.guard(renderer, event.getSectionOrigin()));
    }
}
