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

/**
 * Large "featured" tiles shown under the greeting, mirroring the hero strip of the design:
 * a wide rounded card with the icon, the app name and a small meta line.
 */
public class FeaturedRowAdapter extends RecyclerView.Adapter<FeaturedRowAdapter.FeaturedViewHolder> {

    private final List<AppModel> apps = new ArrayList<>();
    private final List<String> metas = new ArrayList<>();
    private final AppRowAdapter.OnAppActionListener listener;
    private final PreferenceManager preferenceManager;

    public FeaturedRowAdapter(PreferenceManager preferenceManager, AppRowAdapter.OnAppActionListener listener) {
        this.preferenceManager = preferenceManager;
        this.listener = listener;
    }

    public void setItems(List<AppModel> items, List<String> itemMetas) {
        apps.clear();
        metas.clear();
        if (items != null) {
            apps.addAll(items);
        }
        if (itemMetas != null) {
            metas.addAll(itemMetas);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FeaturedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_featured_card, parent, false);
        return new FeaturedViewHolder(view, preferenceManager);
    }

    @Override
    public void onBindViewHolder(@NonNull FeaturedViewHolder holder, int position) {
        holder.bind(apps.get(position), position < metas.size() ? metas.get(position) : "", listener);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class FeaturedViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgIcon;
        private final TextView txtLabel;
        private final TextView txtMeta;
        private final PreferenceManager preferenceManager;

        FeaturedViewHolder(@NonNull View itemView, PreferenceManager preferenceManager) {
            super(itemView);
            this.preferenceManager = preferenceManager;
            this.imgIcon = itemView.findViewById(R.id.imgFeaturedIcon);
            this.txtLabel = itemView.findViewById(R.id.txtFeaturedLabel);
            this.txtMeta = itemView.findViewById(R.id.txtFeaturedMeta);
        }

        void bind(AppModel app, String meta, AppRowAdapter.OnAppActionListener listener) {
            final String pkg = app.getPackageName();
            final ImageView iconView = imgIcon;
            iconView.setTag(pkg);
            iconView.setImageDrawable(app.getIcon());
            txtLabel.setText(app.getLabel());
            txtMeta.setText(meta);
            if (preferenceManager != null && preferenceManager.isHdIconsEnabled()) {
                IconService.get(itemView.getContext()).load(pkg, bitmap -> {
                    if (bitmap != null && !bitmap.isRecycled() && pkg.equals(iconView.getTag())) {
                        iconView.setImageBitmap(bitmap);
                    }
                });
            }

            itemView.setOnClickListener(v -> {
                SoundManager.playClick(v, preferenceManager);
                if (listener != null) listener.onAppClick(app);
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onAppLongClick(app);
                    return true;
                }
                return false;
            });

            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f)
                        .setDuration(140).start();
                txtLabel.setTextColor(hasFocus ? 0xFFFFFFFF : 0xFFF3F7FF);
                if (hasFocus) SoundManager.playFocus(v, preferenceManager);
            });
        }
    }
}
