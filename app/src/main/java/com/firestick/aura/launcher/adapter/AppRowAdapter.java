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
import com.firestick.aura.launcher.ui.CardEffectView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** App tiles for the Home strip (wide cards) and the Apps/Streaming grids (square tiles). */
public class AppRowAdapter extends RecyclerView.Adapter<AppRowAdapter.AppViewHolder> {
    public static final int STYLE_TILE = 0;
    public static final int STYLE_WIDE = 1;

    public interface OnAppActionListener {
        void onAppClick(AppModel app);

        void onAppLongClick(AppModel app, String rowId);
    }

    public interface OnAppFocusedListener {
        void onAppFocused(String rowId, AppModel app);
    }

    private static final class Entry {
        final AppModel app;
        final boolean favorite, locked, moving, hdIcons;
        final int accent;
        final String effect;

        Entry(AppModel app, boolean locked, boolean moving, boolean hdIcons, int accent, String effect) {
            this.app = app;
            this.favorite = app.isFavorite();
            this.locked = locked;
            this.moving = moving;
            this.hdIcons = hdIcons;
            this.accent = accent;
            this.effect = effect;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<AppModel> lastApps = new ArrayList<>();
    private final OnAppActionListener listener;
    private final OnAppFocusedListener focusListener;
    private final PreferenceManager preferences;
    private final String rowId;
    private final int style;
    private RecyclerView recyclerView;
    private String focusedPackage;
    private String movingPackage;

    public AppRowAdapter(PreferenceManager preferences, OnAppActionListener listener, String rowId, int style,
                         OnAppFocusedListener focusListener) {
        this.preferences = preferences;
        this.listener = listener;
        this.rowId = rowId;
        this.style = style;
        this.focusListener = focusListener;
        setHasStableIds(true);
    }

    public void setApps(List<AppModel> apps) {
        List<AppModel> source = apps == null ? new ArrayList<>() : new ArrayList<>(apps);
        lastApps.clear();
        lastApps.addAll(source);
        List<Entry> next = new ArrayList<>();
        boolean hd = preferences.isHdIconsEnabled();
        int fallback = recyclerView == null ? 0xFFE3A15A : ContextCompat.getColor(recyclerView.getContext(), R.color.nova_accent);
        int accent = preferences.getAccentColor(fallback);
        String effect = preferences.getCardEffect();
        Set<String> locked = preferences.getLockedApps();
        for (AppModel app : source) {
            String pkg = app.getPackageName();
            next.add(new Entry(app, locked.contains(pkg), pkg.equals(movingPackage), hd, accent, effect));
        }

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
                return old.favorite == fresh.favorite && old.locked == fresh.locked && old.moving == fresh.moving
                        && old.hdIcons == fresh.hdIcons && old.accent == fresh.accent
                        && old.effect.equals(fresh.effect)
                        && old.app.getLabel().equals(fresh.app.getLabel());
            }
        });
        entries.clear();
        entries.addAll(next);
        result.dispatchUpdatesTo(this);
        restoreFocus();
    }

    /** Marks one app as being moved (arrow badge, ring stays on). {@code null} ends move mode. */
    public void setMovingPackage(String packageName) {
        movingPackage = packageName;
        if (packageName != null) focusedPackage = packageName;
        setApps(new ArrayList<>(lastApps));
    }

    /** After a refresh or a move, D-pad focus returns to the app that had it. */
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
        int layout = style == STYLE_WIDE ? R.layout.item_featured_card : R.layout.item_app_card;
        return new AppViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(entries.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull AppViewHolder holder) {
        holder.effect.reset();
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    /** Reads the bound tile, so refreshes cannot leave a preview attached to a stale app. */
    public AppModel getFocusedApp() {
        if (recyclerView == null) return null;
        View child = recyclerView.getFocusedChild();
        if (child == null) return null;
        int position = recyclerView.getChildAdapterPosition(child);
        return position >= 0 && position < entries.size() ? entries.get(position).app : null;
    }

    final class AppViewHolder extends RecyclerView.ViewHolder {
        private final View iconContainer, focusRingView;
        private final CardEffectView effect;
        private final ImageView icon, favoriteBadge, lockBadge, moveBadge;
        private final TextView label;
        private final int labelFocused, labelIdle;
        private boolean moving;

        AppViewHolder(View view) {
            super(view);
            iconContainer = view.findViewById(R.id.iconContainer);
            focusRingView = view.findViewById(R.id.focusRingView);
            effect = view.findViewById(R.id.cardEffect);
            icon = view.findViewById(R.id.imgAppIcon);
            favoriteBadge = view.findViewById(R.id.badgeFavorite);
            lockBadge = view.findViewById(R.id.badgeLock);
            moveBadge = view.findViewById(R.id.badgeMove);
            label = view.findViewById(R.id.txtAppLabel);
            labelFocused = ContextCompat.getColor(view.getContext(), R.color.nova_text);
            labelIdle = ContextCompat.getColor(view.getContext(), style == STYLE_WIDE ? R.color.nova_text : R.color.nova_text_dim);
        }

        void bind(Entry entry) {
            AppModel app = entry.app;
            String pkg = app.getPackageName();
            moving = entry.moving;
            icon.setTag(pkg);
            icon.setImageDrawable(app.getIcon());
            label.setText(app.getLabel());
            favoriteBadge.setVisibility(style == STYLE_TILE && entry.favorite ? View.VISIBLE : View.GONE);
            lockBadge.setVisibility(entry.locked ? View.VISIBLE : View.GONE);
            lockBadge.setImageTintList(ColorStateList.valueOf(entry.accent));
            moveBadge.setVisibility(entry.moving ? View.VISIBLE : View.GONE);
            moveBadge.setImageTintList(ColorStateList.valueOf(entry.accent));
            focusRingView.setBackgroundTintList(ColorStateList.valueOf(entry.accent));
            effect.setEffect(entry.effect, entry.accent);
            if (entry.hdIcons) {
                IconService.get(itemView.getContext()).load(pkg, app.getLabel(), bitmap -> {
                    if (bitmap != null && !bitmap.isRecycled() && pkg.equals(icon.getTag())) icon.setImageBitmap(bitmap);
                });
            }
            itemView.setOnFocusChangeListener((v, focused) -> {
                applyFocus(focused);
                if (focused) {
                    focusedPackage = pkg;
                    if (focusListener != null) focusListener.onAppFocused(rowId, app);
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
            // Menu acts on release: holding Menu opens the assistant, which consumes or cancels the release.
            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (listener == null || keyCode != KeyEvent.KEYCODE_MENU) return false;
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    listener.onAppLongClick(app, rowId);
                }
                return true;
            });
            applyFocus(itemView.isFocused());
            if (itemView.isFocused() && focusListener != null) {
                focusedPackage = pkg;
                focusListener.onAppFocused(rowId, app);
            }
        }

        /** Lift and thin glowing ring; the background effect runs only on the focused card. */
        private void applyFocus(boolean focused) {
            boolean lifted = focused || moving;
            float scale = lifted ? (style == STYLE_WIDE ? 1.06f : 1.08f) : 1f;
            iconContainer.animate().scaleX(scale).scaleY(scale).translationZ(lifted ? 12f : 0f).setDuration(140).start();
            focusRingView.setVisibility(lifted ? View.VISIBLE : View.INVISIBLE);
            label.setSelected(focused);
            label.setTextColor(lifted ? labelFocused : labelIdle);
            if (focused) effect.start();
            else effect.stop();
        }
    }
}
