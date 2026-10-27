package com.firestick.aura.launcher.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
        void onAppLongClick(AppModel app);
    }

    private final List<AppModel> apps = new ArrayList<>();
    private final OnAppActionListener listener;
    private final PreferenceManager preferenceManager;

    public AppRowAdapter(PreferenceManager preferenceManager, OnAppActionListener listener) {
        this.preferenceManager = preferenceManager;
        this.listener = listener;
    }

    public void setApps(List<AppModel> newApps) {
        apps.clear();
        if (newApps != null) {
            apps.addAll(newApps);
        }
        notifyDataSetChanged();
    }

    public List<AppModel> getApps() {
        return apps;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_card, parent, false);
        return new AppViewHolder(view, preferenceManager);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(apps.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        private static final int FOCUS_DURATION_MS = 140;

        private final View iconContainer;
        private final View plateView;
        private final View focusRingView;
        private final ImageView imgIcon;
        private final ImageView badgeFavorite;
        private final TextView txtLabel;
        private final PreferenceManager preferenceManager;

        AppViewHolder(@NonNull View itemView, PreferenceManager preferenceManager) {
            super(itemView);
            this.preferenceManager = preferenceManager;
            this.iconContainer = itemView.findViewById(R.id.iconContainer);
            this.plateView = itemView.findViewById(R.id.plateView);
            this.focusRingView = itemView.findViewById(R.id.focusRingView);
            this.imgIcon = itemView.findViewById(R.id.imgAppIcon);
            this.badgeFavorite = itemView.findViewById(R.id.badgeFavorite);
            this.txtLabel = itemView.findViewById(R.id.txtAppLabel);
        }

        void bind(AppModel app, OnAppActionListener listener) {
            final String packageName = app.getPackageName();
            final ImageView iconView = imgIcon;
            iconView.setTag(packageName);
            iconView.setImageDrawable(app.getIcon());
            txtLabel.setText(app.getLabel());
            badgeFavorite.setVisibility(app.isFavorite() ? View.VISIBLE : View.GONE);

            Context context = itemView.getContext();
            if (preferenceManager.isHdIconsEnabled()) {
                IconService.get(context).load(packageName, bitmap -> {
                    if (bitmap != null && !bitmap.isRecycled()
                            && packageName.equals(iconView.getTag())) {
                        iconView.setImageBitmap(bitmap);
                    }
                });
            }
            int accent = preferenceManager.getAccentColor(
                    ContextCompat.getColor(context, R.color.nova_accent));
            focusRingView.setBackgroundTintList(ColorStateList.valueOf(accent));

            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                applyFocusState(hasFocus);
                if (hasFocus) {
                    SoundManager.playFocus(v, preferenceManager);
                }
            });

            itemView.setOnClickListener(v -> {
                SoundManager.playClick(v, preferenceManager);
                if (listener != null) {
                    listener.onAppClick(app);
                }
            });

            itemView.setOnLongClickListener(v -> {
                SoundManager.playClick(v, preferenceManager);
                if (listener != null) {
                    listener.onAppLongClick(app);
                    return true;
                }
                return false;
            });

            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                    SoundManager.playClick(v, preferenceManager);
                    if (listener != null) {
                        listener.onAppLongClick(app);
                        return true;
                    }
                }
                return false;
            });

            applyFocusState(itemView.isFocused());
        }

        private void applyFocusState(boolean focused) {
            float scale = focused ? 1.07f : 1.0f;

            iconContainer.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationZ(focused ? 12f : 0f)
                    .setDuration(FOCUS_DURATION_MS)
                    .start();

            plateView.animate()
                    .alpha(focused ? 1f : 0.92f)
                    .setDuration(FOCUS_DURATION_MS)
                    .start();

            focusRingView.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            focusRingView.animate()
                    .alpha(focused ? 1f : 0f)
                    .setDuration(FOCUS_DURATION_MS)
                    .start();

            txtLabel.setSelected(focused);
            txtLabel.setTextColor(focused ? 0xFFFFFFFF : 0xFFD6DEEC);
        }
    }
}
