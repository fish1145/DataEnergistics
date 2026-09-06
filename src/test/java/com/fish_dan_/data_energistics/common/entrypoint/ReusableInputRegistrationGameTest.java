package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.Optional;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableInputRegistrationGameTest {

    private ReusableInputRegistrationGameTest() {}

    @TestHolder("reusable_input_plugin_registration_is_atomic_and_closed_after_commit")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void registrationIsAtomicAndClosedAfterCommit(GameTestHelper helper) {
        PluginRegistrationAccumulator accumulator = new PluginRegistrationAccumulator();
        var first = accumulator.createStaging("test", "first");
        RuleAdapter adapter = new RuleAdapter(ResourceLocation.fromNamespaceAndPath("test", "tool"));
        first.reusableInputs().register(adapter);
        accumulator.commit(first);
        expectClosed(helper, () -> first.reusableInputs().register(adapter));
        var duplicate = accumulator.createStaging("test", "second");
        duplicate.reusableInputs().register(adapter);
        expectClosed(helper, () -> accumulator.commit(duplicate));
        duplicate.discard();
        var discarded = accumulator.createStaging("test", "discarded");
        discarded.reusableInputs().register(new RuleAdapter(ResourceLocation.fromNamespaceAndPath("test", "unused")));
        discarded.discard();
        expectClosed(helper, () -> accumulator.commit(discarded));
        accumulator.freeze();
        expectClosed(helper, () -> accumulator.createStaging("test", "late"));
        helper.succeed();
    }

    private static void expectClosed(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail("Registry lifecycle or duplicate ownership must reject the operation");
    }

    private record RuleAdapter(ResourceLocation id) implements ReusableInputRuleAdapter {

        @Override
        public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
            return Optional.empty();
        }
    }
}
