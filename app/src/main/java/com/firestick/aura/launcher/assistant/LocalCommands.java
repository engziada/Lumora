package com.firestick.aura.launcher.assistant;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Short spoken or typed commands ("pause", "علي الصوت") that run at once without asking the model.
 * Only whole-message matches count, so questions that merely contain a command word go to the model.
 */
public final class LocalCommands {
    private static final Map<String, TvAction> PHRASES = new HashMap<>();
    private static final String[] POLITE = {"please", "lumora", "hey lumora", "\u0644\u0648 \u0633\u0645\u062d\u062a",
            "\u0645\u0646 \u0641\u0636\u0644\u0643", "\u064a\u0627 \u0644\u0648\u0645\u0648\u0631\u0627", "\u0644\u0648\u0645\u0648\u0631\u0627"};

    static {
        add(TvAction.PAUSE, "pause", "pause it", "pause the video", "stop", "stop it", "hold on",
                "وقف", "اوقف", "ايقاف", "ايقاف مؤقت", "وقف الفيديو", "استوب");
        add(TvAction.PLAY, "play", "play it", "resume", "continue", "unpause",
                "شغل", "تشغيل", "كمل", "استمر", "كمل التشغيل");
        add(TvAction.NEXT, "next", "skip", "next episode", "next track", "next song",
                "التالي", "اللي بعده", "الحلقه التاليه", "الحلقه اللي بعدها");
        add(TvAction.PREVIOUS, "previous", "previous episode", "previous track", "previous song",
                "السابق", "اللي قبله", "الحلقه السابقه", "الحلقه اللي قبلها");
        add(TvAction.FORWARD, "fast forward", "forward", "skip ahead", "قدم", "تقديم");
        add(TvAction.REWIND, "rewind", "ترجيع", "رجع شويه");
        add(TvAction.VOLUME_UP, "volume up", "louder", "turn it up", "turn up the volume", "turn the volume up",
                "increase the volume", "increase volume", "raise the volume",
                "علي الصوت", "ارفع الصوت", "زود الصوت", "صوت اعلي");
        add(TvAction.VOLUME_DOWN, "volume down", "quieter", "turn it down", "turn down the volume",
                "turn the volume down", "decrease the volume", "decrease volume", "lower the volume",
                "وطي الصوت", "اخفض الصوت", "قلل الصوت", "نزل الصوت", "وطي");
        add(TvAction.MUTE, "mute", "mute it", "mute the tv", "silence", "اكتم", "اكتم الصوت", "ميوت");
        add(TvAction.UNMUTE, "unmute", "unmute it", "sound on", "الغي الكتم", "الغ الكتم", "فك الكتم", "شغل الصوت");
        add(TvAction.BACK, "back", "go back", "ارجع", "رجوع", "الرجوع");
        add(TvAction.HOME, "home", "go home", "go to home", "home screen",
                "الرئيسيه", "الصفحه الرئيسيه", "روح للرئيسيه", "هوم");
        add(TvAction.SCREENSHOT, "screenshot", "take a screenshot", "take screenshot",
                "لقطه شاشه", "سكرين شوت", "صور الشاشه");
        add(TvAction.POWER_MENU, "power menu", "قائمه الطاقه");
        addSettings("main", "settings", "open settings", "الاعدادات", "افتح الاعدادات");
        addSettings("wifi", "wifi settings", "wi fi settings", "open wifi settings", "network settings",
                "اعدادات الواي فاي", "اعدادات الشبكه");
        addSettings("bluetooth", "bluetooth settings", "open bluetooth settings", "اعدادات البلوتوث");
        addSettings("display", "display settings", "اعدادات الشاشه");
        addSettings("sound", "sound settings", "audio settings", "اعدادات الصوت");
    }

    private LocalCommands() {
    }

    private static void add(String name, String... phrases) {
        TvAction action = TvAction.simple(name);
        for (String phrase : phrases) PHRASES.put(normalize(phrase), action);
    }

    private static void addSettings(String page, String... phrases) {
        TvAction action = TvAction.of(TvAction.SETTINGS, page, null);
        for (String phrase : phrases) PHRASES.put(normalize(phrase), action);
    }

    /** @return the action for a whole-message command, or {@code null}. */
    public static TvAction match(String text) {
        if (text == null) return null;
        String normalized = normalize(text);
        for (String polite : POLITE) {
            String word = normalize(polite);
            if (normalized.startsWith(word + " ")) normalized = normalized.substring(word.length() + 1);
            if (normalized.endsWith(" " + word)) normalized = normalized.substring(0, normalized.length() - word.length() - 1);
        }
        return PHRASES.get(normalized.trim());
    }

    /**
     * Lower case, punctuation to spaces, Arabic diacritics and tatweel removed, alef forms unified,
     * taa marbuta to haa and alef maqsura to yaa, so spelling variants of the same command match.
     */
    static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= '\u064B' && c <= '\u0652') || c == '\u0640') continue;
            if (c == '\u0623' || c == '\u0625' || c == '\u0622') c = '\u0627';
            else if (c == '\u0629') c = '\u0647';
            else if (c == '\u0649') c = '\u064A';
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                out.append(' ');
            }
        }
        return out.toString().trim();
    }
}
