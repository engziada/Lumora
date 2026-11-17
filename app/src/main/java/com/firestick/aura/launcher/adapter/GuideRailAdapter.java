package com.firestick.aura.launcher.adapter;

import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The category rail beside the Live TV guide. Resting on an entry previews it (the activity
 * applies it after a short pause); OK applies it at once; Right enters the guide.
 */
public class GuideRailAdapter extends RecyclerView.Adapter<GuideRailAdapter.Holder> {
    public static final class Entry {
        public final String key;
        public final String label;
        public final int count;

        public Entry(String key, String label, int count) {
            this.key = key;
            this.label = label;
            this.count = count;
        }
    }

    public interface Listener {
        /** Focus landed on an entry. */
        void onRest(String key);

        /** OK on an entry. */
        void onPick(String key);

        /** Right from an entry. Returns true when it moved focus. */
        boolean onRight(String key);
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Long> ids = new HashMap<>();
    private final PreferenceManager preferences;
    private final Listener listener;
    private String selected = "";

    public GuideRailAdapter(PreferenceManager preferences, Listener listener) {
        this.preferences = preferences;
        this.listener = listener;
        setHasStableIds(true);
    }

    /** Rebinds only the entries whose label or count changed, then moves the selection. */
    public void setEntries(List<Entry> next, String selectedKey) {
        List<Entry> previous = new ArrayList<>(entries);
        List<Entry> fresh = new ArrayList<>(next);
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
                return previous.get(oldPosition).key.equals(fresh.get(newPosition).key);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Entry a = previous.get(oldPosition), b = fresh.get(newPosition);
                return a.count == b.count && a.label.equals(b.label);
            }
        });
        entries.clear();
        entries.addAll(fresh);
        diff.dispatchUpdatesTo(this);
        setSelected(selectedKey);
    }

    public void setSelected(String key) {
        String next = key == null ? "" : key;
        if (next.equals(selected)) return;
        int previous = indexOf(selected);
        selected = next;
        if (previous >= 0) notifyItemChanged(previous);
        int current = indexOf(next);
        if (current >= 0) notifyItemChanged(current);
    }

    public String selected() {
        return selected;
    }

    public int indexOf(String key) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).key.equals(key)) return i;
        return -1;
    }

    @Override
    public long getItemId(int position) {
        String key = entries.get(position).key;
        Long id = ids.get(key);
        if (id == null) {
            id = (long) ids.size();
            ids.put(key, id);
        }
        return id;
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guide_category, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(entries.get(position));
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final TextView label, count;
        private Entry entry;

        Holder(View view) {
            super(view);
            label = view.findViewById(R.id.guideRailLabel);
            count = view.findViewById(R.id.guideRailCount);
            view.setOnFocusChangeListener((v, focused) -> {
                if (!focused || entry == null) return;
                SoundManager.playFocus(v, preferences);
                listener.onRest(entry.key);
            });
            view.setOnClickListener(v -> {
                if (entry == null) return;
                SoundManager.playClick(v, preferences);
                listener.onPick(entry.key);
            });
            view.setOnKeyListener((v, keyCode, event) -> keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    && event.getAction() == KeyEvent.ACTION_DOWN && entry != null && listener.onRight(entry.key));
        }

        void bind(Entry entry) {
            this.entry = entry;
            boolean isSelected = entry.key.equals(selected);
            itemView.setTag("rail:" + entry.key);
            itemView.setSelected(isSelected);
            label.setText(entry.label);
            label.setTypeface(Typeface.DEFAULT, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            count.setText(String.valueOf(entry.count));
        }
    }
}
