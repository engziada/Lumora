package com.firestick.aura.launcher.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AssistantTextTest {
    @Test
    public void parseExtractsOpenDirectivesAndStripsThem() {
        AssistantText.Reply reply = AssistantText.parse("Sure, opening it.\n[[open:com.netflix.ninja]]");
        assertEquals("Sure, opening it.", reply.text);
        assertEquals(Collections.singletonList("com.netflix.ninja"), reply.packages);
    }

    @Test
    public void parseToleratesSpacingAndDeduplicates() {
        AssistantText.Reply reply = AssistantText.parse("A [[ open : com.a.b ]] B [[open:com.a.b]] C [[open:org.c.d]]");
        assertEquals(Arrays.asList("com.a.b", "org.c.d"), reply.packages);
        assertFalse(reply.text.contains("[["));
    }

    @Test
    public void parseIgnoresMalformedPackages() {
        AssistantText.Reply reply = AssistantText.parse("[[open:nodots]] [[open:../etc]] ok");
        assertTrue(reply.packages.isEmpty());
    }

    @Test
    public void parseLimitsSuggestionsToThree() {
        AssistantText.Reply reply = AssistantText.parse("[[open:a.a]][[open:b.b]][[open:c.c]][[open:d.d]]");
        assertEquals(3, reply.packages.size());
    }

    @Test
    public void parseRemovesThinkingAndMarkdown() {
        AssistantText.Reply reply = AssistantText.parse("<think>hidden plan</think>## Title\n**Bold** text\n\n\n\nEnd");
        assertEquals("Title\nBold text\n\nEnd", reply.text);
    }

    @Test
    public void parseHandlesNull() {
        AssistantText.Reply reply = AssistantText.parse(null);
        assertEquals("", reply.text);
        assertTrue(reply.packages.isEmpty());
    }

    @Test
    public void maskNeverRevealsTheKeyBody() {
        String key = "sk-or-v1-0123456789abcdef0123456789abcdef";
        String masked = AssistantText.mask(key);
        assertEquals("sk-or-\u2026cdef", masked);
        assertFalse(masked.contains("0123456789"));
        assertEquals("\u2022\u2022\u2022\u2022", AssistantText.mask("short"));
        assertEquals("", AssistantText.mask("  "));
        assertEquals("", AssistantText.mask(null));
    }

    @Test
    public void systemPromptListsAppsOnlyWhenShared() {
        String withApps = AssistantText.systemPrompt("Monday 20:00", Collections.singletonList("Netflix (com.netflix.ninja)"));
        assertTrue(withApps.contains("[[open:PACKAGE]]"));
        assertTrue(withApps.contains("Netflix (com.netflix.ninja)"));
        assertTrue(withApps.contains("Monday 20:00"));
        String withoutApps = AssistantText.systemPrompt("Monday 20:00", Collections.emptyList());
        assertFalse(withoutApps.contains("[[open:"));
    }
}
