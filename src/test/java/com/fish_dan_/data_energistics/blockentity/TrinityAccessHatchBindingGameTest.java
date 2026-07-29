package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentBindingHandle;
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
        CompartmentBindingHandle previousBinding = hatch.compartment$bindingHandle();
        previousHost.onRemove = () -> hatch.compartment$bindToHost("main", replacementHost);

        hatch.compartment$bindToHost("main", replacementHost);

        helper.assertTrue(hatch.compartmentHost() == replacementHost,
                "A re-entrant old release must not clear the replacement host");
        helper.assertTrue("main".equals(hatch.compartmentStructureName()),
                "A replacement binding must retain its structure name");
        helper.assertTrue(previousHost.compartments("main").isEmpty(),
                "The old host must not retain its registration");
        helper.assertValueEqual(replacementHost.compartments("main"), List.of(hatch),
                "The replacement host must retain the hatch registration");

        hatch.compartment$unbindFromHost(previousBinding);
        helper.assertTrue(hatch.compartmentHost() == replacementHost,
                "A stale old-host callback must not clear the replacement binding");
        helper.assertValueEqual(replacementHost.compartments("main"), List.of(hatch),
                "A stale old-host callback must not remove the replacement registration");

        CompartmentBindingHandle replacementBinding = hatch.compartment$bindingHandle();
        hatch.compartment$unbindFromHost(replacementBinding);
        helper.assertTrue(hatch.compartmentHost() == null,
                "The current binding should clear after its matching unbind");
        helper.assertTrue(replacementHost.compartments("main").isEmpty(),
                "The replacement host should be empty after its matching unbind");

        hatch.compartment$bindToHost("main", replacementHost);
        CompartmentBindingHandle olderSameHostBinding = hatch.compartment$bindingHandle();
        hatch.compartment$unbindFromHost(olderSameHostBinding);
        hatch.compartment$bindToHost("main", replacementHost);
        CompartmentBindingHandle newerSameHostBinding = hatch.compartment$bindingHandle();

        hatch.compartment$unbindFromHost("main", replacementHost);
        hatch.compartment$unbindFromHost(olderSameHostBinding);
        helper.assertTrue(hatch.compartmentHost() == replacementHost,
                "An untagged or old same-host callback must not clear the newer binding");
        helper.assertValueEqual(replacementHost.compartments("main"), List.of(hatch),
                "An old same-host callback must not remove the newer registration");

        TestHost failingHost = new TestHost();
        TestHost retryHost = new TestHost();
        hatch.compartment$unbindFromHost(newerSameHostBinding);
        hatch.compartment$bindToHost("main", failingHost);
        CompartmentBindingHandle failingBinding = hatch.compartment$bindingHandle();
        failingHost.onRemove = () -> hatch.compartment$bindToHost("main", retryHost);
        failingHost.failAfterRemove = true;

        boolean releaseFailed = false;
        try {
            hatch.compartment$unbindFromHost(failingBinding);
        } catch (IllegalStateException expected) {
            releaseFailed = true;
        }
        helper.assertTrue(releaseFailed,
                "A host removal failure must propagate instead of discarding the releasing binding identity");
        helper.assertTrue(hatch.compartmentHost() == failingHost,
                "A failed release must retain its paired old host identity for retry");
        helper.assertTrue(retryHost.compartments("main").isEmpty(),
                "A re-entrant replacement must wait until old host removal succeeds");

        failingHost.failAfterRemove = false;
        hatch.compartment$unbindFromHost(failingBinding);
        helper.assertTrue(hatch.compartmentHost() == retryHost,
                "Retrying with the original failed binding handle must publish the queued replacement after release succeeds");
        helper.assertTrue(failingHost.compartments("main").isEmpty(),
                "The failed host must remain empty after the successful release retry");
        helper.assertValueEqual(retryHost.compartments("main"), List.of(hatch),
                "The queued replacement must register exactly once after retry");

        hatch.compartment$unbindFromHost(hatch.compartment$bindingHandle());
        helper.assertTrue(retryHost.compartments("main").isEmpty(),
                "The final matching handle should release the retried binding");
        helper.succeed();
    }

    private static final class TestHost implements CompartmentHost {

        private final CompartmentHostState state = new CompartmentHostState();
        private Runnable onRemove;
        private boolean failAfterRemove;

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
            if (this.failAfterRemove) {
                throw new IllegalStateException("Requested test host removal failure");
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
