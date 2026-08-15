package com.fish_dan_.data_energistics.mixin.core.accessor.ae2;

import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderBatchAccess;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Set;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicFieldAccessor extends PatternProviderBatchAccess {

    @Override
    @Accessor("host")
    PatternProviderLogicHost dataEnergistics$getHost();

    @Accessor("actionSource")
    IActionSource dataEnergistics$getActionSource();

    @Override
    @Accessor("mainNode")
    IManagedGridNode dataEnergistics$getMainNode();

    @Override
    @Accessor("patterns")
    List<IPatternDetails> dataEnergistics$getPatterns();

    @Override
    @Accessor("patternInputs")
    Set<AEKey> dataEnergistics$getPatternInputs();

    @Override
    @Accessor("sendList")
    List<GenericStack> dataEnergistics$getSendList();

    @Override
    @Accessor("roundRobinIndex")
    int dataEnergistics$getRoundRobinIndex();

    @Override
    @Accessor("roundRobinIndex")
    void dataEnergistics$setRoundRobinIndex(int roundRobinIndex);

    @Override
    @Accessor("sendDirection")
    void dataEnergistics$setSendDirection(Direction direction);

    @Override
    @Invoker("getActiveSides")
    Set<Direction> dataEnergistics$invokeGetActiveSides();

    @Override
    @Invoker("findAdapter")
    @Nullable
    PatternProviderTarget dataEnergistics$invokeFindAdapter(Direction side);

    @Override
    @Invoker("sendStacksOut")
    boolean dataEnergistics$invokeSendStacksOut();

    @Override
    @Invoker("onPushPatternSuccess")
    void dataEnergistics$invokeOnPushPatternSuccess(IPatternDetails patternDetails);
}
