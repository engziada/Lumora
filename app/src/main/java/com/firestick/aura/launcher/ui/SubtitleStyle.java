package com.firestick.aura.launcher.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * How subtitles look in the IPTV and movie players. Until the viewer changes a setting, the
 * players follow the Fire TV caption settings; Reset returns to them.
 */
@SuppressLint("UnsafeOptInUsageError")
public final class SubtitleStyle {
    public static final String SIZE = "pref_sub_size", COLOR = "pref_sub_color", BACKGROUND = "pref_sub_bg",
            EDGE = "pref_sub_edge", POSITION = "pref_sub_position", EMBEDDED = "pref_sub_embedded";
    private static final String[] INT_KEYS = {SIZE, COLOR, BACKGROUND, EDGE, POSITION};

    private static final float[] SIZES = {0.8f, 1f, 1.3f, 1.6f};
    private static final int[] SIZE_NAMES = {R.string.sub_size_small, R.string.sub_size_medium,
            R.string.sub_size_large, R.string.sub_size_huge};
    private static final int[] COLORS = {Color.WHITE, 0xFFFFE14D, 0xFF6EE7F2, 0xFF8CF29A};
    private static final int[] COLOR_NAMES = {R.string.sub_color_white, R.string.sub_color_yellow,
            R.string.sub_color_cyan, R.string.sub_color_green};
    private static final int[] BACKGROUNDS = {Color.TRANSPARENT, 0x99000000, Color.BLACK};
    private static final int[] BACKGROUND_NAMES = {R.string.sub_bg_none, R.string.sub_bg_translucent,
            R.string.sub_bg_solid};
    private static final int[] EDGES = {CaptionStyleCompat.EDGE_TYPE_NONE, CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW};
    private static final int[] EDGE_NAMES = {R.string.sub_edge_none, R.string.sub_edge_outline,
            R.string.sub_edge_shadow};
    private static final float[] POSITIONS = {SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION, 0.2f};
    private static final int[] POSITION_NAMES = {R.string.sub_position_bottom, R.string.sub_position_raised};

    /** Menu rows, in order. */
    private static final int ROW_SIZE = 0, ROW_COLOR = 1, ROW_BACKGROUND = 2, ROW_EDGE = 3, ROW_POSITION = 4,
            ROW_EMBEDDED = 5, ROW_RESET = 6;

    private SubtitleStyle() {
    }

    /** True once the viewer has changed any setting; until then the Fire TV caption settings apply. */
    public static boolean isCustom(SharedPreferences store) {
        for (String key : INT_KEYS) if (store.contains(key)) return true;
        return store.contains(EMBEDDED);
    }

    public static void apply(PlayerView player, PreferenceManager preferences) {
        SubtitleView view = player.getSubtitleView();
        if (view == null) return;
        SharedPreferences store = preferences.storage();
        if (!isCustom(store)) {
            view.setUserDefaultStyle();
            view.setUserDefaultTextSize();
            view.setApplyEmbeddedStyles(true);
            view.setApplyEmbeddedFontSizes(true);
            view.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION);
            return;
        }
        boolean embedded = store.getBoolean(EMBEDDED, false);
        view.setStyle(new CaptionStyleCompat(COLORS[index(store, COLOR, 0, COLORS.length)],
                BACKGROUNDS[index(store, BACKGROUND, 1, BACKGROUNDS.length)], Color.TRANSPARENT,
                EDGES[index(store, EDGE, 0, EDGES.length)], Color.BLACK, null));
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * SIZES[index(store, SIZE, 1, SIZES.length)]);
        // The channel's own colours and sizes would hide the viewer's choice, so they apply only on request.
        view.setApplyEmbeddedStyles(embedded);
        view.setApplyEmbeddedFontSizes(embedded);
        view.setBottomPaddingFraction(POSITIONS[index(store, POSITION, 0, POSITIONS.length)]);
    }

    /**
     * Opens the style menu. OK on a row moves that setting to its next value and the menu stays
     * open, so a player behind it shows each change at once.
     */
    public static void show(Activity activity, PreferenceManager preferences, @Nullable PlayerView preview) {
        SharedPreferences store = preferences.storage();
        List<GlassMenu.Item> items = new ArrayList<>();
        for (int row = ROW_SIZE; row <= ROW_POSITION; row++) {
            items.add(new GlassMenu.Item(R.drawable.ic_tv, label(activity, store, row)));
        }
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.sub_embedded))
                .checked(isCustom(store) && store.getBoolean(EMBEDDED, false)));
        items.add(new GlassMenu.Item(R.drawable.ic_settings, activity.getString(R.string.sub_reset)));
        Dialog[] dialog = new Dialog[1];
        dialog[0] = GlassMenu.show(activity, activity.getString(R.string.sub_style_title), hint(activity, store), null,
                items, true, (index, item) -> {
                    SharedPreferences.Editor edit = store.edit();
                    switch (index) {
                        case ROW_SIZE: edit.putInt(SIZE, next(store, SIZE, 1, SIZES.length)); break;
                        case ROW_COLOR: edit.putInt(COLOR, next(store, COLOR, 0, COLORS.length)); break;
                        case ROW_BACKGROUND: edit.putInt(BACKGROUND, next(store, BACKGROUND, 1, BACKGROUNDS.length)); break;
                        case ROW_EDGE: edit.putInt(EDGE, next(store, EDGE, 0, EDGES.length)); break;
                        case ROW_POSITION: edit.putInt(POSITION, next(store, POSITION, 0, POSITIONS.length)); break;
                        case ROW_EMBEDDED: edit.putBoolean(EMBEDDED, item.isChecked()); break;
                        default:
                            for (String key : INT_KEYS) edit.remove(key);
                            edit.remove(EMBEDDED);
                    }
                    edit.apply();
                    refresh(activity, store, dialog[0], items);
                    if (preview != null) apply(preview, preferences);
                });
    }

    private static void refresh(Activity activity, SharedPreferences store, Dialog dialog, List<GlassMenu.Item> items) {
        LinearLayout rows = dialog.findViewById(R.id.menuItems);
        if (rows == null) return;
        for (int row = ROW_SIZE; row <= ROW_POSITION && row < rows.getChildCount(); row++) {
            ((TextView) rows.getChildAt(row).findViewById(R.id.rowLabel)).setText(label(activity, store, row));
        }
        boolean embedded = isCustom(store) && store.getBoolean(EMBEDDED, false);
        items.get(ROW_EMBEDDED).checked(embedded);
        if (ROW_EMBEDDED < rows.getChildCount()) {
            rows.getChildAt(ROW_EMBEDDED).findViewById(R.id.rowCheck).setVisibility(embedded ? View.VISIBLE : View.INVISIBLE);
        }
        TextView subtitle = dialog.findViewById(R.id.menuSubtitle);
        if (subtitle != null) subtitle.setText(hint(activity, store));
    }

    private static String hint(Activity activity, SharedPreferences store) {
        return activity.getString(isCustom(store) ? R.string.sub_style_hint : R.string.sub_style_hint_system);
    }

    private static String label(Activity activity, SharedPreferences store, int row) {
        int name, value;
        switch (row) {
            case ROW_SIZE: name = R.string.sub_size; value = SIZE_NAMES[index(store, SIZE, 1, SIZES.length)]; break;
            case ROW_COLOR: name = R.string.sub_color; value = COLOR_NAMES[index(store, COLOR, 0, COLORS.length)]; break;
            case ROW_BACKGROUND:
                name = R.string.sub_background;
                value = BACKGROUND_NAMES[index(store, BACKGROUND, 1, BACKGROUNDS.length)];
                break;
            case ROW_EDGE: name = R.string.sub_edge; value = EDGE_NAMES[index(store, EDGE, 0, EDGES.length)]; break;
            default:
                name = R.string.sub_position;
                value = POSITION_NAMES[index(store, POSITION, 0, POSITIONS.length)];
        }
        return activity.getString(R.string.sub_value, activity.getString(name), activity.getString(value));
    }

    /** A stored choice, or the default when it is missing or out of range after an update. */
    private static int index(SharedPreferences store, String key, int fallback, int count) {
        int value = store.getInt(key, fallback);
        return value >= 0 && value < count ? value : fallback;
    }

    private static int next(SharedPreferences store, String key, int fallback, int count) {
        return (index(store, key, fallback, count) + 1) % count;
    }
}
