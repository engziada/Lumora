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
import com.firestick.aura.launcher.model.IptvChannel;

import java.util.ArrayList;
import java.util.List;

/** 16:9 poster cards for the IPTV channels tab. */
public class IptvAdapter extends RecyclerView.Adapter<IptvAdapter.Holder> {
    public interface OnChannelClickListener {
        void onChannelClick(IptvChannel channel);
    }

    public interface OnFavoriteToggleListener {
        void onToggleFavorite(IptvChannel channel, boolean isFavorite);
    }

    private static final int CARD_WIDTH_DP = 162;
    private static final int CARD_HEIGHT_DP = 91;

    private final List<IptvChannel> items = new ArrayList<>();
    private final PreferenceManager preferences;
    private final OnChannelClickListener channelListener;
    private final OnFavoriteToggleListener favoriteListener;

    public IptvAdapter(PreferenceManager preferences, 
            OnChannelClickListener channelListener,
            OnFavoriteToggleListener favoriteListener) {
        this.preferences = preferences;
        this.channelListener = channelListener;
        this.favoriteListener = favoriteListener;
        setHasStableIds(true);
    }

    public void setItems(List<IptvChannel> next) {
        List<IptvChannel> previous = new ArrayList<>(items);
        List<IptvChannel> fresh = next == null ? new ArrayList<>() : new ArrayList<>(next);
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
                IptvChannel a = previous.get(oldPosition), b = fresh.get(newPosition);
                return a.logo.equals(b.logo) && a.name.equals(b.name)
                        && a.url.equals(b.url) && a.category.equals(b.category)
                        && a.genres.equals(b.genres) && a.currentProgram.equals(b.currentProgram)
                        && a.isFavorite == b.isFavorite;
            }
        });
        items.clear();
        items.addAll(fresh);
        diff.dispatchUpdatesTo(this);
    }

    public void updateFavoriteState(long channelId, boolean isFavorite) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == channelId) {
                IptvChannel updated = new IptvChannel(
                    items.get(i).id,
                    items.get(i).name,
                    items.get(i).logo,
                    items.get(i).category,
                    items.get(i).genres,
                    items.get(i).country,
                    items.get(i).language,
                    items.get(i).url,
                    isFavorite,
                    items.get(i).currentProgram,
                    items.get(i).nextProgram,
                    items.get(i).currentTime,
                    items.get(i).nextTime
                );
                items.set(i, updated);
                notifyItemChanged(i);
                break;
            }
        }
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
        private final View favoriteBadge;

        Holder(View view) {
            super(view);
            frame = view.findViewById(R.id.posterFrame);
            focusRing = view.findViewById(R.id.posterFocus);
            poster = view.findViewById(R.id.imgPoster);
            title = view.findViewById(R.id.txtPosterTitle);
            meta = view.findViewById(R.id.txtPosterMeta);
            favoriteBadge = view.findViewById(R.id.favoriteBadge);
            poster.setClipToOutline(true);
        }

        void bind(IptvChannel channel) {
            title.setText(channel.name);
            
            StringBuilder metaText = new StringBuilder();
            if (!channel.category.isEmpty()) {
                metaText.append(channel.category);
            }
            if (!channel.currentTime.isEmpty()) {
                if (metaText.length() > 0) metaText.append(" • ");
                metaText.append(channel.currentTime);
            }
            meta.setText(metaText.toString());

            // Set logo as poster (or placeholder)
            String url = channel.logo.isEmpty() ? "" : channel.logo;
            poster.setTag(url);
            poster.setImageDrawable(null);
            int width = Math.round(CARD_WIDTH_DP * itemView.getResources().getDisplayMetrics().density);
            int height = Math.round(CARD_HEIGHT_DP * itemView.getResources().getDisplayMetrics().density);
            
            if (!url.isEmpty()) {
                RemoteImageLoader.get(itemView.getContext()).load(url, width, bitmap -> {
                    if (url.equals(poster.getTag())) poster.setImageBitmap(bitmap);
                });
            } else {
                // Use default poster with channel initial
                poster.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.nova_card));
            }

            int accent = preferences.getAccentColor(ContextCompat.getColor(itemView.getContext(), R.color.nova_accent));
            focusRing.setBackgroundTintList(ColorStateList.valueOf(accent));
            
            // Show favorite badge if applicable
            poster.clearColorFilter();
            if (channel.isFavorite) {
                if (favoriteBadge != null) {
                    favoriteBadge.setVisibility(View.VISIBLE);
                }
            } else {
                if (favoriteBadge != null) {
                    favoriteBadge.setVisibility(View.GONE);
                }
            }

            itemView.setOnFocusChangeListener((v, focused) -> {
                focusRing.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
                frame.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(140).start();
                title.setSelected(focused);
                if (focused) SoundManager.playFocus(v, preferences);
            });
            
            itemView.setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                if (channelListener != null) channelListener.onChannelClick(channel);
            });
            
            itemView.setOnLongClickListener(v -> {
                SoundManager.playClick(v, preferences);
                if (favoriteListener != null) {
                    favoriteListener.onToggleFavorite(channel, !channel.isFavorite);
                    return true;
                }
                return false;
            });
            itemView.setContentDescription(itemView.getContext().getString(
                    channel.isFavorite ? R.string.iptv_remove_favorite : R.string.iptv_add_favorite,
                    channel.name));
            
            focusRing.setVisibility(itemView.isFocused() ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
