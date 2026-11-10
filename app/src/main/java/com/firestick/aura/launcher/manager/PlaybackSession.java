package com.firestick.aura.launcher.manager;

import android.app.Activity;
import android.content.Intent;
import com.firestick.aura.launcher.IptvPlayerActivity;
import com.firestick.aura.launcher.model.IptvChannel;
import org.json.JSONArray;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Private file transport avoids Android's binder limit for channel queues. */
public final class PlaybackSession {
    private static List<IptvChannel> activeQueue;

    private PlaybackSession() { }

    public static void open(Activity activity, List<IptvChannel> channels, IptvChannel selected) {
        activeQueue = new ArrayList<>(channels);
        if (activeQueue.stream().noneMatch(channel -> channel.id == selected.id)) activeQueue = new ArrayList<>(java.util.Collections.singletonList(selected));
        persistWindow(activity, activeQueue, selected);
        activity.startActivity(new Intent(activity, IptvPlayerActivity.class)
                .putExtra("channel_id", selected.id));
    }

    public static void persistWindow(Activity activity, List<IptvChannel> channels, IptvChannel selected) {
        JSONArray queue = new JSONArray();
        int position = 0;
        for (int i = 0; i < channels.size(); i++) if (channels.get(i).id == selected.id) { position = i; break; }
        int start = Math.max(0, position - 100);
        int end = Math.min(channels.size(), start + 200);
        for (int i = start; i < end; i++) {
            queue.put(channels.get(i).toJson());
        }
        boolean present = false;
        for (int i = start; i < end; i++) if (channels.get(i).id == selected.id) present = true;
        if (!present) queue = new JSONArray().put(selected.toJson());
        File file = new File(activity.getCacheDir(), "iptv-playback.json");
        if (!Http.writeFile(file, queue.toString().getBytes(StandardCharsets.UTF_8))) return;

    }

    public static List<IptvChannel> read(Activity activity) {
        if (activeQueue != null) return new ArrayList<>(activeQueue);
        List<IptvChannel> channels = new ArrayList<>();
        byte[] data = Http.readFile(new File(activity.getCacheDir(), "iptv-playback.json"));
        if (data == null || data.length > 2 * 1024 * 1024) return channels;
        try {
            JSONArray rows = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < rows.length() && i < 200; i++)
                channels.add(IptvChannel.fromJson(rows.getJSONObject(i)));
        } catch (org.json.JSONException ignored) { channels.clear(); }
        return channels;
    }
}
