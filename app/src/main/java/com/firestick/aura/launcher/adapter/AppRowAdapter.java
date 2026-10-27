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
        void onAppLongClick(AppModel app, String rowId);
    }

    private final List<AppModel> apps = new ArrayList<>();
    private final OnAppActionListener listener;
    private final PreferenceManager preferences;
    private final String rowId;

    public AppRowAdapter(PreferenceManager preferences, OnAppActionListener listener, String rowId) {
        this.preferences = preferences;
        this.listener = listener;
        this.rowId = rowId;
    }

    public void setApps(List<AppModel> newApps) {
        apps.clear();
        if (newApps != null) apps.addAll(newApps);
        notifyDataSetChanged();
    }

    public List<AppModel> getApps() { return apps; }

    @NonNull @Override public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AppViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_card, parent, false), preferences);
    }

    @Override public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(apps.get(position), listener, rowId);
    }

    @Override public int getItemCount() { return apps.size(); }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        private final View iconContainer, plateView, focusRingView;
        private final ImageView imgIcon, badgeFavorite;
        private final TextView txtLabel;
        private final PreferenceManager preferences;

        AppViewHolder(View view, PreferenceManager preferences) {
            super(view);
            this.preferences = preferences;
            iconContainer = view.findViewById(R.id.iconContainer);
            plateView = view.findViewById(R.id.plateView);
            focusRingView = view.findViewById(R.id.focusRingView);
            imgIcon = view.findViewById(R.id.imgAppIcon);
            badgeFavorite = view.findViewById(R.id.badgeFavorite);
            txtLabel = view.findViewById(R.id.txtAppLabel);
        }

        void bind(AppModel app, OnAppActionListener listener, String rowId) {
            String pkg = app.getPackageName();
            imgIcon.setTag(pkg);
            imgIcon.setImageDrawable(app.getIcon());
            txtLabel.setText(app.getLabel());
            badgeFavorite.setVisibility(app.isFavorite() ? View.VISIBLE : View.GONE);
            Context context = itemView.getContext();
            if (preferences.isHdIconsEnabled()) IconService.get(context).load(pkg, bitmap -> {
                if (bitmap != null && !bitmap.isRecycled() && pkg.equals(imgIcon.getTag())) imgIcon.setImageBitmap(bitmap);
            });
            int accent = preferences.getAccentColor(ContextCompat.getColor(context, R.color.nova_accent));
            focusRingView.setBackgroundTintList(ColorStateList.valueOf(accent));
            itemView.setOnFocusChangeListener((v, focused) -> { applyFocus(focused); if (focused) SoundManager.playFocus(v, preferences); });
            itemView.setOnClickListener(v -> { SoundManager.playClick(v, preferences); if (listener != null) listener.onAppClick(app); });
            itemView.setOnLongClickListener(v -> { if (listener == null) return false; listener.onAppLongClick(app, rowId); return true; });
            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (listener != null && event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                    listener.onAppLongClick(app, rowId); return true;
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
            txtLabel.setSelected(focused);
            txtLabel.setTextColor(focused ? 0xFFFFFFFF : 0xFFD6DEEC);
        }
    }
}
