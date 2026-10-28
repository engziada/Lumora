package com.firestick.aura.launcher.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class TvActionTest {
    @Test
    public void simpleActionsAreWhitelisted() {
        assertEquals("pause", TvAction.of("PAUSE", null, null).name);
        assertNull(TvAction.of("reboot", null, null));
        assertNull(TvAction.of("power_off", null, null));
        assertNull(TvAction.of(null, null, null));
    }

    @Test
    public void volumeStepsAreClamped() {
        assertEquals(TvAction.DEFAULT_STEPS, TvAction.of("volume_up", null, null).steps);
        assertEquals(TvAction.DEFAULT_STEPS, TvAction.of("volume_up", "loud", null).steps);
        assertEquals(1, TvAction.of("volume_down", "0", null).steps);
        assertEquals(TvAction.MAX_STEPS, TvAction.of("volume_up", "99", null).steps);
        assertEquals(3, TvAction.of("volume_up", " 3 ", null).steps);
    }

    @Test
    public void settingsThemeAndSearchValidateArguments() {
        assertEquals("main", TvAction.of("settings", null, null).arg);
        assertEquals("wifi", TvAction.of("settings", "WiFi", null).arg);
        assertNull(TvAction.of("settings", "developer", null));
        assertEquals("rose", TvAction.of("theme", "Rose", null).arg);
        assertNull(TvAction.of("theme", "purple", null));
        TvAction search = TvAction.of("search", "com.netflix.ninja", " Dark ");
        assertEquals("com.netflix.ninja", search.arg);
        assertEquals("Dark", search.query);
        assertNull(TvAction.of("search", "../etc", "x"));
        assertNull(TvAction.of("search", "com.netflix.ninja", ""));
        char[] longQuery = new char[500];
        Arrays.fill(longQuery, 'a');
        assertEquals(TvAction.MAX_QUERY, TvAction.of("search", "a.b", new String(longQuery)).query.length());
    }

    @Test
    public void panelAndQuickActionFlags() {
        assertTrue(TvAction.simple("home").closesPanel());
        assertTrue(TvAction.simple("home").needsQuickActions());
        assertTrue(TvAction.simple("play").closesPanel());
        assertFalse(TvAction.simple("pause").closesPanel());
        assertFalse(TvAction.of("volume_up", "2", null).closesPanel());
        assertTrue(TvAction.simple("next").isMedia());
        assertTrue(TvAction.simple("mute").isVolume());
        assertFalse(TvAction.simple("pause").needsQuickActions());
    }

    @Test
    public void replyParsesActionDirectives() {
        AssistantText.Reply reply = AssistantText.parse(
                "Turning it up.\n[[do:volume_up|3]]\n[[ do : pause ]]\n[[do:search|com.google.android.youtube.tv|lofi | beats]]"
                        + "\n[[do:format_disk]] [[do:pause]]");
        assertEquals("Turning it up.", reply.text);
        assertEquals(Arrays.asList(TvAction.of("volume_up", "3", null), TvAction.simple("pause"),
                TvAction.of("search", "com.google.android.youtube.tv", "lofi | beats")), reply.actions);
    }

    @Test
    public void replyLimitsActions() {
        AssistantText.Reply reply = AssistantText.parse("[[do:play]][[do:pause]][[do:next]][[do:previous]][[do:stop]][[do:mute]]");
        assertEquals(4, reply.actions.size());
    }

    @Test
    public void promptDescribesControlOnlyWhenEnabled() {
        String on = AssistantText.systemPrompt("now", Collections.singletonList("YouTube (com.a.b)"), true);
        assertTrue(on.contains("[[do:volume_up|N]]"));
        assertTrue(on.contains("it opens at once"));
        String off = AssistantText.systemPrompt("now", Collections.emptyList(), false);
        assertFalse(off.contains("[[do:"));
        assertTrue(off.contains("cannot change device settings"));
    }
}
