package com.firestick.aura.launcher.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MonogramTest {
    @Test public void firstLetterOrDigitOfTheFirstTwoWords() {
        assertEquals("BO", Monogram.initials("BBC One HD"));
        assertEquals("M2", Monogram.initials("MBC 2"));
        assertEquals("AJ", Monogram.initials("  al-jazeera news"));
        assertEquals("C", Monogram.initials("CNN"));
    }

    @Test public void skipsSymbolsAndKeepsMarkedWordsWhole() {
        assertEquals("MP", Monogram.initials("\uD83D\uDCFA Movies+ Plus"));
        assertEquals("L2", Monogram.initials("L'\u00C9quipe 21"));
        // A fatha after the first letter must not split the word: the initials are qaf and alef.
        assertEquals("\u0642\u0627", Monogram.initials("\u0642\u064E\u0646\u0627\u0629 \u0627\u0644\u062C\u0632\u064A\u0631\u0629"));
    }

    @Test public void emptyWhenThereIsNothingToShow() {
        assertEquals("", Monogram.initials(null));
        assertEquals("", Monogram.initials(" +++ "));
    }
}
