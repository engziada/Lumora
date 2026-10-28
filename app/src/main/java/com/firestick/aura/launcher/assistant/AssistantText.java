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
    /** {@code [[do:name]]}, {@code [[do:name|arg]]} or {@code [[do:name|arg|query]]}. */
    private static final Pattern DO = Pattern.compile(
            "\\[\\[\\s*do\\s*:\\s*([A-Za-z_]+)\\s*(?:\\|([^|\\]]*))?(?:\\|([^\\]]*))?]]");
    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final int MAX_PACKAGES = 3, MAX_ACTIONS = 4;

    private AssistantText() {
    }

    /** Assistant reply with app-open and action directives removed and listed separately. */
    public static final class Reply {
        public final String text;
        public final List<String> packages;
        public final List<TvAction> actions;

        Reply(String text, List<String> packages, List<TvAction> actions) {
            this.text = text;
            this.packages = Collections.unmodifiableList(packages);
            this.actions = Collections.unmodifiableList(actions);
        }
    }

    public static Reply parse(String raw) {
        if (raw == null) return new Reply("", new ArrayList<>(), new ArrayList<>());
        String withoutThinking = THINK.matcher(raw).replaceAll("");
        Set<String> packages = new LinkedHashSet<>();
        Matcher open = OPEN.matcher(withoutThinking);
        while (open.find() && packages.size() < MAX_PACKAGES) packages.add(open.group(1));
        List<TvAction> actions = new ArrayList<>();
        Matcher action = DO.matcher(withoutThinking);
        while (action.find() && actions.size() < MAX_ACTIONS) {
            TvAction parsed = TvAction.of(action.group(1), action.group(2), action.group(3));
            if (parsed != null && !actions.contains(parsed)) actions.add(parsed);
        }
        String stripped = DO.matcher(OPEN.matcher(withoutThinking).replaceAll("")).replaceAll("");
        return new Reply(clean(stripped), new ArrayList<>(packages), actions);
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

    public static String systemPrompt(String now, List<String> apps) {
        return systemPrompt(now, apps, false);
    }

    /**
     * @param now     local date and time, already formatted
     * @param apps    installed apps as {@code "Label (package)"}; empty when sharing is off
     * @param control whether the user allows the assistant to control the TV
     */
    public static String systemPrompt(String now, List<String> apps, boolean control) {
        StringBuilder prompt = new StringBuilder()
                .append("You are Lumora Assistant, built into the Lumora launcher on an Amazon Fire TV. ")
                .append("Answer in the language the user writes in (Arabic or English). ")
                .append("Replies are read on a TV from a sofa: keep them short (at most about 120 words), ")
                .append("plain text, no tables, no Markdown. ");
        if (control) {
            prompt.append("You can control the TV. When the user asks for an action, reply with one short ")
                    .append("confirmation sentence and add one line per action, using only these directives: ")
                    .append("[[do:play]] [[do:pause]] [[do:play_pause]] [[do:next]] [[do:previous]] [[do:stop]] ")
                    .append("[[do:forward]] [[do:rewind]] (the app that is playing), ")
                    .append("[[do:volume_up|N]] [[do:volume_down|N]] (N = 1 to 10 steps) [[do:mute]] [[do:unmute]], ")
                    .append("[[do:back]] [[do:home]] [[do:screenshot]] [[do:power_menu]], ")
                    .append("[[do:settings|PAGE]] (PAGE: main, wifi, bluetooth, display, sound, apps, about), ")
                    .append("[[do:theme|NAME]] (Lumora theme: amber, copper, rose, moonlight), ")
                    .append("[[do:search|PACKAGE|QUERY]] (search inside an installed app). ")
                    .append("Only add directives the user asked for. You cannot power off, sleep, change the ")
                    .append("TV input or tap inside other apps; say so if asked. ");
        } else {
            prompt.append("You cannot change device settings or play media yourself. ");
        }
        prompt.append("Current local time: ").append(now).append('.');
        if (apps != null && !apps.isEmpty() && control) {
            prompt.append("\nWhen the user asks to open one of the installed apps below, add a separate line ")
                    .append("[[open:PACKAGE]] with the exact package name from the list; it opens at once. ")
                    .append("To only suggest an app, name it in the text without the directive. ")
                    .append("Never invent package names. Installed apps: ")
                    .append(String.join("; ", apps)).append('.');
        } else if (apps != null && !apps.isEmpty()) {
            prompt.append("\nWhen the user wants to open or watch something in one of the installed apps below, ")
                    .append("add a separate line [[open:PACKAGE]] with the exact package name from the list. ")
                    .append("Never invent package names. Installed apps: ")
                    .append(String.join("; ", apps)).append('.');
        }
        return prompt.toString();
    }
}
