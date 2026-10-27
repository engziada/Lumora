package com.firestick.aura.launcher.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;

import java.util.List;

/** Compact glass list: context menus, pickers and multi-select lists. */
public final class GlassMenu {
    public interface OnPick {
        void onPick(int index, Item item);
    }

    public static final class Item {
        public final CharSequence label;
        private final int iconRes;
        private final Drawable icon;
        private boolean danger;
        private Boolean checked;

        public Item(int iconRes, CharSequence label) {
            this(iconRes, null, label);
        }

        public Item(Drawable icon, CharSequence label) {
            this(0, icon, label);
        }

        private Item(int iconRes, Drawable icon, CharSequence label) {
            this.iconRes = iconRes;
            this.icon = icon;
            this.label = label;
        }

        public Item danger() {
            danger = true;
            return this;
        }

        public Item checked(boolean value) {
            checked = value;
            return this;
        }

        public boolean isChecked() {
            return Boolean.TRUE.equals(checked);
        }
    }

    private GlassMenu() {
    }

    /**
     * @param multiSelect keep the menu open and toggle the check mark of the chosen row
     */
    public static Dialog show(Activity activity, CharSequence title, CharSequence subtitle, Drawable headerIcon,
                              List<Item> items, boolean multiSelect, OnPick onPick) {
        Dialog dialog = Glass.overlay(activity, R.layout.dialog_glass_list, Gravity.CENTER);
        ((TextView) dialog.findViewById(R.id.menuTitle)).setText(title);
        TextView sub = dialog.findViewById(R.id.menuSubtitle);
        if (subtitle != null && subtitle.length() > 0) {
            sub.setText(subtitle);
            sub.setVisibility(View.VISIBLE);
        }
        ImageView header = dialog.findViewById(R.id.menuIcon);
        if (headerIcon != null) {
            header.setImageDrawable(headerIcon);
            header.setVisibility(View.VISIBLE);
        }

        PreferenceManager prefs = new PreferenceManager(activity);
        int accent = prefs.getAccentColor(ContextCompat.getColor(activity, R.color.nova_accent));
        int error = ContextCompat.getColor(activity, R.color.state_error);
        LinearLayout list = dialog.findViewById(R.id.menuItems);
        LayoutInflater inflater = LayoutInflater.from(activity);
        View focus = null;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int index = i;
            View row = inflater.inflate(R.layout.item_menu_row, list, false);
            ImageView icon = row.findViewById(R.id.rowIcon);
            TextView label = row.findViewById(R.id.rowLabel);
            ImageView check = row.findViewById(R.id.rowCheck);
            label.setText(item.label);
            if (item.icon != null) {
                icon.setImageDrawable(item.icon);
            } else if (item.iconRes != 0) {
                icon.setImageResource(item.iconRes);
                icon.setImageTintList(ColorStateList.valueOf(item.danger ? error : accent));
            } else {
                icon.setVisibility(View.GONE);
            }
            if (item.danger) label.setTextColor(error);
            check.setImageTintList(ColorStateList.valueOf(accent));
            check.setVisibility(item.isChecked() ? View.VISIBLE : item.checked != null ? View.INVISIBLE : View.GONE);
            row.setOnFocusChangeListener((v, focused) -> {
                if (focused) SoundManager.playFocus(v, prefs);
            });
            row.setOnClickListener(v -> {
                SoundManager.playClick(v, prefs);
                if (multiSelect) {
                    item.checked = !item.isChecked();
                    check.setVisibility(item.isChecked() ? View.VISIBLE : View.INVISIBLE);
                } else {
                    dialog.dismiss();
                }
                if (onPick != null) onPick.onPick(index, item);
            });
            list.addView(row);
            if (focus == null || (!multiSelect && item.isChecked())) focus = row;
        }
        dialog.show();
        if (focus != null) focus.requestFocus();
        return dialog;
    }
}
