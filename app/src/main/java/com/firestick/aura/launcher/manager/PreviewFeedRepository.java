package com.firestick.aura.launcher.manager;

import android.content.Context;
import com.firestick.aura.launcher.model.AppPreviewSamples.Provider;
import java.io.File;
import java.util.List;

/** Key-free recent uploads, with a one-hour cache and a bounded one-day offline fallback. */
public final class PreviewFeedRepository {
    private final File directory;
    public PreviewFeedRepository(Context context) { directory = new File(context.getCacheDir(), "preview-feeds"); }
    public List<String> load(Provider provider, String country) throws Exception {
        String channel = provider == Provider.NETFLIX ? "UCWOA1ZGywLbqmigxE4Qlvuw"
                : provider == Provider.PRIME ? "UCQJWtTnAHhEG5w4uN0udnUQ" : "UCBR8-60-B28hp2BmDPdntcQ";
        // YouTube Atom feeds are global. Country scopes the cache and player language, not ranking/availability.
        File cache = new File(directory, provider.name() + "-" + country.replaceAll("[^A-Z]", "") + ".xml");
        byte[] bytes = cache.isFile() && System.currentTimeMillis() - cache.lastModified() < 3600000L ? Http.readFile(cache) : null;
        if (bytes == null) {
            bytes = Http.get("https://www.youtube.com/feeds/videos.xml?channel_id=" + channel, 5000, 10000);
            if (bytes != null) {
                List<String> parsed = PreviewFeedParser.parse(bytes, provider != Provider.YOUTUBE);
                if (!parsed.isEmpty()) { Http.writeFile(cache, bytes); return wideOnly(parsed); }
            }
            if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < 86400000L) bytes = Http.readFile(cache);
        }
        if (bytes == null) throw new java.io.IOException("Public feed unavailable");
        return wideOnly(PreviewFeedParser.parse(bytes, provider != Provider.YOUTUBE));
    }

    private List<String> wideOnly(List<String> ids) throws Exception {
        List<String> accepted = new java.util.ArrayList<>();
        for (String id : ids) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            File shape = new File(directory, "shape-v2-" + id);
            byte[] known = shape.isFile() && System.currentTimeMillis() - shape.lastModified() < 86400000L
                    ? Http.readFile(shape) : null;
            boolean wide;
            if (known != null) wide = known.length == 1 && known[0] == 1;
            else {
                byte[] page = Http.get("https://www.youtube.com/watch?v=" + id, 4000, 6000, "Mozilla/5.0");
                wide = page != null && PreviewVideoShape.fits(new String(page, java.nio.charset.StandardCharsets.UTF_8));
                if (page != null) Http.writeFile(shape, new byte[]{(byte) (wide ? 1 : 0)});
            }
            if (wide) accepted.add(id);
        }
        return accepted;
    }
}
