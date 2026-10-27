package com.firestick.aura.launcher.adapter;

import android.content.res.ColorStateList;
import android.view.KeyEvent;
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
import com.firestick.aura.launcher.manager.IconService;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.model.AppModel;

import java.util.ArrayList;
import java.util.List;

public class AppRowAdapter extends RecyclerView.Adapter<AppRowAdapter.AppViewHolder> {
    public interface OnAppActionListener {
        void onAppClick(AppModel app);

        void onAppLongClick(AppModel app, String rowId);
    }

    interface OnAppFocusedListener {
        void onAppFocused(String rowId, String packageName);
    }

    private static final class Entry {
        final AppModel app;
        final boolean favorite;
        final boolean hdIcons;
        final int accent;

        Entry(AppModel app, boolean hdIcons, int accent) {
            this.app = app;
            this.favorite = app.isFavorite();
            this.hdIcons = hdIcons;
            this.accent = accent;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final OnAppActionListener listener;
    private final OnAppFocusedListener focusListener;
    private final PreferenceManager preferences;
    private final String rowId;
    private RecyclerView recyclerView;
    private String focusedPackage;

    public AppRowAdapter(PreferenceManager preferences, OnAppActionListener listener,
                         String rowId, OnAppFocusedListener focusListener) {
        this.preferences = preferences;
        this.listener = listener;
        this.rowId = rowId;
        this.focusListener = focusListener;
        setHasStableIds(true);
    }

    public void setApps(List<AppModel> apps) {
        List<Entry> next = new ArrayList<>();
        boolean hd = preferences.isHdIconsEnabled();
        int fallback = recyclerView == null ? 0 : ContextCompat.getColor(recyclerView.getContext(), R.color.nova_accent);
        int accent = preferences.getAccentColor(fallback);
        if (apps != null) for (AppModel app : apps) next.add(new Entry(app, hd, accent));

        List<Entry> previous = new ArrayList<>(entries);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previous.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return previous.get(oldPos).app.getPackageName().equals(next.get(newPos).app.getPackageName());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                Entry old = previous.get(oldPos), fresh = next.get(newPos);
                return old.favorite == fresh.favorite && old.hdIcons == fresh.hdIcons && old.accent == fresh.accent
                        && old.app.getLabel().equals(fresh.app.getLabel());
            }
        });
        entries.clear();
        entries.addAll(next);
        result.dispatchUpdatesTo(this);
        restoreFocus();
    }

    void clearFocusedApp() {
        focusedPackage = null;
    }

    /** After a refresh, D-pad focus returns to the app that had it, not to the first tile. */
    private void restoreFocus() {
        if (recyclerView == null || focusedPackage == null) return;
        int position = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (focusedPackage.equals(entries.get(i).app.getPackageName())) {
                position = i;
                break;
            }
        }
        if (position < 0) {
            focusedPackage = null;
            return;
        }
        final int target = position;
        recyclerView.post(() -> {
            if (recyclerView == null) return;
            recyclerView.scrollToPosition(target);
            recyclerView.post(() -> {
                if (recyclerView == null) return;
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(target);
                if (holder != null && recyclerView.hasFocus()) holder.itemView.requestFocus();
            });
        });
    }

    @Override
    public long getItemId(int position) {
        return entries.get(position).app.getPackageName().hashCode();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recycler) {
        recyclerView = recycler;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recycler) {
        if (recyclerView == recycler) recyclerView = null;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AppViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(entries.get(position));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    final class AppViewHolder extends RecyclerView.ViewHolder {
        private final View iconContainer, plateView, focusRingView;
        private final ImageView icon, favoriteBadge;
        private final TextView label;
        private final int labelFocused, labelIdle;

        AppViewHolder(View view) {
            super(view);
            iconContainer = view.findViewById(R.id.iconContainer);
            plateView = view.findViewById(R.id.plateView);
            focusRingView = view.findViewById(R.id.focusRingView);
            icon = view.findViewById(R.id.imgAppIcon);
            favoriteBadge = view.findViewById(R.id.badgeFavorite);
            label = view.findViewById(R.id.txtAppLabel);
            labelFocused = ContextCompat.getColor(view.getContext(), R.color.nova_text);
            labelIdle = ContextCompat.getColor(view.getContext(), R.color.nova_text_dim);
        }

        void bind(Entry entry) {
            AppModel app = entry.app;
            String pkg = app.getPackageName();
            icon.setTag(pkg);
            icon.setImageDrawable(app.getIcon());
            label.setText(app.getLabel());
            favoriteBadge.setVisibility(entry.favorite ? View.VISIBLE : View.GONE);
            focusRingView.setBackgroundTintList(ColorStateList.valueOf(entry.accent));
            if (entry.hdIcons) {
                IconService.get(itemView.getContext()).load(pkg, app.getLabel(), bitmap -> {
                    if (bitmap != null && !bitmap.isRecycled() && pkg.equals(icon.getTag())) icon.setImageBitmap(bitmap);
                });
            }
            itemView.setOnFocusChangeListener((v, focused) -> {
                applyFocus(focused);
                if (focused) {
                    focusedPackage = pkg;
                    if (focusListener != null) focusListener.onAppFocused(rowId, pkg);
                    SoundManager.playFocus(v, preferences);
                }
            });
            itemView.setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                if (listener != null) listener.onAppClick(app);
            });
            itemView.setOnLongClickListener(v -> {
                if (listener == null) return false;
                listener.onAppLongClick(app, rowId);
                return true;
            });
            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (listener != null && event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                    listener.onAppLongClick(app, rowId);
                    return true;
                }
                return false;
            });
            applyFocus(itemView.isFocused());
        }

        private void applyFocus(boolean focused) {
            float scale = focused ? 1.07f : 1f;
            iconContainer.animate().scaleX(scale).scaleY(scale).translationZ(focused ? 12f : 0f).setDuration(140).start();
            plateView.animate().alpha(focused ? 1f : .92f).setDuration(140).start();
            focusRingView.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            label.setSelected(focused);
            label.setTextColor(focused ? labelFocused : labelIdle);
        }
    }
}
