package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.Collection;
import java.util.List;

/**
 * Exercises the real Trinity access hatch binding entry points across a re-entrant release callback.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityAccessHatchBindingGameTest {

    private TrinityAccessHatchBindingGameTest() {}

    @TestHolder("trinity_access_hatch_old_release_cannot_clear_new_binding")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void oldReleaseCannotClearNewBinding(GameTestHelper helper) {
        TrinityAccessHatchBlockEntity hatch = new TrinityAccessHatchBlockEntity(
                BlockPos.ZERO,
                ModBlocks.TRINITY_ACCESS_HATCH.get().defaultBlockState());
        TestHost previousHost = new TestHost();
        TestHost replacementHost = new TestHost();

        hatch.compartment$bindToHost("main", previousHost);
        previousHost.onRemove = () -> hatch.compartment$bindToHost("main", replacementHost);

        hatch.compartment$bindToHost("main", replacementHost);

        helper.assertValueEqual(hatch.compartmentHost(), replacementHost,
                "A re-entrant old release must not clear the replacement host");
        helper.assertValueEqual(hatch.compartmentStructureName(), "main",
                "A replacement binding must retain its structure name");
        helper.assertTrue(previousHost.compartments("main").isEmpty(),
                "The old host must not retain its registration");
        helper.assertValueEqual(replacementHost.compartments("main"), List.of(hatch),
                "The replacement host must retain the hatch registration");

        hatch.compartment$unbindFromHost("main", previousHost);
        helper.assertValueEqual(hatch.compartmentHost(), replacementHost,
                "A stale old-host callback must not clear the replacement binding");
        helper.assertValueEqual(replacementHost.compartments("main"), List.of(hatch),
                "A stale old-host callback must not remove the replacement registration");

        hatch.compartment$unbindFromHost("main", replacementHost);
        helper.assertTrue(hatch.compartmentHost() == null,
                "The current binding should clear after its matching unbind");
        helper.assertTrue(replacementHost.compartments("main").isEmpty(),
                "The replacement host should be empty after its matching unbind");
        helper.succeed();
    }

    private static final class TestHost implements CompartmentHost {

        private final CompartmentHostState state = new CompartmentHostState();
        private Runnable onRemove;

        @Override
        public void compartmentHost$addCompartment(String structureName, CompartmentPart part) {
            this.state.addCompartment(structureName, part);
        }

        @Override
        public void compartmentHost$removeCompartment(String structureName, CompartmentPart part) {
            this.state.removeCompartment(structureName, part);
            Runnable callback = this.onRemove;
            this.onRemove = null;
            if (callback != null) {
                callback.run();
            }
        }

        @Override
        public Collection<CompartmentPart> compartmentHost$getCompartments(String structureName) {
            return this.state.compartments(structureName);
        }

        private Collection<CompartmentPart> compartments(String structureName) {
            return this.compartmentHost$getCompartments(structureName);
        }
    }
}
