package com.firestick.aura.launcher.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LocalCommandsTest {
    @Test
    public void englishCommands() {
        assertEquals(TvAction.simple("pause"), LocalCommands.match("Pause"));
        assertEquals(TvAction.simple("pause"), LocalCommands.match("please pause!"));
        assertEquals(TvAction.simple("play"), LocalCommands.match("resume"));
        assertEquals(TvAction.simple("next"), LocalCommands.match("Next episode."));
        assertEquals(TvAction.of("volume_up", null, null), LocalCommands.match("turn it up please"));
        assertEquals(TvAction.simple("mute"), LocalCommands.match("mute"));
        assertEquals(TvAction.simple("home"), LocalCommands.match("Go home"));
        assertEquals(TvAction.of("settings", "wifi", null), LocalCommands.match("Wi-Fi settings"));
    }

    @Test
    public void arabicCommandsWithSpellingVariants() {
        assertEquals(TvAction.simple("pause"), LocalCommands.match("وقف"));
        assertEquals(TvAction.simple("pause"), LocalCommands.match("أوقف"));
        assertEquals(TvAction.of("volume_up", null, null), LocalCommands.match("علّي الصوت"));
        assertEquals(TvAction.of("volume_up", null, null), LocalCommands.match("على الصوت لو سمحت"));
        assertEquals(TvAction.of("volume_down", null, null), LocalCommands.match("وطي الصوت"));
        assertEquals(TvAction.simple("next"), LocalCommands.match("الحلقة التالية"));
        assertEquals(TvAction.simple("home"), LocalCommands.match("الرئيسية"));
        assertEquals(TvAction.simple("unmute"), LocalCommands.match("شغل الصوت"));
        assertEquals(TvAction.simple("play"), LocalCommands.match("شغّل"));
    }

    @Test
    public void questionsAreNotCommands() {
        assertNull(LocalCommands.match("why did the show pause?"));
        assertNull(LocalCommands.match("what is next on Netflix"));
        assertNull(LocalCommands.match("ليه الصوت واطي"));
        assertNull(LocalCommands.match(""));
        assertNull(LocalCommands.match(null));
    }

    @Test
    public void normalizeUnifiesArabicLetters() {
        assertEquals("الحلقه التاليه", LocalCommands.normalize("الحلقة التالية"));
        assertEquals("اوقف", LocalCommands.normalize("أُوقِف"));
        assertEquals("wi fi settings", LocalCommands.normalize("  Wi-Fi   settings!! "));
    }
}
