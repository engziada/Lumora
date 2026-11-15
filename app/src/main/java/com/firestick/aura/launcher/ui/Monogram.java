package com.firestick.aura.launcher.ui;

import java.util.Locale;

/** Placeholder initials for a tile without artwork: the first letter or digit of the first two words. */
public final class Monogram {
    private Monogram() {
    }

    public static String initials(String name) {
        if (name == null) return "";
        StringBuilder initials = new StringBuilder(4);
        boolean inWord = false;
        int found = 0;
        for (int i = 0; i < name.length() && found < 2; ) {
            int point = name.codePointAt(i);
            i += Character.charCount(point);
            if (Character.isLetterOrDigit(point)) {
                if (!inWord) {
                    initials.appendCodePoint(point);
                    found++;
                }
                inWord = true;
            } else if (!isWordInternal(point)) {
                inWord = false;
            }
        }
        return initials.toString().toUpperCase(Locale.ROOT);
    }

    /** Apostrophes and combining marks (accents, Arabic vowel marks) do not end a word. */
    private static boolean isWordInternal(int point) {
        if (point == '\'' || point == '\u2019') return true;
        int type = Character.getType(point);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
