package com.firestick.aura.launcher.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain-Java text helpers for the assistant: prompt, reply clean-up and key masking. */
public final class AssistantText {
    private static final Pattern OPEN = Pattern.compile("\\[\\[\\s*open\\s*:\\s*([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)\\s*]]");
    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final int MAX_PACKAGES = 3;

    private AssistantText() {
    }

    /** Assistant reply with app-open directives removed and listed separately. */
    public static final class Reply {
        public final String text;
        public final List<String> packages;

        Reply(String text, List<String> packages) {
            this.text = text;
            this.packages = Collections.unmodifiableList(packages);
        }
    }

    public static Reply parse(String raw) {
        if (raw == null) return new Reply("", new ArrayList<>());
        String withoutThinking = THINK.matcher(raw).replaceAll("");
        Set<String> packages = new LinkedHashSet<>();
        Matcher matcher = OPEN.matcher(withoutThinking);
        while (matcher.find() && packages.size() < MAX_PACKAGES) packages.add(matcher.group(1));
        String text = clean(OPEN.matcher(withoutThinking).replaceAll(""));
        return new Reply(text, new ArrayList<>(packages));
    }

    /** TV text views do not render Markdown; drop the most common markers. */
    static String clean(String text) {
        String result = text.replace("**", "").replace("__", "");
        result = HEADING.matcher(result).replaceAll("");
        result = BLANK_LINES.matcher(result).replaceAll("\n\n");
        return result.trim();
    }

    /** {@code sk-or-…1a2b}: enough to recognise a key without showing it. */
    public static String mask(String key) {
        if (key == null) return "";
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.length() <= 10) return "\u2022\u2022\u2022\u2022";
        String prefix = trimmed.startsWith("sk-or-") ? "sk-or-" : trimmed.substring(0, 3);
        return prefix + "\u2026" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * @param now  local date and time, already formatted
     * @param apps installed apps as {@code "Label (package)"}; empty when sharing is off
     */
    public static String systemPrompt(String now, List<String> apps) {
        StringBuilder prompt = new StringBuilder()
                .append("You are Lumora Assistant, built into the Lumora launcher on an Amazon Fire TV. ")
                .append("Answer in the language the user writes in (Arabic or English). ")
                .append("Replies are read on a TV from a sofa: keep them short (at most about 120 words), ")
                .append("plain text, no tables, no Markdown. ")
                .append("You cannot change device settings or play media yourself. ")
                .append("Current local time: ").append(now).append('.');
        if (apps != null && !apps.isEmpty()) {
            prompt.append("\nWhen the user wants to open or watch something in one of the installed apps below, ")
                    .append("add a separate line [[open:PACKAGE]] with the exact package name from the list. ")
                    .append("Never invent package names. Installed apps: ")
                    .append(String.join("; ", apps)).append('.');
        }
        return prompt.toString();
    }
}
