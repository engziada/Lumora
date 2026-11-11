package com.firestick.aura.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import com.firestick.aura.launcher.manager.*;
import com.firestick.aura.launcher.model.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Device checks through the normal Home, filter, search, settings and playback entry points. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
final class LauncherEnhancementRegression {
    private final Instrumentation test;
    private Activity activity;
    private int assertions;
    private ServerSocket server;
    private volatile boolean serving;
    LauncherEnhancementRegression(Instrumentation test) { this.test = test; }

    void run() {
        Bundle result = new Bundle();
        PreferenceManager prefs = new PreferenceManager(test.getTargetContext());
        Map<String, ?> original = prefs.storage().getAll();
        SharedPreferences sourcePrefs = test.getTargetContext().getSharedPreferences("iptv_sources", Context.MODE_PRIVATE);
        Map<String, ?> originalSources = sourcePrefs.getAll();
        SharedPreferences healthPrefs = test.getTargetContext().getSharedPreferences("iptv_health", Context.MODE_PRIVATE);
        Map<String, ?> originalHealth = healthPrefs.getAll();
        int finishCode = -1;
        try {
            startServer();
            healthPrefs.edit().clear().commit();
            verifyCatalog(sourcePrefs);
            EpgRepository epg = new EpgRepository(test.getTargetContext());
            java.lang.reflect.Method read = EpgRepository.class.getDeclaredMethod("readGuide", String.class, String.class, String.class);
            read.setAccessible(true);
            String programs = (String) read.invoke(epg, "http://127.0.0.1:" + server.getLocalPort() + "/guide.xml", "Fixture.us", "fixture");
            check(programs.contains("Current fixture") && programs.contains("Upcoming fixture"),
                    "XMLTV guide resolves current and upcoming programs");
            epg.shutdown();
            AppLock.setCode(prefs, "UDLR");
            prefs.setSettingsLocked(false);
            prefs.setReducedMotion(true);
            prefs.setHomeSections(Arrays.asList("favorites", "recent", "iptv"));
            List<IptvChannel> fixture = IptvRepository.parsePlaylist(
                    "#EXTINF:-1 tvg-id=\"Fixture.us\" group-title=\"Movies\",Fixture\nhttp://127.0.0.1:" + server.getLocalPort() + "/bad.mpd\n"
                    + "#EXTINF:-1 tvg-id=\"Fixture.us\" group-title=\"Movies\",Fixture\nhttp://127.0.0.1:" + server.getLocalPort() + "/bad\n"
                    + "#EXTINF:-1 tvg-id=\"Fixture.us\" group-title=\"Movies\",Fixture\n#EXTVLCOPT:http-user-agent=Fixture-Agent\nhttp://127.0.0.1:" + server.getLocalPort() + "/valid.mp4\n"
                    + "#EXTINF:-1 tvg-id=\"Next.us\" group-title=\"Music\",Next channel\nhttp://127.0.0.1:" + server.getLocalPort() + "/valid2.mp4\n"
                    + "#EXTINF:-1 tvg-id=\"Adult.us\" group-title=\"XXX\",Adult fixture\nhttp://127.0.0.1:" + server.getLocalPort() + "/adult.mp4\n", "US");
            IptvChannel selected = fixture.stream().filter(channel -> channel.name.equals("Fixture")).findFirst().get();
            prefs.recordChannel(selected);
            prefs.recordLaunch("com.google.android.youtube.tv");
            activity = start(MainActivity.class);
            main(() -> {
                check("IPTV".contentEquals(((TextView) activity.findViewById(R.id.txtNavShows)).getText()), "IPTV navbar label");
                check(activity.findViewById(R.id.homeSections) != null, "Home sections render");
                @SuppressWarnings("unchecked") List<IptvCountry> countries = (List<IptvCountry>) get("iptvCountries");
                countries.clear();
                countries.add(new IptvCountry("ALL", "All countries", "", Collections.emptyList()));
                countries.add(new IptvCountry("CA", "Canada", "", Collections.emptyList()));
                countries.add(new IptvCountry("EG", "Egypt", "", Collections.emptyList()));
                countries.add(new IptvCountry("FR", "France", "", Collections.emptyList()));
                countries.add(new IptvCountry("US", "United States", "", Collections.emptyList()));
                set("iptvCountriesLoaded", true);
                set("selectedIptvCountry", prefs.getShowsCountry());
                set("channelsLoaded", true);
                set("iptvLoadedAt", System.currentTimeMillis());
                activity.findViewById(R.id.navShows).performClick();
                set("countryChannels", new ArrayList<>(fixture));
                set("channelsLoaded", true);
                call("applyIptvFilters");
                activity.findViewById(R.id.iptvFilter).setEnabled(true);
                check(activity.findViewById(R.id.iptvCountry).requestFocus(), "Country can focus");
            });
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            main(() -> check(activity.getCurrentFocus() == activity.findViewById(R.id.iptvFilter), "Country moves to category"));
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            main(() -> check(activity.getCurrentFocus() == activity.findViewById(R.id.iptvOnlineOnly), "Category moves to Online"));
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            main(() -> check(activity.getCurrentFocus() == activity.findViewById(R.id.iptvSearch), "Online moves to search"));
            capture("enhancement-iptv");
            main(() -> activity.findViewById(R.id.iptvCountry).performClick());
            main(() -> {
                Dialog picker = (Dialog) get("countryPicker");
                View letter = picker.findViewById(R.id.countryLetters).findViewWithTag("letter:E");
                check(letter != null, "Country picker exposes alphabetical index");
                letter.performClick();
            });
            SystemClock.sleep(300);
            main(() -> {
                Dialog picker = (Dialog) get("countryPicker");
                ListView countries = picker.findViewById(R.id.countryPickerList);
                check(countries.getSelectedItemPosition() == 2, "Letter E jumps directly to Egypt");
                check(countries.hasFocus(), "Letter selection transfers remote focus to countries");
            });
            capture("enhancement-country-index");
            main(() -> ((Dialog) get("countryPicker")).dismiss());
            main(() -> activity.findViewById(R.id.iptvFilter).performClick());
            AccessibilityNodeInfo adults = node("Adults · 1");
            check(adults != null, "Adults listed last");
            clickNode("Adults · 1");
            SystemClock.sleep(300);
            node(activity.getString(R.string.lock_title_verify));
            main(() -> check(!"category:18+".equals(get("selectedIptvCategory")), "Adults stays locked"));
            key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_RIGHT);
            main(() -> check("category:18+".equals(get("selectedIptvCategory")), "Correct existing key opens Adults"));
            main(() -> {
                set("selectedIptvCategory", "all");
                call("applyIptvFilters");
                ((CheckBox) activity.findViewById(R.id.iptvOnlineOnly)).setChecked(true);
            });
            SystemClock.sleep(2000);
            main(() -> check(((TextView) activity.findViewById(R.id.onlineProgress)).getText().toString().contains("Checked 2 / 2"),
                    "Online scan reports progress"));
            main(() -> PlaybackSession.open(activity, fixture, selected));
            activity = waitActivity(IptvPlayerActivity.class);
            awaitPlayback();
            main(() -> {
                ExoPlayer player = (ExoPlayer) get("player");
                check(player != null && player.getCurrentMediaItem().localConfiguration.uri.toString().endsWith("valid.mp4"),
                        "Player falls back from unsupported format and HTTP 404 to valid feed");
                check(new StreamHealth(activity).rank(selected.streamUrls).get(0).endsWith("valid.mp4"),
                        "Saved device health deprioritizes failed stream");
                check(player.isPlaying(), "Header-protected feed decodes and plays: state=" + player.getPlaybackState()
                        + " error=" + player.getPlayerError());
                check(prefs.recentChannels().get(0).id == selected.id, "Successful playback records history");
                activity.findViewById(R.id.playerPicture).performClick();
            });
            clickNode(activity.getString(R.string.picture_fit));
            main(() -> activity.findViewById(R.id.playerAudio).performClick());
            clickNode(activity.getString(R.string.track_auto));
            main(() -> activity.findViewById(R.id.playerSubtitles).performClick());
            clickNode(activity.getString(R.string.track_off));
            key(KeyEvent.KEYCODE_CHANNEL_UP);
            main(() -> check("Next channel".contentEquals(((TextView) activity.findViewById(R.id.iptvPlayerChannelTitle)).getText()),
                    "Channel Up advances queue"));
            key(KeyEvent.KEYCODE_CHANNEL_DOWN);
            main(() -> check("Fixture".contentEquals(((TextView) activity.findViewById(R.id.iptvPlayerChannelTitle)).getText()),
                    "Channel Down returns to previous channel"));
            main(() -> {
                androidx.media3.ui.PlayerView view = activity.findViewById(R.id.iptvPlayerView);
                view.hideController();
                view.requestFocus();
            });
            key(KeyEvent.KEYCODE_DPAD_UP);
            main(() -> check("Next channel".contentEquals(((TextView) activity.findViewById(R.id.iptvPlayerChannelTitle)).getText()),
                    "Remote Up advances channel without exiting playback: index=" + get("channelIndex") + " controls=" + get("controlsShowing")));
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            main(() -> check("Fixture".contentEquals(((TextView) activity.findViewById(R.id.iptvPlayerChannelTitle)).getText()),
                    "Remote Down returns to previous channel"));
            main(() -> ((androidx.media3.ui.PlayerView) activity.findViewById(R.id.iptvPlayerView)).showController());
            key(KeyEvent.KEYCODE_BACK);
            main(() -> check(!activity.isFinishing()
                    && !((androidx.media3.ui.PlayerView) activity.findViewById(R.id.iptvPlayerView)).isControllerFullyVisible(),
                    "Back hides controls before leaving playback: finishing=" + activity.isFinishing() + " controls=" + get("controlsShowing")));
            capture("enhancement-player");
            main(() -> activity.finish());
            activity = start(SearchActivity.class);
            main(() -> ((EditText) activity.findViewById(R.id.globalSearchField)).setText("Appearance"));
            node("Appearance and Home layout · Settings");
            capture("enhancement-search");
            main(() -> activity.finish());
            activity = start(SettingsActivity.class);
            main(() -> activity.findViewById(R.id.secAppearance).requestFocus());
            reveal(R.id.panelAppearance, R.string.home_customize);
            node(activity.getString(R.string.home_customize));
            reveal(R.id.panelAppearance, R.string.reduced_motion);
            node(activity.getString(R.string.reduced_motion));
            main(() -> activity.findViewById(R.id.secTools).requestFocus());
            reveal(R.id.panelTools, R.string.iptv_sources);
            clickNode(activity.getString(R.string.iptv_sources));
            node(activity.getString(R.string.iptv_curated_source));
            clickNode(activity.getString(R.string.iptv_source_add));
            enterPrompt("Device fixture");
            enterPrompt("http://127.0.0.1:" + server.getLocalPort() + "/playlist.m3u");
            node(activity.getString(R.string.iptv_source_saved));
            check(IptvSources.read(sourcePrefs).size() == 1, "Validated custom source persists");
            clickNode(activity.getString(R.string.close));
            reveal(R.id.panelTools, R.string.iptv_sources);
            clickNode(activity.getString(R.string.iptv_sources));
            clickNode(activity.getString(R.string.iptv_source_remove, "Device fixture"));
            check(IptvSources.read(sourcePrefs).isEmpty(), "Custom source can be removed");
            reveal(R.id.panelTools, R.string.backup_export);
            node(activity.getString(R.string.backup_export));
            reveal(R.id.panelTools, R.string.backup_restore);
            node(activity.getString(R.string.backup_restore));
            capture("enhancement-settings");
            result.putString("stream", "PASS: " + assertions + " launcher enhancement assertions.\n");

        } catch (Throwable error) {
            result.putString("stream", "FAIL after " + assertions + ": " + android.util.Log.getStackTraceString(error));
            finishCode = 0;
        } finally {
            main(() -> { if (activity != null) activity.finish(); });
            serving = false;
            try { if (server != null) server.close(); } catch (IOException ignored) { }
            restore(prefs.storage(), original);
            restore(sourcePrefs, originalSources);
            restore(healthPrefs, originalHealth);
        }
        test.finish(finishCode, result);
    }

    private void enterPrompt(String value) {
        android.accessibilityservice.AccessibilityServiceInfo service = test.getUiAutomation().getServiceInfo();
        service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        test.getUiAutomation().setServiceInfo(service);
        SystemClock.sleep(350);
        AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
        List<AccessibilityNodeInfo> fields = root.findAccessibilityNodeInfosByViewId(
                test.getTargetContext().getPackageName() + ":id/promptField");
        check(!fields.isEmpty(), "Source prompt is editable");
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        check(fields.get(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Entered source field");
        clickNode(activity.getString(R.string.prompt_save));
    }

    private void awaitPlayback() {
        for (int i = 0; i < 60; i++) {
            boolean[] playing = {false};
            main(() -> {
                ExoPlayer player = (ExoPlayer) get("player");
                playing[0] = player != null && player.isPlaying() && player.getVideoSize().width > 0;
            });
            if (playing[0]) return;
            SystemClock.sleep(250);
        }
        throw new AssertionError("Header-protected video did not start");
    }

    private void verifyCatalog(SharedPreferences sourcePrefs) throws Exception {
        File directory = new File(test.getTargetContext().getCacheDir(), "iptv");
        directory.mkdirs();
        Map<File, byte[]> originals = new HashMap<>();
        Map<String, String> fixtures = new HashMap<>();
        fixtures.put("country-all.m3u", "#EXTM3U\n#EXTINF:-1 tvg-id=\"One.us\" group-title=\"News\",One\nhttps://a.test/live\n");
        fixtures.put("free-tv.m3u", "#EXTM3U\n#EXTINF:-1 tvg-id=\"One.us\" tvg-country=\"US\" group-title=\"USA\",One\nhttps://b.test/live\n"
                + "#EXTINF:-1 tvg-id=\"Two.eg\" tvg-country=\"EG\" group-title=\"Egypt\",Two\nhttps://c.test/live\n");
        fixtures.put("streams.json", "[{\"channel\":\"One.us\",\"url\":\"https://d.test/live\",\"user_agent\":\"Fixture\"}]");
        IptvRepository repository = null;
        try {
            for (Map.Entry<String, String> fixture : fixtures.entrySet()) {
                File file = new File(directory, fixture.getKey());
                originals.put(file, file.exists() ? Http.readFile(file) : null);
                Http.writeFile(file, fixture.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            sourcePrefs.edit().clear().putBoolean("free_tv", true).commit();
            IptvSources.save(sourcePrefs, Arrays.asList(
                    new IptvSources.Source("Fixture", "http://127.0.0.1:" + server.getLocalPort() + "/playlist.m3u"),
                    new IptvSources.Source("Unavailable", "http://127.0.0.1:" + server.getLocalPort() + "/bad")));
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            final List<IptvChannel> result = new ArrayList<>();
            final boolean[] failed = {true};
            repository = new IptvRepository(test.getTargetContext());
            repository.loadChannels("ALL", (rows, error) -> { result.addAll(rows); failed[0] = error; done.countDown(); });
            check(done.await(20, java.util.concurrent.TimeUnit.SECONDS), "Combined catalog returns");
            check(!failed[0] && result.size() == 3, "Failed custom source leaves other catalogs available");
            IptvChannel one = result.stream().filter(row -> row.channelId.equals("One.us")).findFirst().get();
            check(one.streamUrls.size() == 3, "Primary, curated and API feeds merge into one channel");
            check("Fixture".equals(one.streamHeaders.get("https://d.test/live").get("User-Agent")), "API request headers retained");
            check(IptvRepository.filterCountry(result, "EG").size() == 1, "Supplemental country filtering works");
        } finally {
            if (repository != null) repository.shutdown();
            for (Map.Entry<File, byte[]> entry : originals.entrySet()) {
                if (entry.getValue() == null) entry.getKey().delete(); else Http.writeFile(entry.getKey(), entry.getValue());
            }
            sourcePrefs.edit().clear().commit();
        }
    }

    private Activity start(Class<?> type) {
        Instrumentation.ActivityMonitor monitor = test.addMonitor(type.getName(), null, false);
        test.getTargetContext().startActivity(new Intent(test.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity result = test.waitForMonitorWithTimeout(monitor, 15000);
        test.removeMonitor(monitor);
        check(result != null, type.getSimpleName() + " starts");
        SystemClock.sleep(1200);
        return result;
    }
    private Activity waitActivity(Class<?> type) {
        // PlaybackSession starts synchronously on main. ActivityMonitor with block=false observes the launch.
        Activity result = test.waitForMonitorWithTimeout(playerMonitor, 10000);
        test.removeMonitor(playerMonitor);
        check(result != null, "Embedded player starts");
        return result;
    }
    private Instrumentation.ActivityMonitor playerMonitor;
    private void main(Runnable action) {
        final Throwable[] failure = new Throwable[1];
        if (playerMonitor == null) playerMonitor = test.addMonitor(IptvPlayerActivity.class.getName(), null, false);
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private Object get(String name) {
        try { java.lang.reflect.Field field = activity.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(activity); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void set(String name, Object value) {
        try { java.lang.reflect.Field field = activity.getClass().getDeclaredField(name); field.setAccessible(true); field.set(activity, value); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void call(String name) {
        try { java.lang.reflect.Method method = activity.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void key(int code) { test.sendKeyDownUpSync(code); SystemClock.sleep(120); }
    private void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); assertions++; }
    private void clickNode(String label) {
        for (int attempt = 0; attempt < 10; attempt++) {
            SystemClock.sleep(150);
            AccessibilityNodeInfo target = node(label);
            while (target != null && !target.isClickable()) target = target.getParent();
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                check(true, "Selected " + label);
                SystemClock.sleep(250);
                return;
            }
        }
        throw new AssertionError("Cannot select " + label);
    }
    private AccessibilityNodeInfo node(String text) {
        for (int i = 0; i < 30; i++) {
            AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (text.contentEquals(node.getText() == null ? "" : node.getText())) { assertions++; return node; }
            SystemClock.sleep(150);
        }
        throw new AssertionError("Missing UI " + text);
    }
    private void reveal(int panel, int text) {
        main(() -> {
            View target = findText(activity.findViewById(panel), activity.getString(text));
            check(target != null, "Settings control exists");
            target.requestFocus();
        });
        SystemClock.sleep(300);
    }
    private View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findText(group.getChildAt(i), text);
                if (match != null) return match;
            }
        }
        return null;
    }
    private void capture(String name) throws IOException {
        Bitmap bitmap = null;
        for (int attempt = 0; attempt < 5 && bitmap == null; attempt++) {
            SystemClock.sleep(350);
            bitmap = test.getUiAutomation().takeScreenshot();
        }
        if (bitmap == null) {
            File target = new File(test.getTargetContext().getExternalFilesDir(null), name + ".png");
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                    test.getUiAutomation().executeShellCommand("screencap -p"));
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            }
            if (target.length() == 0) throw new IOException("Emulator screenshot unavailable");
            return;
        }
        try (FileOutputStream output = new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }
    private void startServer() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        serving = true;
        new Thread(() -> {
            while (serving) try (Socket socket = server.accept()) {
                socket.setSoTimeout(3000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line = reader.readLine();
                if (line == null) continue;
                String header;
                boolean authorized = false;
                while ((header = reader.readLine()) != null && !header.isEmpty()) {
                    if (header.equalsIgnoreCase("User-Agent: Fixture-Agent")) authorized = true;
                }
                OutputStream output = socket.getOutputStream();
                if (line.contains("/bad") || (line.contains(" /valid.mp4") && !authorized)) output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes());
                else if (line.contains("/playlist.m3u")) {
                    byte[] bytes = ("#EXTM3U\n#EXTINF:-1 tvg-id=\"Custom.us\" group-title=\"Music\",Custom\nhttp://127.0.0.1:"
                            + server.getLocalPort() + "/valid2.mp4\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/x-mpegURL\r\nContent-Length: " + bytes.length
                            + "\r\nConnection: close\r\n\r\n").getBytes());
                    output.write(bytes);
                }
                else if (line.contains("/guide.xml")) {
                    java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
                    java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss xx");
                    String xml = "<tv><programme channel=\"Fixture.us\" start=\"" + format.format(now.minusMinutes(10))
                            + "\" stop=\"" + format.format(now.plusMinutes(10)) + "\"><title>Current fixture</title></programme>"
                            + "<programme channel=\"Fixture.us\" start=\"" + format.format(now.plusMinutes(10))
                            + "\" stop=\"" + format.format(now.plusMinutes(30)) + "\"><title>Upcoming fixture</title></programme></tv>";
                    byte[] bytes = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\nContent-Length: " + bytes.length
                            + "\r\nConnection: close\r\n\r\n").getBytes());
                    output.write(bytes);
                } else {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = test.getContext().getAssets().open("live-fixture.mp4")) {
                        byte[] buffer = new byte[4096]; int count;
                        while ((count = input.read(buffer)) > 0) bytes.write(buffer, 0, count);
                    }
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: " + bytes.size()
                            + "\r\nConnection: close\r\n\r\n").getBytes());
                    bytes.writeTo(output);
                }
                output.flush();
            } catch (IOException ignored) { }
        }, "fixture-http").start();
    }
    @SuppressWarnings("unchecked")
    private void restore(SharedPreferences preferences, Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        }
        editor.commit();
    }
}
