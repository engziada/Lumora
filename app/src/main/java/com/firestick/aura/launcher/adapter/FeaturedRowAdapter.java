package com.firestick.aura.launcher.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.IconService;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.model.AppModel;

import java.util.ArrayList;
import java.util.List;

public class FeaturedRowAdapter extends RecyclerView.Adapter<FeaturedRowAdapter.Holder> {
    private final List<AppModel> apps = new ArrayList<>();
    private final List<String> metas = new ArrayList<>();
    private final AppRowAdapter.OnAppActionListener listener;
    private final PreferenceManager preferences;

    public FeaturedRowAdapter(PreferenceManager preferences, AppRowAdapter.OnAppActionListener listener) {
        this.preferences = preferences;
        this.listener = listener;
    }

    public void setItems(List<AppModel> items, List<String> itemMetas) {
        apps.clear(); metas.clear();
        if (items != null) apps.addAll(items);
        if (itemMetas != null) metas.addAll(itemMetas);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_featured_card, parent, false), preferences);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) { holder.bind(apps.get(position), position < metas.size() ? metas.get(position) : "", listener); }
    @Override public int getItemCount() { return apps.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView label, meta;
        private final PreferenceManager preferences;
        Holder(View view, PreferenceManager preferences) {
            super(view); this.preferences = preferences;
            icon = view.findViewById(R.id.imgFeaturedIcon); label = view.findViewById(R.id.txtFeaturedLabel); meta = view.findViewById(R.id.txtFeaturedMeta);
        }
        void bind(AppModel app, String metadata, AppRowAdapter.OnAppActionListener listener) {
            String pkg = app.getPackageName();
            icon.setTag(pkg); icon.setImageDrawable(app.getIcon()); label.setText(app.getLabel()); meta.setText(metadata);
            if (preferences.isHdIconsEnabled()) IconService.get(itemView.getContext()).load(pkg, bitmap -> {
                if (bitmap != null && !bitmap.isRecycled() && pkg.equals(icon.getTag())) icon.setImageBitmap(bitmap);
            });
            itemView.setOnClickListener(v -> { SoundManager.playClick(v, preferences); if (listener != null) listener.onAppClick(app); });
            itemView.setOnLongClickListener(v -> { if (listener == null) return false; listener.onAppLongClick(app, null); return true; });
            itemView.setOnFocusChangeListener((v, focused) -> {
                v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(140).start();
                if (focused) SoundManager.playFocus(v, preferences);
            });
        }
    }
}
