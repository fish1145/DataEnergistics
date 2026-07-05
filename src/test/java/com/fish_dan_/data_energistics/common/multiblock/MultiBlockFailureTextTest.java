package com.fish_dan_.data_energistics.common.multiblock;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public final class MultiBlockFailureTextTest {

    @Test
    void mapsMdlibBlockPredicateDiagnosticToTranslationKey() {
        Component component = MultiBlockFailureText.describe("Block predicate did not match");

        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals("text.data_energistics.multiblock.failure.block_predicate", contents.getKey());
    }

    @Test
    void keepsUnknownDiagnosticsLiteralForDebugging() {
        Component component = MultiBlockFailureText.describe("custom low level failure");

        assertEquals("custom low level failure", component.getString());
    }
}
