package com.firestick.aura.launcher.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.RemoteImageLoader;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.model.Movie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One shelf of films: a still for each, with the title, year and rating below. */
public final class MovieAdapter extends RecyclerView.Adapter<MovieAdapter.Holder> {
    public interface Listener {
        void onFocus(Movie movie);

        void onPlay(Movie movie);

        /** The viewer held OK on a film to take it off the shelves. */
        void onRemove(Movie movie);
    }

    private static final int CARD_WIDTH_DP = 162;

    private final PreferenceManager preferences;
    private final Listener listener;
    private final List<Movie> items = new ArrayList<>();
    /** Film ID to {position, duration} in ms, for the films to resume. */
    private Map<String, long[]> progress = Collections.emptyMap();

    public MovieAdapter(PreferenceManager preferences, Listener listener) {
        this.preferences = preferences;
        this.listener = listener;
        // Stable IDs keep focus on the same film when Continue watching refreshes.
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        long hash = 0xcbf29ce484222325L;
        for (char c : items.get(position).id.toCharArray()) {
            hash ^= c;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    public void setItems(List<Movie> movies, Map<String, long[]> resume) {
        items.clear();
        items.addAll(movies);
        progress = resume == null ? Collections.emptyMap() : resume;
        notifyDataSetChanged();
    }

    public int positionOf(String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(id)) return i;
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poster_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final View frame, focusRing;
        private final ImageView poster;
        private final TextView title, meta, percent, remove;
        private final ProgressBar bar;

        Holder(View view) {
            super(view);
            frame = view.findViewById(R.id.posterFrame);
            focusRing = view.findViewById(R.id.posterFocus);
            poster = view.findViewById(R.id.imgPoster);
            title = view.findViewById(R.id.txtPosterTitle);
            meta = view.findViewById(R.id.txtPosterMeta);
            bar = view.findViewById(R.id.posterProgress);
            percent = view.findViewById(R.id.posterPercent);
            remove = view.findViewById(R.id.posterRemove);
            poster.setClipToOutline(true);
        }

        void bind(Movie movie) {
            android.content.Context context = itemView.getContext();
            title.setText(movie.title);
            long[] resume = progress.get(movie.id);
            String details;
            if (resume != null && resume[1] > 0) {
                int left = (int) Math.max(1, (resume[1] - resume[0]) / 60_000L);
                int done = (int) Math.min(100, Math.max(1, 100 * resume[0] / resume[1]));
                details = context.getString(R.string.movies_left, left);
                bar.setProgress(done);
                bar.setVisibility(View.VISIBLE);
                percent.setText(context.getString(R.string.movies_percent, done));
                percent.setVisibility(View.VISIBLE);
            } else {
                List<String> parts = new ArrayList<>(3);
                if (!movie.year.isEmpty()) parts.add(movie.year);
                if (movie.minutes > 0) parts.add(context.getString(R.string.movies_minutes, movie.minutes));
                if (!movie.rating.isEmpty()) parts.add(movie.rating);
                details = android.text.TextUtils.join(" · ", parts);
                bar.setVisibility(View.GONE);
                percent.setVisibility(View.GONE);
            }
            meta.setText(details);
            itemView.setContentDescription(details.isEmpty() ? movie.title : movie.title + ", " + details);

            String url = movie.thumbnail();
            poster.setTag(url);
            poster.setImageDrawable(null);
            int width = Math.round(CARD_WIDTH_DP * context.getResources().getDisplayMetrics().density);
            RemoteImageLoader.get(context).load(url, width, bitmap -> {
                if (url.equals(poster.getTag())) poster.setImageBitmap(bitmap);
            });

            int accent = preferences.getAccentColor(ContextCompat.getColor(context, R.color.nova_accent));
            focusRing.setBackgroundTintList(ColorStateList.valueOf(accent));
            bar.setProgressTintList(ColorStateList.valueOf(accent));
            itemView.setOnFocusChangeListener((v, focused) -> {
                focusRing.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
                remove.setVisibility(focused ? View.VISIBLE : View.GONE);
                float scale = focused ? 1.06f : 1f;
                frame.animate().scaleX(scale).scaleY(scale).setDuration(preferences.reducedMotion() ? 0 : 140).start();
                title.setSelected(focused);
                if (!focused) return;
                SoundManager.playFocus(v, preferences);
                listener.onFocus(movie);
            });
            itemView.setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                listener.onPlay(movie);
            });
            // Holding OK is the remote's long press; Menu stays free for the assistant.
            itemView.setOnLongClickListener(v -> {
                listener.onRemove(movie);
                return true;
            });
            focusRing.setVisibility(itemView.isFocused() ? View.VISIBLE : View.INVISIBLE);
            remove.setVisibility(itemView.isFocused() ? View.VISIBLE : View.GONE);
        }
    }
}
