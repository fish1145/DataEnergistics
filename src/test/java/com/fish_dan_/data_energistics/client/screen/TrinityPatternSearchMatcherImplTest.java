package com.fish_dan_.data_energistics.client.screen;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityPatternSearchMatcherImplTest {

    private final TrinityPatternSearchMatcher matcher = new TrinityPatternSearchMatcherImpl();

    @Test
    void createsInputSearchTextFromEveryInputName() {
        assertEquals(
                "iron ingot\ncertus quartz",
                this.matcher.createSearchText(
                        List.of("Iron Ingot", "CERTUS QUARTZ"),
                        List.of("Logic Processor"),
                        TrinityPatternSearchMode.INPUT));
    }

    @Test
    void createsOutputSearchTextFromEveryOutputName() {
        assertEquals(
                "logic processor\ncalculation processor",
                this.matcher.createSearchText(
                        List.of("Printed Logic Circuit"),
                        List.of("Logic Processor", "CALCULATION PROCESSOR"),
                        TrinityPatternSearchMode.OUTPUT));
    }

    @Test
    void createsCombinedSearchTextWithInputsBeforeOutputs() {
        assertEquals(
                "redstone\nsilicon\nlogic processor\nengineering processor",
                this.matcher.createSearchText(
                        List.of("Redstone", "Silicon"),
                        List.of("Logic Processor", "Engineering Processor"),
                        TrinityPatternSearchMode.INPUT_OUTPUT));
    }

    @Test
    void preservesCandidateOrderAndMultiplicity() {
        assertEquals(
                "first\nfirst\nsecond\nsecond",
                this.matcher.createSearchText(
                        List.of("First", "First"),
                        List.of("Second", "Second"),
                        TrinityPatternSearchMode.INPUT_OUTPUT));
    }

    @Test
    void cyclesThroughEverySearchMode() {
        assertAll(
                () -> assertEquals(TrinityPatternSearchMode.OUTPUT, TrinityPatternSearchMode.INPUT.next()),
                () -> assertEquals(
                        TrinityPatternSearchMode.INPUT_OUTPUT,
                        TrinityPatternSearchMode.OUTPUT.next()),
                () -> assertEquals(
                        TrinityPatternSearchMode.INPUT,
                        TrinityPatternSearchMode.INPUT_OUTPUT.next()));
    }

    @Test
    void normalizesWithRootLocaleInsteadOfDefaultLocale() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(
                    "i",
                    this.matcher.createSearchText(
                            List.of("I"),
                            List.of(),
                            TrinityPatternSearchMode.INPUT));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void rejectsNullCollectionsAndMode() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> this.matcher.createSearchText(
                                null,
                                List.of(),
                                TrinityPatternSearchMode.INPUT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> this.matcher.createSearchText(
                                List.of(),
                                null,
                                TrinityPatternSearchMode.INPUT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> this.matcher.createSearchText(List.of(), List.of(), null)));
    }

    @Test
    void rejectsNullElementsInEitherCollection() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> this.matcher.createSearchText(
                                Arrays.asList("Iron Ingot", null),
                                List.of(),
                                TrinityPatternSearchMode.INPUT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> this.matcher.createSearchText(
                                List.of(),
                                Arrays.asList("Logic Processor", null),
                                TrinityPatternSearchMode.INPUT)));
    }

    @Test
    void matchesPartialQueryTokensInCandidateOrder() {
        assertTrue(this.matcher.matchesSearchText("Iron Ingot", "iro ing"));
        assertTrue(this.matcher.matchesSearchText("Iron Plate Ingot", "iro ing"));
    }

    @Test
    void ignoresRepeatedSpacesAndSurroundingWhitespace() {
        assertTrue(this.matcher.matchesSearchText(
                "  Iron   Ingot  \nGold Ingot",
                "  iro   ing  "));
    }

    @Test
    void rejectsQueryTokensInTheWrongOrder() {
        assertFalse(this.matcher.matchesSearchText("Iron Ingot", "ing iro"));
    }

    @Test
    void doesNotMatchAcrossCandidateNameBoundaries() {
        assertFalse(this.matcher.matchesSearchText("Iron\nIngot", "iro ing"));
    }

    @Test
    void acceptsBlankQueries() {
        assertAll(
                () -> assertTrue(this.matcher.matchesSearchText("", "")),
                () -> assertTrue(this.matcher.matchesSearchText("Iron Ingot", "   ")));
    }

    @Test
    void tokenMatchingUsesRootLocaleInsteadOfDefaultLocale() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertAll(
                    () -> assertTrue(this.matcher.matchesSearchText("IRIS INGOT", "iri ing")),
                    () -> assertTrue(this.matcher.matchesSearchText("iris ingot", "IRI ING")));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void tokenMatchingRejectsNullArguments() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> this.matcher.matchesSearchText(null, "")),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> this.matcher.matchesSearchText("", null)));
    }
}
