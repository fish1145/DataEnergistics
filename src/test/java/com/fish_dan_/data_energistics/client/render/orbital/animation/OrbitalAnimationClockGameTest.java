package com.fish_dan_.data_energistics.client.render.orbital.animation;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalAnimationClockGameTest {

    private OrbitalAnimationClockGameTest() {}

    @TestHolder("orbital_animation_advances_between_sparse_baselines")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void advancesAcrossTicksBetweenBaselines(GameTestHelper helper) {
        OrbitalAnimationClock clock = new OrbitalAnimationClock();
        helper.assertTrue(clock.sample(100, 1000, 0.75F) == 100.75, "Initial partial tick must be retained");
        helper.assertTrue(clock.sample(100, 1001, 0.25F) == 101.25, "A new client tick must not rewind animation");
        helper.assertTrue(clock.sample(100, 1004, 0.75F) == 104.75, "Time must advance throughout the five-tick gap");
        helper.assertTrue(clock.sample(105, 1005, 0.25F) == 105.25, "An on-time baseline must preserve continuity");
        helper.assertTrue(clock.sample(105, 1005, 0.25F) == 105.25, "A paused game must hold animation time");
        helper.succeed();
    }

    @TestHolder("orbital_animation_handles_delayed_updates_and_reconnects")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void delayedBaselineDoesNotRewindAndReconnectResets(GameTestHelper helper) {
        OrbitalAnimationClock clock = new OrbitalAnimationClock();
        clock.sample(100, 1000, 0);
        helper.assertTrue(clock.sample(105, 1010, 0.5F) == 110.5, "A delayed baseline must not rewind cosmetic motion");
        helper.assertTrue(clock.sample(20, 50, 0.25F) == 20.25, "Reconnection must discard the old clock mapping");
        helper.assertTrue(clock.sample(25, 1, 0.5F) == 25.5, "A new client level must establish a fresh local origin");
        helper.succeed();
    }

    @TestHolder("orbital_animation_preserves_fractional_time_in_old_worlds")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void oldWorldRetainsSubTickAngularMotion(GameTestHelper helper) {
        double time = 1L << 40;
        float first = OrbitalAnimationClock.angle(time, Long.MAX_VALUE, 720);
        float next = OrbitalAnimationClock.angle(time + 0.25, Long.MAX_VALUE, 720);
        helper.assertTrue(Math.abs(next - first - Math.PI / 1440) < 0.00001,
                "Long-running worlds must preserve quarter-tick angular motion");
        helper.succeed();
    }
}
