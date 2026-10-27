package com.firestick.aura.launcher.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.RemoteImageLoader;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.model.ShowItem;

import java.util.ArrayList;
import java.util.List;

/** 2:3 poster cards for the Shows tab. */
public class ShowsAdapter extends RecyclerView.Adapter<ShowsAdapter.Holder> {
    public interface OnShowClickListener {
        void onShowClick(ShowItem show);
    }

    private static final int POSTER_WIDTH_DP = 108;

    private final List<ShowItem> items = new ArrayList<>();
    private final PreferenceManager preferences;
    private final OnShowClickListener listener;

    public ShowsAdapter(PreferenceManager preferences, OnShowClickListener listener) {
        this.preferences = preferences;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setItems(List<ShowItem> next) {
        List<ShowItem> previous = new ArrayList<>(items);
        List<ShowItem> fresh = next == null ? new ArrayList<>() : new ArrayList<>(next);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previous.size();
            }

            @Override
            public int getNewListSize() {
                return fresh.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).id == fresh.get(newPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                ShowItem a = previous.get(oldPosition), b = fresh.get(newPosition);
                return a.posterUrl.equals(b.posterUrl) && a.airtime.equals(b.airtime) && a.channel.equals(b.channel);
            }
        });
        items.clear();
        items.addAll(fresh);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
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
        private final View frame;
        private final View focusRing;
        private final ImageView poster;
        private final TextView title;
        private final TextView meta;

        Holder(View view) {
            super(view);
            frame = view.findViewById(R.id.posterFrame);
            focusRing = view.findViewById(R.id.posterFocus);
            poster = view.findViewById(R.id.imgPoster);
            title = view.findViewById(R.id.txtPosterTitle);
            meta = view.findViewById(R.id.txtPosterMeta);
            poster.setClipToOutline(true);
        }

        void bind(ShowItem show) {
            title.setText(show.title);
            if (show.channel.isEmpty()) {
                meta.setText(show.airtime);
            } else if (show.airtime.isEmpty()) {
                meta.setText(show.channel);
            } else {
                meta.setText(itemView.getContext().getString(R.string.show_channel_time, show.channel, show.airtime));
            }

            String url = show.posterUrl;
            poster.setTag(url);
            poster.setImageDrawable(null);
            int width = Math.round(POSTER_WIDTH_DP * itemView.getResources().getDisplayMetrics().density);
            RemoteImageLoader.get(itemView.getContext()).load(url, width, bitmap -> {
                if (url.equals(poster.getTag())) poster.setImageBitmap(bitmap);
            });

            int accent = preferences.getAccentColor(ContextCompat.getColor(itemView.getContext(), R.color.nova_accent));
            focusRing.setBackgroundTintList(ColorStateList.valueOf(accent));
            itemView.setOnFocusChangeListener((v, focused) -> {
                focusRing.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
                frame.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(140).start();
                title.setSelected(focused);
                if (focused) SoundManager.playFocus(v, preferences);
            });
            itemView.setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                if (listener != null) listener.onShowClick(show);
            });
            focusRing.setVisibility(itemView.isFocused() ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
